package dev.zems.lib.value.convert;

import dev.zems.lib.value.SlotShape;
import dev.zems.lib.value.UnsupportedValue;
import dev.zems.lib.value.Value;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts a value to a target type through the conversions it knows.
 *
 * <p>
 * A converter is immutable and safe to share across threads; hold one and reuse it rather than rebuilding it per call.
 * A conversion that cannot succeed answers a state marker, never an exception: a type pair no conversion covers
 * answers an unsupported value, a payload the conversion refuses an error value. Only a {@code null} argument, a blank
 * type slot name, a misdeclared {@code SLOTS} constant or a {@link SlotShape} method that throws (a programming bug,
 * like a mistyped {@code DESCRIPTOR}) throws.
 */
public final class Converter {

  private static final Map<Class<?>, Class<?>> WRAPPERS = Map.of(
    boolean.class,
    Boolean.class,
    char.class,
    Character.class,
    byte.class,
    Byte.class,
    short.class,
    Short.class,
    int.class,
    Integer.class,
    long.class,
    Long.class,
    float.class,
    Float.class,
    double.class,
    Double.class
  );

  private static final Converter STANDARD = new Converter(index(StandardConversions.table()));

  private final Map<TypePair, Conversion<?, ?>> conversions;

  private Converter(Map<TypePair, Conversion<?, ?>> conversions) {
    this.conversions = conversions;
  }

  /**
   * The shared converter carrying the library's lossless conversions: every builtin with a canonical string form parses
   * from and renders to a string, and numbers convert between the eight numeric types where the value survives
   * unchanged. A lossy request (a fraction into an integral type, a count into an instant) is refused. A JSON number
   * converts to {@code Integer}, {@code Long}, {@code BigInteger}, {@code BigDecimal} and {@code Double} on its text,
   * and a {@code BigInteger} result is capped at 1 000 digits, or at the digits written when there are more.
   */
  public static Converter standard() {
    return STANDARD;
  }

  /**
   * Derives a new converter that also knows {@code conversion}; this converter is unchanged. A conversion for a type
   * pair already covered replaces that entry.
   *
   * @throws IllegalArgumentException if {@code conversion}, its source type or its target type is {@code null}
   */
  public Converter with(Conversion<?, ?> conversion) {
    if (conversion == null) {
      throw new IllegalArgumentException("Conversion must not be null");
    }
    if (conversion.sourceType() == null) {
      throw new IllegalArgumentException("Conversion source type must not be null");
    }
    if (conversion.targetType() == null) {
      throw new IllegalArgumentException("Conversion target type must not be null");
    }
    Map<TypePair, Conversion<?, ?>> derived = new HashMap<>(conversions);
    derived.put(pairOf(conversion.sourceType(), conversion.targetType()), conversion);
    return new Converter(Map.copyOf(derived));
  }

  /**
   * Converts {@code source} to {@code target}. State markers pass through re-typed (an error and an unsupported
   * marker each keep their reason under the new expected type). Otherwise the conversion registered for the payload
   * class or its nearest superclass runs, for a JSON engine value the one registered for its kind interface; failing
   * that, a payload the target already accepts is returned as it stands, and a JSON string or boolean converts as its
   * {@code String} or {@code Boolean} does. Failing that, a record converts into a structure type that declares a
   * {@code public static final SLOTS} {@link SlotShape}, and such a structure converts into a record, each component
   * filled by name from the same-named slot through this converter. A type pair no conversion covers answers an
   * unsupported value.
   *
   * @param target the class to convert to; a primitive class is read as its wrapper
   * @throws IllegalArgumentException if {@code source} or {@code target} is {@code null}
   * @throws IllegalStateException if the structure type declares {@code SLOTS} with a type that is not a
   *     {@link SlotShape}, or its {@code SLOTS} holds {@code null}
   */
  public <T> Value<T> convert(Value<?> source, Class<T> target) {
    if (source == null) {
      throw new IllegalArgumentException("Source value must not be null");
    }
    if (target == null) {
      throw new IllegalArgumentException("Target class must not be null");
    }
    Class<T> targetType = wrapperOf(target);
    if (source.isStateMarker()) {
      return retypedState(source, targetType);
    }
    Object payload = source.unbox();
    Class<?> payloadType = JsonPayloads.lookupKeyOf(payload);
    Conversion<?, ?> conversion = lookupOrNull(payloadType, targetType);
    if (conversion != null) {
      return run(conversion, payload);
    }
    if (targetType.isInstance(payload)) {
      return retyped(source);
    }
    Value<?> scalar = JsonPayloads.scalarOrNull(payload);
    if (scalar != null) {
      return convert(scalar, target);
    }
    Value<T> structural = StructuralConversion.convertOrNull(this, payload, targetType);
    if (structural != null) {
      return structural;
    }
    return ConversionErrors.noConversion(payloadType, targetType);
  }

  /**
   * Converts the structure {@code source} to the bound class its {@code typeSlot} names. The slot is read as a
   * {@code TypeName} through this converter, so a type value and a string in qualified-name form both serve; the name
   * matches a bound class by {@link Class#getName()} only. The matched class then converts exactly as
   * {@link #convert(Value, Class)} converts it. State markers pass through re-typed to the base type. A payload with no
   * {@code SLOTS} shape answers an unsupported value; a missing type slot, a slot holding no type name, or a name outside
   * {@code types} answers an error value.
   *
   * @throws IllegalArgumentException if {@code source} or {@code types} is {@code null}, or {@code typeSlot} is
   *     {@code null} or blank
   * @throws IllegalStateException if the structure type misdeclares {@code SLOTS}, as for {@link #convert(Value, Class)}
   */
  public <T> Value<T> convert(Value<?> source, BoundTypes<T> types, String typeSlot) {
    if (source == null) {
      throw new IllegalArgumentException("Source value must not be null");
    }
    if (types == null) {
      throw new IllegalArgumentException("Bound types must not be null");
    }
    if (typeSlot == null || typeSlot.isBlank()) {
      throw new IllegalArgumentException("Type slot name must not be null or blank");
    }
    if (source.isStateMarker()) {
      return retypedState(source, types.base());
    }
    return TypeDispatch.convert(this, source, types, typeSlot);
  }

  private static Map<TypePair, Conversion<?, ?>> index(List<Conversion<?, ?>> conversions) {
    Map<TypePair, Conversion<?, ?>> table = new HashMap<>();
    for (Conversion<?, ?> conversion : conversions) {
      table.put(pairOf(conversion.sourceType(), conversion.targetType()), conversion);
    }
    return Map.copyOf(table);
  }

  private static TypePair pairOf(Class<?> sourceType, Class<?> targetType) {
    return new TypePair(wrapperOf(sourceType), wrapperOf(targetType));
  }

  @SuppressWarnings("unchecked") // int.class is a Class<Integer>, so the wrapper of a Class<T> is still a Class<T>
  private static <T> Class<T> wrapperOf(Class<T> type) {
    return type.isPrimitive() ? (Class<T>) WRAPPERS.getOrDefault(type, type) : type;
  }

  private static <T> Value<T> retypedState(Value<?> source, Class<T> targetType) {
    if (source.isNull()) {
      return Value.nullValue();
    }
    if (source.isUndefined()) {
      return Value.undefined();
    }
    if (source.isUnresolved()) {
      return Value.unresolved();
    }
    if (source.isTombstone()) {
      return Value.tombstone();
    }
    if (source instanceof UnsupportedValue<?> unsupported) {
      return Value.unsupported(unsupported.reason(), targetType);
    }
    return Value.errorOf(source.error().orElseThrow(), targetType);
  }

  @SuppressWarnings("unchecked") // safe by the isInstance / conversion-target check the caller made
  private static <T> Value<T> retyped(Value<?> source) {
    return (Value<T>) source;
  }

  @SuppressWarnings("unchecked") // the conversion was found under the payload's own class and the requested target
  private static <T> Value<T> run(Conversion<?, ?> conversion, Object payload) {
    return ((Conversion<Object, T>) conversion).convert(payload);
  }

  private Conversion<?, ?> lookupOrNull(Class<?> payloadType, Class<?> targetType) {
    for (Class<?> candidate = payloadType; candidate != null; candidate = candidate.getSuperclass()) {
      Conversion<?, ?> hit = conversions.get(new TypePair(candidate, targetType));
      if (hit != null) {
        return hit;
      }
    }
    return null;
  }

  private record TypePair(Class<?> source, Class<?> target) {}
}
