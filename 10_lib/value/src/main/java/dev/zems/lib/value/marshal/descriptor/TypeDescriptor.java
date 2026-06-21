package dev.zems.lib.value.marshal.descriptor;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.Protocol;
import dev.zems.lib.value.marshal.StateReader;
import dev.zems.lib.value.marshal.StateWriter;
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
 * Describes a Java type and (optionally) carries the read/write logic to marshal instances of it.
 *
 * <p>
 * Sealed into seven implementations:
 *
 * <ul>
 * <li>{@link ScalarTypeDescriptor} - non-collection types
 * <li>{@link ListTypeDescriptor} - List types with element type info
 * <li>{@link SetTypeDescriptor} - Set types with element type info
 * <li>{@link MapTypeDescriptor} - Map types with key/value type info
 * <li>{@link SortedMapTypeDescriptor} - SortedMap types preserving natural-key order
 * <li>{@link StructuredTypeDescriptor} - record-shaped types driven by a slot table
 * <li>{@link UnionTypeDescriptor} - explicit oneOf union over a closed branch set
 * </ul>
 *
 * <p>
 * Two construction modes for scalars:
 *
 * <ul>
 * <li>{@code TypeDescriptor.of(name, clazz)} — metadata-only. {@link #read} / {@link #write} throw
 * {@link IllegalStateException} unless {@code clazz} is a built-in JDK type
 * (see {@link BuiltinTypeDescriptors}), in which case the built-in's read/write is adopted.
 * <li>{@code TypeDescriptor.of(name, clazz, reader, writer)} — wired with custom logic.
 * </ul>
 *
 * <p>
 * Discovery is via {@link #find(Class)} which checks built-ins first then looks for a
 * {@code public static final TypeDescriptor} field on the class.
 *
 * @param <T> the Java type being described
 */
public sealed interface TypeDescriptor<
  T
> permits
  ScalarTypeDescriptor,
  ListTypeDescriptor,
  SetTypeDescriptor,
  MapTypeDescriptor,
  SortedMapTypeDescriptor,
  StructuredTypeDescriptor,
  UnionTypeDescriptor {
  String DEFAULT_DESCRIPTOR_NAME = "DESCRIPTOR";

  /**
   * Metadata-only descriptor. If {@code clazz} matches a built-in JDK type, that built-in's read/write logic is adopted
   * (with {@code descriptorName} overriding the built-in's name). Otherwise, the descriptor is unwired and
   * {@link #read}/{@link #write} throw.
   */
  static <T> TypeDescriptor<T> of(String descriptorName, Class<T> clazz) {
    return ScalarTypeDescriptor.of(descriptorName, clazz);
  }

  /**
   * Fully wired scalar descriptor with custom read/write logic.
   */
  static <T> TypeDescriptor<T> of(
    String descriptorName,
    Class<T> clazz,
    Function<StateReader, T> reader,
    BiConsumer<StateWriter, T> writer
  ) {
    return ScalarTypeDescriptor.of(descriptorName, clazz, reader, writer);
  }

  /**
   * Uniform factory dispatching on the raw class.
   * <ul>
   * <li>{@code List.class} — exactly one type-arg (Class or TypeDescriptor); returns
   * {@link ListTypeDescriptor}.
   * <li>{@code Set.class} — exactly one type-arg; returns {@link SetTypeDescriptor}.
   * <li>{@code Map.class} — exactly two type-args; returns {@link MapTypeDescriptor}.
   * <li>{@code SortedMap.class} — exactly two type-args; returns {@link SortedMapTypeDescriptor}.
   * <li>{@code record} class — auto-synthesizes a {@link StructuredTypeDescriptor} from the
   * record's components via {@link RecordSynthesis}. Type-args bind the record's type
   * parameters in declaration order.
   * <li>any other class — throws {@link IllegalStateException} pointing the author at
   * {@link #builder(Class)} for manual reader/writer setup.
   * </ul>
   *
   * <p>
   * Type-args may be {@link Class} or {@link TypeDescriptor}; a {@link Class} is resolved
   * via {@link #find(Class)}.
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
      // Single-arg path: try built-in registry first (String, Integer, UUID, Instant, …),
      // then fall back to discovering a static DESCRIPTOR field on the class.
      var found = find(cls);
      if (found.isPresent()) {
        return (TypeDescriptor<T>) found.get();
      }
    }
    throw new IllegalStateException("TypeDescriptor.of(" + cls.getName() + "): " + RecordSynthesis.resolutionHint(cls));
  }

  /**
   * List descriptor composed of an element descriptor. Always wired.
   */
  static <E> TypeDescriptor<List<Value<E>>> ofList(String descriptorName, TypeDescriptor<E> elementType) {
    return ListTypeDescriptor.of(descriptorName, elementType);
  }

  /**
   * Set descriptor composed of an element descriptor. Always wired.
   */
  static <E> TypeDescriptor<Set<Value<E>>> ofSet(String descriptorName, TypeDescriptor<E> elementType) {
    return SetTypeDescriptor.of(descriptorName, elementType);
  }

  /**
   * Map descriptor composed of key+value descriptors. Always wired.
   */
  static <K, V> TypeDescriptor<Map<K, Value<V>>> ofMap(
    String descriptorName,
    TypeDescriptor<K> keyType,
    TypeDescriptor<V> valueType
  ) {
    return MapTypeDescriptor.of(descriptorName, keyType, valueType);
  }

  /**
   * SortedMap descriptor composed of key+value descriptors. Always wired. Keys must be {@link Comparable}; the
   * descriptor restores natural-key order on read.
   */
  static <K, V> TypeDescriptor<SortedMap<K, Value<V>>> ofSortedMap(
    String descriptorName,
    TypeDescriptor<K> keyType,
    TypeDescriptor<V> valueType
  ) {
    return SortedMapTypeDescriptor.of(descriptorName, keyType, valueType);
  }

  /**
   * Explicit discriminated-union ("oneOf") descriptor over a closed, author-supplied set of branch descriptors. The
   * wire carries the matched branch's index (in declared order) plus that branch's payload; matching on write is by the
   * runtime type of the unboxed value, first match wins. A value matching no branch fails fast. A single-branch union
   * is wire-identical to its sole branch (no discriminator). Branches must not themselves be unions — flatten them.
   *
   * <p>
   * The described type is {@code Object} because a descriptor marshals the unboxed payload; compose with
   * {@link #ofList(String, TypeDescriptor)} / {@link #ofMap(String, TypeDescriptor, TypeDescriptor)} to marshal
   * heterogeneous collections (e.g. {@code ofMap(name, STRING, oneOf(...))}).
   */
  static TypeDescriptor<Object> oneOf(String descriptorName, TypeDescriptor<?>... branches) {
    Objects.requireNonNull(branches, "branches must not be null");
    // Arrays.asList (not List.of) so null branches reach the sharpened check in UnionTypeDescriptor.of.
    return UnionTypeDescriptor.of(descriptorName, Arrays.asList(branches));
  }

  /** {@link List}-valued counterpart of {@link #oneOf(String, TypeDescriptor...)}. */
  static TypeDescriptor<Object> oneOf(String descriptorName, List<TypeDescriptor<?>> branches) {
    return UnionTypeDescriptor.of(descriptorName, branches);
  }

  /**
   * Fluent builder for non-record types — supply the read and write lambdas plus optional descriptor name, aliases, and
   * evolution policy. For records, prefer {@link #of(Class, Object...)} which auto-synthesizes from the canonical
   * constructor.
   */
  static <T> TypeDescriptorBuilder<T> builder(Class<T> clazz) {
    return new TypeDescriptorBuilder<>(clazz);
  }

  /**
   * Looks up a fully wired descriptor for {@code clazz}: checks {@link BuiltinTypeDescriptors} first, then a
   * {@code public static final TypeDescriptor} field named {@code "DESCRIPTOR"} on the class, then — if absent and the
   * class is a record — auto-synthesizes one via {@link RecordSynthesis}.
   *
   * <p>
   * Results are cached per (Class, fieldName) via {@link ClassValue}, so repeated lookups return the same instance.
   *
   * <h4>Generic types</h4>
   *
   * <p>
   * Java erasure means {@code Box<Foo>} and {@code Box<Bar>} share the same {@code Class<Box>} at runtime, so this
   * method returns the same shared descriptor for every parameterisation. To obtain a per-parameterisation descriptor
   * for a generic record, call {@link #find(Class, String, Object...)} or build via {@link #of(Class, Object...)} /
   * {@link RecordSynthesis#synthesize(Class, Object...)} directly.
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
   * Per-parameterisation descriptor for a generic record. Bypasses the {@code (Class, fieldName)} cache so
   * {@code Box<Foo>} and {@code Box<Bar>} get distinct slot types.
   *
   * <ul>
   * <li>{@code typeArgs.length == 0} delegates to {@link #find(Class, String)}.
   * <li>Otherwise, {@code clazz} must be a record with no explicit static {@code DESCRIPTOR}
   * field; this method returns {@link RecordSynthesis#synthesize(Class, Object...)}.
   * <li>If a static {@code DESCRIPTOR} field exists on {@code clazz}, the method throws
   * {@link IllegalStateException} — type-args only apply to record auto-synthesis; the user's
   * explicit descriptor takes precedence and parameterisation must be expressed there.
   * </ul>
   *
   * <p>
   * Type-args may be {@link Class} or {@link TypeDescriptor}; mixed are allowed (see
   * {@link #of(Class, Object...)}).
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

  // -- Factories --

  /**
   * Looks up a fully wired descriptor for {@code clazz}: looks for a {@code public static final TypeDescriptor} field
   * with the given name on the class. For the default field name {@code "DESCRIPTOR"} on a record class, falls back to
   * {@link RecordSynthesis#synthesize} when no field is present.
   *
   * <p>
   * The result is cached per (Class, fieldName) including misses (Optional.empty). The inheritance check (a
   * {@link ScalarTypeDescriptor} must describe an assignable class) runs on every call against the cached raw value —
   * so two callers with different generic type expectations get correct results without polluting the cache.
   *
   * <h4>Generic types</h4>
   *
   * <p>
   * The cache key is {@code (Class, fieldName)} only, so {@code Box<Foo>} and {@code Box<Bar>} share one descriptor.
   * Use {@link #find(Class, String, Object...)} when you need a per-parameterisation descriptor for a generic record.
   */
  @SuppressWarnings("unchecked") // erasure: parameterized type can't be checked at runtime
  static <T> Optional<TypeDescriptor<? extends T>> find(Class<T> clazz, String fieldName) {
    Objects.requireNonNull(clazz, "clazz must not be null");
    Objects.requireNonNull(fieldName, "fieldName must not be null");
    // No-nesting rule on the discovery path too: a Value type fails fast the same way direct construction does.
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

  /** Coerces a {@code Class} or {@code TypeDescriptor} typeArg into a descriptor. */
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

  /**
   * Attempts to read a {@code public static} {@code TypeDescriptor} field (of any subtype) via
   * {@link MethodHandles#publicLookup()}. The field is discovered by name first, so a declaration that uses a concrete
   * subtype — e.g. {@code public static final ScalarTypeDescriptor<Foo> DESCRIPTOR = ...} — is honoured, not only the
   * exact {@code TypeDescriptor} type. A same-named {@code static} field whose type is not a {@code TypeDescriptor}
   * fails loudly rather than being silently ignored (which would let a record fall back to auto-synthesis with a
   * different wire shape).
   */
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

  /**
   * Resolves the raw descriptor for {@code (clazz, fieldName)}: explicit static-final field first; record auto-synth
   * fallback only when (a) no field found, (b) {@code fieldName == "DESCRIPTOR"}, and (c) {@code clazz.isRecord()}.
   */
  private static Optional<TypeDescriptor<?>> resolveRaw(Class<?> clazz, String fieldName) {
    Optional<TypeDescriptor<?>> fromField = tryStaticField(clazz, fieldName);
    if (fromField.isPresent()) {
      return fromField;
    }
    if (DEFAULT_DESCRIPTOR_NAME.equals(fieldName) && clazz.isRecord()) {
      return Optional.of(RecordSynthesis.synthesize(clazz));
    }
    return Optional.empty();
  }

  /**
   * Human-readable type name for diagnostics ({@code "java.lang.String"}, {@code "List<java.lang.String>"},
   * {@code "Map<java.lang.String, java.lang.Integer>"}).
   */
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
   * Boxes the raw read payload into a {@link Value}. Scalar/structured descriptors delegate to {@link Value#of};
   * collection descriptors ({@link ListTypeDescriptor}, {@link SetTypeDescriptor}, {@link MapTypeDescriptor}) override
   * to construct {@link dev.zems.lib.value.builtin.ListValue}, {@link dev.zems.lib.value.builtin.SetValue},
   * {@link dev.zems.lib.value.builtin.MapValue} directly — the public {@code Value.of(...)} rejects raw
   * {@code List}/{@code Set}/{@code Map} in favour of {@code listOf}/{@code setOf}/ {@code mapOf}, so the marshal-side
   * wrapping is done via this hook instead.
   *
   * <p>
   * Used by {@link StateReader#read(int, String, TypeDescriptor)} after {@code readRecord}.
   */
  default Value<T> box(T raw) {
    return Value.of(raw);
  }

  /**
   * Hex-encoded structural signature derived from the descriptor's shape inputs (descriptor name and
   * slot/element/key/value descriptors recursively). 16 chars. Stable across JVMs and build environments — pure
   * function of the descriptor's configuration.
   *
   * <p>
   * Excludes read-time concerns ({@link #nameAliases()}, slot defaults, {@link #evolutionPolicy()}) — they bridge a
   * wire-shape mismatch on read but do not change the wire shape itself. Implementations should cache the result;
   * default implementations recompute on each call.
   */
  default String signature() {
    return Signatures.forScalar(descriptorName());
  }

  /**
   * Stable identifier written to the wire when {@link Protocol.V1#typeVerificationEnabled()} is true. Author-controlled
   * (no class-name coupling).
   */
  String descriptorName();

  /**
   * Alternate descriptor names this descriptor accepts on read. When type verification is on and the wire's descriptor
   * name doesn't match {@link #descriptorName()}, the reader checks if it matches any alias before failing.
   */
  default List<String> nameAliases() {
    return List.of();
  }

  /** Read-time policy for slots present on the wire that this descriptor doesn't consume. */
  default EvolutionPolicy evolutionPolicy() {
    return EvolutionPolicy.STRICT;
  }

  /**
   * Returns a descriptor that accepts the given names as alternates for {@link #descriptorName()} during type-verified
   * reads. Concrete scalar / structured descriptors return a new instance; collection descriptors
   * ({@link ListTypeDescriptor}, {@link MapTypeDescriptor}) have no name to alias and return {@code this} unchanged.
   */
  default TypeDescriptor<T> withAliases(String... aliases) {
    return this;
  }

  /**
   * Returns a descriptor that uses the given evolution policy on read. Concrete scalar / structured descriptors return
   * a new instance; collection descriptors return {@code this} unchanged because they never carry unknown slots
   * themselves.
   */
  default TypeDescriptor<T> withEvolutionPolicy(EvolutionPolicy policy) {
    return this;
  }
}
