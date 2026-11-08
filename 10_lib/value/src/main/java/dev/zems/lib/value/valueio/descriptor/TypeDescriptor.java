package dev.zems.lib.value.valueio.descriptor;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.valueio.Protocol;
import dev.zems.lib.value.valueio.StateReader;
import dev.zems.lib.value.valueio.StateWriter;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Describes a Java type and carries the read and write logic that marshals its unboxed payload.
 *
 * <p>
 * Sealed into scalar, list, set, map, sorted-map, structured, union and enum descriptors. {@link #find(Class)} resolves
 * built-ins first, then a {@code public static final TypeDescriptor DESCRIPTOR} field, then record synthesis, then enum
 * synthesis.
 */
public sealed interface TypeDescriptor<T> permits
  ScalarTypeDescriptor,
  ListTypeDescriptor,
  SetTypeDescriptor,
  MapTypeDescriptor,
  SortedMapTypeDescriptor,
  StructuredTypeDescriptor,
  UnionTypeDescriptor,
  EnumTypeDescriptor {
  String DEFAULT_DESCRIPTOR_NAME = "DESCRIPTOR";

  /**
   * Metadata-only descriptor: when {@code clazz} is a built-in, its read and write logic is adopted under
   * {@code descriptorName}; otherwise {@link #read} and {@link #write} throw {@link IllegalStateException}.
   */
  static <T> TypeDescriptor<T> of(String descriptorName, Class<T> clazz) {
    return ScalarTypeDescriptor.of(descriptorName, clazz);
  }

  static <T> TypeDescriptor<T> of(
    String descriptorName,
    Class<T> clazz,
    Function<StateReader, T> reader,
    BiConsumer<StateWriter, T> writer
  ) {
    return ScalarTypeDescriptor.of(descriptorName, clazz, reader, writer);
  }

  /**
   * Builds a descriptor from a raw class: {@code List} and {@code Set} take one type-arg, {@code Map} and
   * {@code SortedMap} two, a record binds its type parameters in declaration order and is synthesised, and any other
   * class resolves through {@link #find(Class)}. A type-arg is a {@link Class} or a {@link TypeDescriptor}. Throws
   * {@link IllegalStateException} when nothing resolves.
   */
  @SuppressWarnings("unchecked")
  static <T> TypeDescriptor<T> of(Class<T> cls, Object... typeArgs) {
    Objects.requireNonNull(cls, "cls must not be null");
    Objects.requireNonNull(typeArgs, "typeArgs must not be null");
    if (cls == List.class) {
      if (typeArgs.length != 1) {
        throw new IllegalArgumentException(
          "TypeDescriptor.of(List.class, ...) requires exactly one type-arg, got " + typeArgs.length
        );
      }
      return (TypeDescriptor<T>) ListTypeDescriptor.of(cls.getName(), asDescriptor(typeArgs[0]));
    }
    if (cls == Set.class) {
      if (typeArgs.length != 1) {
        throw new IllegalArgumentException(
          "TypeDescriptor.of(Set.class, ...) requires exactly one type-arg, got " + typeArgs.length
        );
      }
      return (TypeDescriptor<T>) SetTypeDescriptor.of(cls.getName(), asDescriptor(typeArgs[0]));
    }
    if (cls == Map.class) {
      if (typeArgs.length != 2) {
        throw new IllegalArgumentException(
          "TypeDescriptor.of(Map.class, ...) requires exactly two type-args, got " + typeArgs.length
        );
      }
      return (TypeDescriptor<T>) MapTypeDescriptor.of(
        cls.getName(),
        asDescriptor(typeArgs[0]),
        asDescriptor(typeArgs[1])
      );
    }
    if (cls == SortedMap.class) {
      if (typeArgs.length != 2) {
        throw new IllegalArgumentException(
          "TypeDescriptor.of(SortedMap.class, ...) requires exactly two type-args, got " + typeArgs.length
        );
      }
      return (TypeDescriptor<T>) SortedMapTypeDescriptor.of(
        cls.getName(),
        asDescriptor(typeArgs[0]),
        asDescriptor(typeArgs[1])
      );
    }
    if (cls.isRecord()) {
      return RecordSynthesis.synthesize(cls, typeArgs);
    }
    if (typeArgs.length == 0) {
      var found = find(cls);
      if (found.isPresent()) {
        return (TypeDescriptor<T>) found.get();
      }
    }
    throw new IllegalStateException("TypeDescriptor.of(" + cls.getName() + "): " + RecordSynthesis.resolutionHint(cls));
  }

  static <E> TypeDescriptor<List<Value<E>>> ofList(String descriptorName, TypeDescriptor<E> elementType) {
    return ListTypeDescriptor.of(descriptorName, elementType);
  }

  static <E> TypeDescriptor<Set<Value<E>>> ofSet(String descriptorName, TypeDescriptor<E> elementType) {
    return SetTypeDescriptor.of(descriptorName, elementType);
  }

  static <K, V> TypeDescriptor<Map<K, Value<V>>> ofMap(
    String descriptorName,
    TypeDescriptor<K> keyType,
    TypeDescriptor<V> valueType
  ) {
    return MapTypeDescriptor.of(descriptorName, keyType, valueType);
  }

  /** Keys must be {@link Comparable}; natural key order is restored on read. */
  static <K, V> TypeDescriptor<SortedMap<K, Value<V>>> ofSortedMap(
    String descriptorName,
    TypeDescriptor<K> keyType,
    TypeDescriptor<V> valueType
  ) {
    return SortedMapTypeDescriptor.of(descriptorName, keyType, valueType);
  }

  /**
   * Explicit discriminated union over the given branches: the wire carries the matched branch's declared-order index
   * plus its payload, matched on write by the unboxed value's runtime type, first match wins. A single-branch union is
   * wire-identical to its branch. Branches must not be unions; a value matching no branch throws.
   */
  static TypeDescriptor<Object> oneOf(String descriptorName, TypeDescriptor<?>... branches) {
    Objects.requireNonNull(branches, "branches must not be null");
    // Arrays.asList, not List.of: a null branch must reach the check in UnionTypeDescriptor.of, not throw here.
    return UnionTypeDescriptor.of(descriptorName, Arrays.asList(branches));
  }

  static TypeDescriptor<Object> oneOf(String descriptorName, List<TypeDescriptor<?>> branches) {
    return UnionTypeDescriptor.of(descriptorName, branches);
  }

  /** Builder for non-record types; records should use {@link #of(Class, Object...)} instead. */
  static <T> TypeDescriptorBuilder<T> builder(Class<T> clazz) {
    return new TypeDescriptorBuilder<>(clazz);
  }

  /**
   * Finds a wired descriptor for {@code clazz}: built-ins first, then a {@code public static final TypeDescriptor}
   * field named {@code DESCRIPTOR}, then record synthesis, then enum synthesis. Results are cached per class, so every
   * parameterisation of a generic record shares one descriptor; use {@link #find(Class, String, Object...)} for a
   * per-parameterisation one.
   */
  static <T> Optional<TypeDescriptor<? extends T>> find(Class<T> clazz) {
    Objects.requireNonNull(clazz, "clazz must not be null");

    var builtIn = BuiltinTypeDescriptors.find(clazz);
    if (builtIn.isPresent()) {
      return builtIn;
    }

    return find(clazz, DEFAULT_DESCRIPTOR_NAME);
  }

  /**
   * Per-parameterisation descriptor for a generic record, bypassing the cache. Without type-args it delegates to
   * {@link #find(Class, String)}; otherwise {@code clazz} must be a record without an explicit {@code fieldName} field,
   * or {@link IllegalStateException} is thrown.
   */
  static <T> Optional<TypeDescriptor<? extends T>> find(Class<T> clazz, String fieldName, Object... typeArgs) {
    Objects.requireNonNull(clazz, "clazz must not be null");
    Objects.requireNonNull(fieldName, "fieldName must not be null");
    Objects.requireNonNull(typeArgs, "typeArgs must not be null");
    if (typeArgs.length == 0) {
      return find(clazz, fieldName);
    }
    if (!clazz.isRecord()) {
      throw new IllegalStateException(
        "Type args supplied for " +
          clazz.getName() +
          " but it is not a record class; type args only apply to record auto-synthesis."
      );
    }
    if (tryStaticField(clazz, fieldName).isPresent()) {
      throw new IllegalStateException(
        "Type args supplied for " +
          clazz.getName() +
          " but it has an explicit " +
          fieldName +
          " field; type args only apply to record auto-synthesis. Build a parameterised descriptor manually."
      );
    }
    return Optional.of(RecordSynthesis.synthesize(clazz, typeArgs));
  }

  /**
   * Finds the descriptor held in the {@code public static final} field named {@code fieldName}; for {@code DESCRIPTOR}
   * on a record or an enum, falls back to synthesis. Hits and misses are cached per class and field name. A scalar
   * descriptor describing a class not assignable to {@code clazz} answers empty.
   */
  @SuppressWarnings("unchecked")
  static <T> Optional<TypeDescriptor<? extends T>> find(Class<T> clazz, String fieldName) {
    Objects.requireNonNull(clazz, "clazz must not be null");
    Objects.requireNonNull(fieldName, "fieldName must not be null");
    RecordSynthesis.rejectValueType(clazz);

    Optional<TypeDescriptor<?>> raw = TypeDescriptorCache.forClass(clazz).computeIfAbsent(fieldName, fn ->
      resolveRaw(clazz, fn)
    );
    if (raw.isEmpty()) {
      return Optional.empty();
    }
    TypeDescriptor<?> td = raw.get();
    if (td instanceof ScalarTypeDescriptor<?> s && !clazz.isAssignableFrom(s.describedClass())) {
      return Optional.empty();
    }
    return Optional.of((TypeDescriptor<? extends T>) td);
  }

  private static TypeDescriptor<?> asDescriptor(Object o) {
    if (o instanceof TypeDescriptor<?> d) {
      return d;
    }
    if (o instanceof Class<?> raw) {
      return find(raw).orElseThrow(() ->
        new IllegalStateException(
          "No descriptor available for " +
            raw.getName() +
            " — register a built-in, declare a DESCRIPTOR field, or pass a TypeDescriptor directly"
        )
      );
    }
    throw new IllegalArgumentException(
      "typeArg must be Class<?> or TypeDescriptor<?>, got " + (o == null ? "null" : o.getClass().getName())
    );
  }

  private static Optional<TypeDescriptor<?>> tryStaticField(Class<?> clazz, String fieldName) {
    Field field;
    try {
      field = clazz.getField(fieldName);
    } catch (NoSuchFieldException ignored) {
      return Optional.empty();
    }
    if (!Modifier.isStatic(field.getModifiers())) {
      return Optional.empty();
    }
    if (!TypeDescriptor.class.isAssignableFrom(field.getType())) {
      // Answering empty here would let a record with a mistyped DESCRIPTOR fall through to synthesis and a different
      // wire shape.
      throw new IllegalStateException(
        clazz.getName() +
          "." +
          fieldName +
          " is declared as " +
          field.getType().getName() +
          " which is not a TypeDescriptor — declare it as TypeDescriptor or a subtype (e.g. ScalarTypeDescriptor)," +
          " or rename the field."
      );
    }
    try {
      var handle = MethodHandles.publicLookup().findStaticGetter(clazz, fieldName, field.getType());
      return Optional.of((TypeDescriptor<?>) handle.invoke());
    } catch (NoSuchFieldException | IllegalAccessException ignored) {
      return Optional.empty();
    } catch (Throwable e) {
      if (e instanceof Error err) {
        throw err;
      }
      return Optional.empty();
    }
  }

  private static Optional<TypeDescriptor<?>> resolveRaw(Class<?> clazz, String fieldName) {
    Optional<TypeDescriptor<?>> fromField = tryStaticField(clazz, fieldName);
    if (fromField.isPresent()) {
      return fromField;
    }
    if (DEFAULT_DESCRIPTOR_NAME.equals(fieldName) && clazz.isRecord()) {
      return Optional.of(RecordSynthesis.synthesize(clazz));
    }
    if (DEFAULT_DESCRIPTOR_NAME.equals(fieldName) && clazz.isEnum()) {
      return Optional.of(EnumTypeDescriptor.synthesizeRaw(clazz));
    }
    return Optional.empty();
  }

  /** Human-readable type name for diagnostics, such as {@code "List<java.lang.String>"}. */
  String qualifiedName();

  /**
   * Reads a value of {@code T} from {@code reader}.
   *
   * @throws IllegalStateException if no read logic was wired
   */
  T read(StateReader reader);

  /**
   * Writes {@code value} to {@code writer}.
   *
   * @throws IllegalStateException if no writing logic was wired
   */
  void write(StateWriter writer, T value);

  /**
   * Boxes a raw read payload into a {@link Value}; collection descriptors override it to build their typed collection
   * values, since {@code Value.of} rejects raw collections.
   */
  default Value<T> box(T raw) {
    return Value.of(raw);
  }

  /**
   * Reads the payload at the slot and boxes it; a descriptor may instead answer an error value for a payload its
   * evolution policy tolerates.
   */
  default Value<T> readValue(StateReader reader, int id, String name) {
    return box(reader.readRecord(id, name, this));
  }

  /**
   * Structural signature, 16 hex chars derived from the descriptor's name and child descriptors, recursively. Aliases,
   * slot defaults and the evolution policy do not affect it; it is stable across JVMs.
   */
  default String signature() {
    return Signatures.forScalar(descriptorName());
  }

  /** Author-chosen identifier written to the wire when {@link Protocol.V1#typeVerificationEnabled()} is on. */
  String descriptorName();

  /** Alternate descriptor names accepted on a type-verified read besides {@link #descriptorName()}. */
  default List<String> nameAliases() {
    return List.of();
  }

  /** Read-time policy for slots present on the wire that this descriptor does not consume. */
  default EvolutionPolicy evolutionPolicy() {
    return EvolutionPolicy.STRICT;
  }

  /** A descriptor that also accepts {@code aliases} as its wire name; collection descriptors return {@code this}. */
  default TypeDescriptor<T> withAliases(String... aliases) {
    return this;
  }

  /** A descriptor reading with {@code policy}; collection descriptors return {@code this}. */
  default TypeDescriptor<T> withEvolutionPolicy(EvolutionPolicy policy) {
    return this;
  }
}
