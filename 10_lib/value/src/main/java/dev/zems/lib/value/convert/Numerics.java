package dev.zems.lib.value.convert;

import dev.zems.lib.value.Value;
import java.math.BigDecimal;
import java.math.BigInteger;

/** The exact-or-refuse numeric core shared by the standard number conversions and the rounding conversions. */
final class Numerics {

  static final String NOT_WHOLE = " is not a whole number";
  static final String OVERFLOWS = " overflows ";
  static final int BIG_INTEGER_DIGITS = 1_000;

  private static final int INTEGER_DIGITS = 10;
  private static final int LONG_DIGITS = 19;
  private static final int SHORT_DIGITS = 5;
  private static final int BYTE_DIGITS = 3;
  private static final int TINY_SCALE_GAP = 1;
  private static final String NOT_REPRESENTABLE = " is not exactly representable as ";

  private Numerics() {}

  static <T> Value<T> exact(Number source, Class<T> targetType) {
    if (!isFinite(source)) {
      return nonFinite(source, targetType);
    }
    // BigDecimal has no signed zero, so -0.0 would lose its sign on the decimal route below; between the two floating
    // types a plain cast keeps it. Every other target can only hold a plain zero anyway.
    if (isFloatingZero(source) && isFloatingTarget(targetType)) {
      return floatingCast(source, targetType);
    }
    BigDecimal decimal = exactDecimal(source);
    if (targetType == BigDecimal.class) {
      return typed(Value.of(decimal));
    }
    if (targetType == Double.class) {
      return exactDouble(source, decimal, targetType);
    }
    if (targetType == Float.class) {
      return exactFloat(source, decimal, targetType);
    }
    return integral(source, decimal, targetType);
  }

  static <T> Value<T> rounded(Number source, Class<T> targetType, Rounding rounding) {
    if (!isFinite(source)) {
      return ConversionErrors.failure(source.getClass(), targetType, source + NOT_WHOLE);
    }
    BigDecimal decimal = exactDecimal(source);
    if (decimal.signum() != 0) {
      Value<T> tooLarge = tooLargeOrNull(source, decimal, targetType);
      if (tooLarge != null) {
        return tooLarge;
      }
      // setScale would divide by a power of ten as large as the scale; every rounding mode treats any |v| < 0.01
      // like 0.01 of the same sign, so the stand-in rounds alike at once.
      if ((long) decimal.scale() - decimal.precision() > TINY_SCALE_GAP) {
        decimal = BigDecimal.valueOf(decimal.signum(), 2);
      }
    }
    BigDecimal whole = decimal.setScale(0, rounding.mode());
    return ranged(source, whole.toBigIntegerExact(), targetType);
  }

  static String moreDigitsThan(long cap) {
    return " has more than " + cap + " digits";
  }

  private static boolean isFinite(Number source) {
    return switch (source) {
      case Double d -> Double.isFinite(d);
      case Float f -> Float.isFinite(f);
      default -> true;
    };
  }

  private static boolean isFloatingZero(Number source) {
    return switch (source) {
      case Double d -> d == 0.0d;
      case Float f -> f == 0.0f;
      default -> false;
    };
  }

  private static boolean isFloatingTarget(Class<?> targetType) {
    return targetType == Double.class || targetType == Float.class;
  }

  private static <T> Value<T> floatingCast(Number source, Class<T> targetType) {
    return targetType == Double.class ? typed(Value.of(source.doubleValue())) : typed(Value.of(source.floatValue()));
  }

  private static <T> Value<T> nonFinite(Number source, Class<T> targetType) {
    if (isFloatingTarget(targetType)) {
      return floatingCast(source, targetType);
    }
    return ConversionErrors.failure(source.getClass(), targetType, source + NOT_WHOLE);
  }

  private static BigDecimal exactDecimal(Number source) {
    return switch (source) {
      case BigDecimal d -> d;
      case BigInteger i -> new BigDecimal(i);
      // new BigDecimal(double) is the exact binary expansion; BigDecimal.valueOf would round to the shortest decimal
      // and make 0.1d look exactly representable as a float.
      case Double d -> new BigDecimal(d);
      // A float widens to double without loss, so the double's expansion is the float's exact value.
      case Float f -> new BigDecimal(f.doubleValue());
      default -> BigDecimal.valueOf(source.longValue());
    };
  }

  private static <T> Value<T> integral(Number source, BigDecimal decimal, Class<T> targetType) {
    // Only a positive scale can hide a fraction, and stripping it cannot overflow; stripping a negative scale can
    // (100E+2147483647 throws), so whole numbers skip it.
    if (decimal.scale() > 0 && decimal.stripTrailingZeros().scale() > 0) {
      return ConversionErrors.failure(source.getClass(), targetType, source + NOT_WHOLE);
    }
    if (decimal.signum() == 0) {
      return ranged(source, BigInteger.ZERO, targetType);
    }
    Value<T> tooLarge = tooLargeOrNull(source, decimal, targetType);
    if (tooLarge != null) {
      return tooLarge;
    }
    return ranged(source, decimal.toBigIntegerExact(), targetType);
  }

  // Decides on the digit count before the exponent is expanded, whose cost follows the exponent; long arithmetic,
  // because precision minus scale overflows an int at the edge of the scale range.
  private static <T> Value<T> tooLargeOrNull(Number source, BigDecimal decimal, Class<T> targetType) {
    long wholeDigits = (long) decimal.precision() - decimal.scale();
    if (targetType == BigInteger.class) {
      int cap = Math.max(BIG_INTEGER_DIGITS, decimal.precision());
      return wholeDigits > cap
        ? ConversionErrors.failure(source.getClass(), targetType, source + moreDigitsThan(cap))
        : null;
    }
    if (wholeDigits > maxDigits(targetType)) {
      return ConversionErrors.failure(source.getClass(), targetType, source + OVERFLOWS + targetType.getSimpleName());
    }
    return null;
  }

  private static int maxDigits(Class<?> targetType) {
    if (targetType == Integer.class) {
      return INTEGER_DIGITS;
    }
    if (targetType == Long.class) {
      return LONG_DIGITS;
    }
    return targetType == Short.class ? SHORT_DIGITS : BYTE_DIGITS;
  }

  private static <T> Value<T> ranged(Number source, BigInteger whole, Class<T> targetType) {
    if (targetType == BigInteger.class) {
      return typed(Value.of(whole));
    }
    if (whole.bitLength() > valueBits(targetType)) {
      return ConversionErrors.failure(source.getClass(), targetType, source + OVERFLOWS + targetType.getSimpleName());
    }
    if (targetType == Integer.class) {
      return typed(Value.of(whole.intValue()));
    }
    if (targetType == Long.class) {
      return typed(Value.of(whole.longValue()));
    }
    if (targetType == Short.class) {
      return typed(Value.of(whole.shortValue()));
    }
    return typed(Value.of(whole.byteValue()));
  }

  // BigInteger.bitLength() excludes the sign bit, so the magnitude has one bit fewer than the type.
  private static int valueBits(Class<?> targetType) {
    if (targetType == Integer.class) {
      return Integer.SIZE - 1;
    }
    if (targetType == Long.class) {
      return Long.SIZE - 1;
    }
    return targetType == Short.class ? Short.SIZE - 1 : Byte.SIZE - 1;
  }

  private static <T> Value<T> exactDouble(Number source, BigDecimal decimal, Class<T> targetType) {
    double candidate = decimal.doubleValue();
    if (!Double.isFinite(candidate) || new BigDecimal(candidate).compareTo(decimal) != 0) {
      return ConversionErrors.failure(
        source.getClass(),
        targetType,
        source + NOT_REPRESENTABLE + targetType.getSimpleName()
      );
    }
    return typed(Value.of(candidate));
  }

  private static <T> Value<T> exactFloat(Number source, BigDecimal decimal, Class<T> targetType) {
    float candidate = decimal.floatValue();
    if (!Float.isFinite(candidate) || new BigDecimal(candidate).compareTo(decimal) != 0) {
      return ConversionErrors.failure(
        source.getClass(),
        targetType,
        source + NOT_REPRESENTABLE + targetType.getSimpleName()
      );
    }
    return typed(Value.of(candidate));
  }

  @SuppressWarnings("unchecked")
  private static <T> Value<T> typed(Value<?> value) {
    return (Value<T>) value;
  }
}
