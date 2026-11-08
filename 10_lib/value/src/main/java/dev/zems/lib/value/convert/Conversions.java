package dev.zems.lib.value.convert;

import dev.zems.lib.value.Value;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;

/**
 * The shipped conversions that need a policy: fractional to integral with a {@link Rounding}, and epoch number to
 * {@link Instant} with an {@link EpochUnit}.
 *
 * <p>
 * Each factory answers a conversion ready for {@link Converter#with(Conversion)}; registering it replaces the standard
 * converter's entry for that type pair. Rounding decides the fraction only: a value that does not fit the target after
 * rounding is still refused. A {@code null} policy throws {@link IllegalArgumentException}.
 */
public final class Conversions {

  private Conversions() {}

  /** A {@code Double} to {@code Byte} conversion that rounds instead of refusing a fraction. */
  public static Conversion<Double, Byte> doubleToByte(Rounding rounding) {
    return withRounding(Double.class, Byte.class, rounding);
  }

  /** A {@code Double} to {@code Short} conversion that rounds instead of refusing a fraction. */
  public static Conversion<Double, Short> doubleToShort(Rounding rounding) {
    return withRounding(Double.class, Short.class, rounding);
  }

  /** A {@code Double} to {@code Integer} conversion that rounds instead of refusing a fraction. */
  public static Conversion<Double, Integer> doubleToInt(Rounding rounding) {
    return withRounding(Double.class, Integer.class, rounding);
  }

  /** A {@code Double} to {@code Long} conversion that rounds instead of refusing a fraction. */
  public static Conversion<Double, Long> doubleToLong(Rounding rounding) {
    return withRounding(Double.class, Long.class, rounding);
  }

  /** A {@code Double} to {@code BigInteger} conversion that rounds instead of refusing a fraction. */
  public static Conversion<Double, BigInteger> doubleToBigInteger(Rounding rounding) {
    return withRounding(Double.class, BigInteger.class, rounding);
  }

  /** A {@code Float} to {@code Byte} conversion that rounds instead of refusing a fraction. */
  public static Conversion<Float, Byte> floatToByte(Rounding rounding) {
    return withRounding(Float.class, Byte.class, rounding);
  }

  /** A {@code Float} to {@code Short} conversion that rounds instead of refusing a fraction. */
  public static Conversion<Float, Short> floatToShort(Rounding rounding) {
    return withRounding(Float.class, Short.class, rounding);
  }

  /** A {@code Float} to {@code Integer} conversion that rounds instead of refusing a fraction. */
  public static Conversion<Float, Integer> floatToInt(Rounding rounding) {
    return withRounding(Float.class, Integer.class, rounding);
  }

  /** A {@code Float} to {@code Long} conversion that rounds instead of refusing a fraction. */
  public static Conversion<Float, Long> floatToLong(Rounding rounding) {
    return withRounding(Float.class, Long.class, rounding);
  }

  /** A {@code Float} to {@code BigInteger} conversion that rounds instead of refusing a fraction. */
  public static Conversion<Float, BigInteger> floatToBigInteger(Rounding rounding) {
    return withRounding(Float.class, BigInteger.class, rounding);
  }

  /** A {@code BigDecimal} to {@code Byte} conversion that rounds instead of refusing a fraction. */
  public static Conversion<BigDecimal, Byte> bigDecimalToByte(Rounding rounding) {
    return withRounding(BigDecimal.class, Byte.class, rounding);
  }

  /** A {@code BigDecimal} to {@code Short} conversion that rounds instead of refusing a fraction. */
  public static Conversion<BigDecimal, Short> bigDecimalToShort(Rounding rounding) {
    return withRounding(BigDecimal.class, Short.class, rounding);
  }

  /** A {@code BigDecimal} to {@code Integer} conversion that rounds instead of refusing a fraction. */
  public static Conversion<BigDecimal, Integer> bigDecimalToInt(Rounding rounding) {
    return withRounding(BigDecimal.class, Integer.class, rounding);
  }

  /** A {@code BigDecimal} to {@code Long} conversion that rounds instead of refusing a fraction. */
  public static Conversion<BigDecimal, Long> bigDecimalToLong(Rounding rounding) {
    return withRounding(BigDecimal.class, Long.class, rounding);
  }

  /** A {@code BigDecimal} to {@code BigInteger} conversion that rounds instead of refusing a fraction. */
  public static Conversion<BigDecimal, BigInteger> bigDecimalToBigInteger(Rounding rounding) {
    return withRounding(BigDecimal.class, BigInteger.class, rounding);
  }

  /**
   * A {@code Long} to {@link Instant} conversion reading the number as a count since {@code 1970-01-01T00:00:00Z} in
   * the given unit. A count outside the range an {@code Instant} can hold is refused.
   */
  public static Conversion<Long, Instant> epochToInstant(EpochUnit unit) {
    if (unit == null) {
      throw new IllegalArgumentException("Epoch unit must not be null");
    }
    return Conversion.of(Long.class, Instant.class, epoch ->
      ConversionErrors.reframe(Long.class, Instant.class, instantAt(epoch, unit))
    );
  }

  private static Value<Instant> instantAt(long epoch, EpochUnit unit) {
    return switch (unit) {
      case SECONDS -> Value.timeInstantOfEpochSecond(epoch);
      case MILLIS -> Value.timeInstantOfEpochMilli(epoch);
      case MICROS -> Value.timeInstantOfEpochSecond(
        Math.floorDiv(epoch, 1_000_000L),
        Math.floorMod(epoch, 1_000_000L) * 1_000L
      );
      case NANOS -> Value.timeInstantOfEpochSecond(
        Math.floorDiv(epoch, 1_000_000_000L),
        Math.floorMod(epoch, 1_000_000_000L)
      );
    };
  }

  private static <S extends Number, T> Conversion<S, T> withRounding(
    Class<S> sourceType,
    Class<T> targetType,
    Rounding rounding
  ) {
    if (rounding == null) {
      throw new IllegalArgumentException("Rounding must not be null");
    }
    return Conversion.of(sourceType, targetType, source -> Numerics.rounded(source, targetType, rounding));
  }
}
