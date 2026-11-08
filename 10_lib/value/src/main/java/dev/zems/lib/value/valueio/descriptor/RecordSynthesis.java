package dev.zems.lib.value.valueio.descriptor;

import dev.zems.lib.value.CoreValue;
import dev.zems.lib.value.Value;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Synthesises a {@link StructuredTypeDescriptor} from a record class: one slot per component, id = its index. */
final class RecordSynthesis {

  private RecordSynthesis() {}

  static void rejectValueType(Class<?> clazz) {
    if (Value.class.isAssignableFrom(clazz) && !CoreValue.class.isAssignableFrom(clazz)) {
      throw new IllegalArgumentException("Cannot describe Value types - use the Value directly");
    }
  }

  private static void rejectValueComponent(Class<?> recordClass, RecordComponent component) {
    if (holdsValue(component.getGenericType())) {
      throw new IllegalArgumentException(
        "Cannot describe " +
          recordClass.getName() +
          ": component '" +
          component.getName() +
          "' is declared as " +
          component.getGenericType().getTypeName() +
          " - declare the plain type, not a Value"
      );
    }
  }

  private static boolean holdsValue(Type type) {
    return switch (type) {
      case Class<?> c when c.isArray() -> holdsValue(c.getComponentType());
      case Class<?> c -> Value.class.isAssignableFrom(c) && !CoreValue.class.isAssignableFrom(c);
      case ParameterizedType p -> holdsValue(p.getRawType()) ||
        Arrays.stream(p.getActualTypeArguments()).anyMatch(RecordSynthesis::holdsValue);
      case WildcardType w -> Arrays.stream(w.getUpperBounds()).anyMatch(RecordSynthesis::holdsValue);
      case GenericArrayType g -> holdsValue(g.getGenericComponentType());
      default -> false;
    };
  }

  static <T> StructuredTypeDescriptor<T> synthesize(Class<T> recordClass, Object... typeArgs) {
    rejectValueType(recordClass);
    if (!recordClass.isRecord()) {
      throw new IllegalStateException(
        "RecordSynthesis.synthesize requires a record class, got " + recordClass.getName()
      );
    }
    Map<TypeVariable<?>, TypeDescriptor<?>> bindings = bindTypeArgs(recordClass, typeArgs);

    RecordComponent[] components = recordClass.getRecordComponents();
    var slots = new ArrayList<SlotSpec<?>>(components.length);
    var ctorParams = new Class<?>[components.length];

    MethodHandles.Lookup lookup = lookupFor(recordClass);

    for (int i = 0; i < components.length; i++) {
      RecordComponent rc = components[i];
      rejectValueComponent(recordClass, rc);
      ctorParams[i] = rc.getType();
      TypeDescriptor<?> slotDescriptor = resolveDescriptor(rc.getGenericType(), bindings);
      MethodHandle accessor;
      try {
        accessor = lookup.unreflect(rc.getAccessor());
      } catch (IllegalAccessException e) {
        throw new IllegalStateException(
          "Cannot access accessor for component '" +
            rc.getName() +
            "' of " +
            recordClass.getName() +
            " — record class must be public or in a module opened to dev.zems.lib.value",
          e
        );
      }
      slots.add(makeSlot(i, rc.getName(), slotDescriptor, accessor, SlotKind.of(rc.getType())));
    }

    MethodHandle ctor;
    try {
      ctor = lookup.findConstructor(recordClass, MethodType.methodType(void.class, ctorParams));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new IllegalStateException(
        "Canonical constructor not accessible for " +
          recordClass.getName() +
          " — record class must be public or in a module opened to dev.zems.lib.value",
        e
      );
    }

    return new StructuredTypeDescriptor<>(
      recordClass.getName(),
      List.of(),
      EvolutionPolicy.STRICT,
      recordClass,
      List.copyOf(slots),
      ctor
    );
  }

  static String resolutionHint(Class<?> c) {
    if (c.isSealed()) {
      var permits = c.getPermittedSubclasses();
      var permitNames = new ArrayList<String>(permits.length);
      for (var p : permits) {
        permitNames.add(p.getSimpleName());
      }
      return (
        "sealed " +
        (c.isInterface() ? "interface" : "class") +
        " — auto-dispatch over permitted subtypes is not supported. " +
        "Write a custom StructuredTypeDescriptor<" +
        c.getSimpleName() +
        "> that pattern-matches on the permits " +
        permitNames +
        " and dispatches each branch to its own descriptor, then expose it as a public static final DESCRIPTOR field on " +
        c.getSimpleName() +
        "."
      );
    }
    if (c.isInterface()) {
      return (
        "interface — interfaces have no auto-synthesisable shape. Use a concrete record/class type, " +
        "or write a custom descriptor and expose it via a public static final DESCRIPTOR field."
      );
    }
    if (Modifier.isAbstract(c.getModifiers())) {
      return (
        "abstract class — abstract classes have no canonical constructor. Use a concrete subtype, " +
        "or write a custom descriptor and expose it via a public static final DESCRIPTOR field."
      );
    }
    return (
      "no built-in descriptor, no DESCRIPTOR field, and not a record. " +
      "Use TypeDescriptor.builder(" +
      c.getSimpleName() +
      ") to declare reader/writer manually and expose it via a public static final DESCRIPTOR field, " +
      "or convert the type to a record."
    );
  }

  private static MethodHandles.Lookup lookupFor(Class<?> cls) {
    try {
      // publicLookup alone cannot see a non-public record; privateLookupIn can, in packages opened to this module.
      return MethodHandles.privateLookupIn(cls, MethodHandles.lookup());
    } catch (IllegalAccessException ignored) {
      return MethodHandles.publicLookup();
    }
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private static SlotSpec<?> makeSlot(
    int id,
    String name,
    TypeDescriptor<?> descriptor,
    MethodHandle accessor,
    SlotKind kind
  ) {
    Object noDefault = null;
    return new SlotSpec(id, name, List.of(), descriptor, noDefault, accessor, kind);
  }

  private static Map<TypeVariable<?>, TypeDescriptor<?>> bindTypeArgs(Class<?> recordClass, Object[] typeArgs) {
    TypeVariable<?>[] params = recordClass.getTypeParameters();
    if (params.length == 0) {
      if (typeArgs.length != 0) {
        throw new IllegalArgumentException(
          recordClass.getName() + " has no type parameters but " + typeArgs.length + " typeArgs were supplied"
        );
      }
      return Map.of();
    }
    if (typeArgs.length != params.length) {
      throw new IllegalArgumentException(
        recordClass.getName() +
          " has " +
          params.length +
          " type parameter(s) but " +
          typeArgs.length +
          " typeArg(s) were supplied"
      );
    }
    var bindings = new HashMap<TypeVariable<?>, TypeDescriptor<?>>(params.length);
    for (int i = 0; i < params.length; i++) {
      bindings.put(params[i], asDescriptor(typeArgs[i]));
    }
    return Map.copyOf(bindings);
  }

  private static TypeDescriptor<?> asDescriptor(Object o) {
    if (o instanceof TypeDescriptor<?> d) {
      return d;
    }
    if (o instanceof Class<?> c) {
      return TypeDescriptor.find(c).orElseThrow(() ->
        new IllegalStateException("No descriptor available for " + c.getName() + " — " + resolutionHint(c))
      );
    }
    throw new IllegalArgumentException(
      "typeArg must be a Class<?> or TypeDescriptor<?>, got " + (o == null ? "null" : o.getClass().getName())
    );
  }

  private static TypeDescriptor<?> resolveDescriptor(
    Type genericType,
    Map<TypeVariable<?>, TypeDescriptor<?>> bindings
  ) {
    if (genericType instanceof Class<?> c) {
      return resolveClass(c);
    }
    if (genericType instanceof TypeVariable<?> tv) {
      var bound = bindings.get(tv);
      if (bound == null) {
        throw new IllegalStateException(
          "Type variable '" + tv.getName() + "' has no binding — pass typeArgs to TypeDescriptor.of(...)"
        );
      }
      return bound;
    }
    if (genericType instanceof ParameterizedType pt) {
      return resolveParameterized(pt, bindings);
    }
    throw new IllegalStateException("Unsupported component type: " + genericType);
  }

  private static TypeDescriptor<?> resolveClass(Class<?> c) {
    Class<?> lookup = boxIfPrimitive(c);
    return TypeDescriptor.find(lookup).orElseThrow(() ->
      new IllegalStateException("Cannot resolve component type " + c.getName() + " — " + resolutionHint(c))
    );
  }

  private static Class<?> boxIfPrimitive(Class<?> c) {
    if (!c.isPrimitive()) {
      return c;
    }
    if (c == int.class) {
      return Integer.class;
    }
    if (c == long.class) {
      return Long.class;
    }
    if (c == double.class) {
      return Double.class;
    }
    if (c == float.class) {
      return Float.class;
    }
    if (c == boolean.class) {
      return Boolean.class;
    }
    if (c == short.class) {
      return Short.class;
    }
    if (c == byte.class) {
      return Byte.class;
    }
    if (c == char.class) {
      return Character.class;
    }
    return c;
  }

  private static TypeDescriptor<?> resolveParameterized(
    ParameterizedType pt,
    Map<TypeVariable<?>, TypeDescriptor<?>> bindings
  ) {
    Class<?> raw = (Class<?>) pt.getRawType();
    Type[] args = pt.getActualTypeArguments();
    if (raw == List.class) {
      if (args.length != 1) {
        throw new IllegalStateException("List type must have exactly one type argument, got " + args.length);
      }
      return TypeDescriptor.of(List.class, resolveDescriptor(args[0], bindings));
    }
    if (raw == Map.class) {
      if (args.length != 2) {
        throw new IllegalStateException("Map type must have exactly two type arguments, got " + args.length);
      }
      return TypeDescriptor.of(Map.class, resolveDescriptor(args[0], bindings), resolveDescriptor(args[1], bindings));
    }
    Object[] argDescs = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      argDescs[i] = resolveDescriptor(args[i], bindings);
    }
    return TypeDescriptor.of(raw, argDescs);
  }
}
