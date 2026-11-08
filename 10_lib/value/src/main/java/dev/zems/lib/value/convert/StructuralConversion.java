package dev.zems.lib.value.convert;

import dev.zems.lib.value.SlotShape;
import dev.zems.lib.value.UnsupportedValue;
import dev.zems.lib.value.Value;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The structural stage of {@link Converter#convert}: records to {@link SlotShape} structures and back. */
final class StructuralConversion {

  private static final String MODULE_NAME = "dev.zems.lib.value";

  private StructuralConversion() {}

  static <T> Value<T> convertOrNull(Converter converter, Object payload, Class<T> target) {
    if (payload instanceof Record record) {
      SlotShape<?> shape = SlotShapes.forTargetOrNull(target);
      if (shape != null) {
        return toStructure(record, shape, target);
      }
    }
    if (target.isRecord()) {
      SlotShape<?> shape = SlotShapes.forPayloadOrNull(payload);
      if (shape != null) {
        return toRecord(converter, payload, shape, target);
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked") // the shape builds instances of its type(), which the target accepts (forTargetOrNull)
  private static <T> Value<T> toStructure(Record record, SlotShape<?> shape, Class<T> target) {
    Class<?> recordType = record.getClass();
    RecordComponent[] components = recordType.getRecordComponents();
    Value<T> refused = unsupportedComponentOrNull(recordType, target, components);
    if (refused != null) {
      return refused;
    }
    MethodHandles.Lookup lookup = lookupFor(recordType);
    Map<String, Value<?>> slots = new LinkedHashMap<>();
    for (RecordComponent component : components) {
      Object componentValue;
      try {
        componentValue = lookup.unreflect(component.getAccessor()).invoke(record);
      } catch (IllegalAccessException e) {
        return inaccessible(recordType, target, recordType);
      } catch (Error e) {
        throw e;
      } catch (Throwable e) {
        return ConversionErrors.failure(recordType, target, ConversionErrors.reasonOf(e), e);
      }
      if (isCollection(componentValue)) {
        return unsupportedComponent(recordType, target, component);
      }
      slots.put(component.getName(), Value.of(componentValue));
    }
    Value<T> built = (Value<T>) shape.build(Collections.unmodifiableMap(slots));
    return ConversionErrors.reframe(recordType, target, built);
  }

  @SuppressWarnings("unchecked") // forPayloadOrNull checked that the shape reads this payload
  private static <S, T> Value<T> toRecord(Converter converter, Object payload, SlotShape<S> shape, Class<T> target) {
    Class<S> sourceType = shape.type();
    RecordComponent[] components = target.getRecordComponents();
    Value<T> refused = unsupportedComponentOrNull(sourceType, target, components);
    if (refused != null) {
      return refused;
    }
    Class<?>[] parameterTypes = new Class<?>[components.length];
    for (int i = 0; i < components.length; i++) {
      parameterTypes[i] = components[i].getType();
    }
    MethodHandle constructor;
    try {
      constructor = lookupFor(target).findConstructor(target, MethodType.methodType(void.class, parameterTypes));
    } catch (IllegalAccessException | NoSuchMethodException e) {
      return inaccessible(sourceType, target, target);
    }
    Map<String, Value<?>> slots = shape.slots((S) payload);
    Object[] arguments = new Object[components.length];
    for (int i = 0; i < components.length; i++) {
      RecordComponent component = components[i];
      String name = component.getName();
      Value<?> slot = slots.get(name);
      if (slot == null || slot.isUndefined() || slot.isTombstone()) {
        return ConversionErrors.failure(
          sourceType,
          target,
          "slot '" + name + "' for component '" + name + "' is missing"
        );
      }
      if (slot.isNull()) {
        if (component.getType().isPrimitive()) {
          return cannotFill(sourceType, target, name, "null cannot fill a primitive component", null);
        }
        arguments[i] = null;
        continue;
      }
      if (slot.isUnresolved()) {
        return cannotFill(sourceType, target, name, "the slot is unresolved", null);
      }
      Value<?> filled = converter.convert(slot, component.getType());
      if (filled.isError()) {
        Throwable cause = filled.error().orElseThrow();
        return cannotFill(sourceType, target, name, ConversionErrors.reasonOf(cause), cause);
      }
      if (filled instanceof UnsupportedValue<?> unsupported) {
        return cannotFill(sourceType, target, name, unsupported.reason(), null);
      }
      Object argument = filled.unbox();
      if (isCollection(argument)) {
        return unsupportedComponent(sourceType, target, component);
      }
      arguments[i] = argument;
    }
    Object built;
    try {
      built = constructor.invokeWithArguments(arguments);
    } catch (Error e) {
      throw e;
    } catch (Throwable e) {
      return ConversionErrors.failure(sourceType, target, ConversionErrors.reasonOf(e), e);
    }
    return Value.of((T) built);
  }

  private static <T> Value<T> unsupportedComponentOrNull(
    Class<?> sourceType,
    Class<T> target,
    RecordComponent[] components
  ) {
    for (RecordComponent component : components) {
      Class<?> type = component.getType();
      if (
        Value.class.isAssignableFrom(type) || Iterable.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)
      ) {
        return unsupportedComponent(sourceType, target, component);
      }
    }
    return null;
  }

  private static <T> Value<T> unsupportedComponent(Class<?> sourceType, Class<T> target, RecordComponent component) {
    return ConversionErrors.unsupported(
      sourceType,
      target,
      "component '" +
        component.getName() +
        "' has type " +
        component.getType().getSimpleName() +
        ", which structural conversion does not support"
    );
  }

  private static <T> Value<T> inaccessible(Class<?> sourceType, Class<T> target, Class<?> recordType) {
    return ConversionErrors.unsupported(
      sourceType,
      target,
      "record " +
        recordType.getSimpleName() +
        " is not accessible; make it public or open its package to " +
        MODULE_NAME
    );
  }

  private static <T> Value<T> cannotFill(
    Class<?> sourceType,
    Class<T> target,
    String name,
    String detail,
    Throwable cause
  ) {
    return ConversionErrors.failure(
      sourceType,
      target,
      "slot '" + name + "' cannot fill component '" + name + "': " + detail,
      cause
    );
  }

  // A wide component (Object, a type variable) may still hold a collection at runtime; Value.of would refuse it and a
  // boxed collection handed to a constructor would carry unconverted elements.
  private static boolean isCollection(Object value) {
    return value instanceof Collection<?> || value instanceof Map<?, ?>;
  }

  private static MethodHandles.Lookup lookupFor(Class<?> recordType) {
    try {
      // publicLookup alone cannot see a non-public record; privateLookupIn can, in packages opened to this module.
      return MethodHandles.privateLookupIn(recordType, MethodHandles.lookup());
    } catch (IllegalAccessException ignored) {
      return MethodHandles.publicLookup();
    }
  }
}
