package dev.zems.lib.value.convert;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.json.engine.JsonNumber;
import dev.zems.lib.value.json.engine.internal.NumberForm;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

/**
 * The JSON number rows of the standard table: lossless per value and decided on the number text, so a huge exponent
 * answers at once.
 */
final class JsonNumbers {

  private static final int INTEGER_DIGITS = 10;
  private static final int LONG_DIGITS = 19;
  private static final String UNDERFLOWS = " underflows ";
  private static final String OUTSIDE_BIG_DECIMAL = " is outside the BigDecimal range";

  private JsonNumbers() {}

  static List<Conversion<?, ?>> rows() {
    return List.of(
      Conversion.of(JsonNumber.class, Integer.class, number -> integral(number, Integer.class, INTEGER_DIGITS)),
      Conversion.of(JsonNumber.class, Long.class, number -> integral(number, Long.class, LONG_DIGITS)),
      Conversion.of(JsonNumber.class, BigInteger.class, JsonNumbers::bigInteger),
      Conversion.of(JsonNumber.class, BigDecimal.class, JsonNumbers::bigDecimal),
      Conversion.of(JsonNumber.class, Double.class, JsonNumbers::toDouble)
    );
  }

  private static <T> Value<T> integral(JsonNumber number, Class<T> targetType, int maxDigits) {
    NumberForm form = NumberForm.of(number.toString());
    if (form.exponent().signum() < 0) {
      return refusal(number, targetType, Numerics.NOT_WHOLE);
    }
    if (exceeds(form, maxDigits)) {
      return refusal(number, targetType, Numerics.OVERFLOWS + targetType.getSimpleName());
    }
    BigInteger whole = whole(form);
    if (targetType == Integer.class) {
      return whole.bitLength() < Integer.SIZE
        ? typed(Value.of(whole.intValue()))
        : refusal(number, targetType, Numerics.OVERFLOWS + targetType.getSimpleName());
    }
    return whole.bitLength() < Long.SIZE
      ? typed(Value.of(whole.longValue()))
      : refusal(number, targetType, Numerics.OVERFLOWS + targetType.getSimpleName());
  }

  private static Value<BigInteger> bigInteger(JsonNumber number) {
    NumberForm form = NumberForm.of(number.toString());
    if (form.exponent().signum() < 0) {
      return refusal(number, BigInteger.class, Numerics.NOT_WHOLE);
    }
    int cap = Math.max(Numerics.BIG_INTEGER_DIGITS, form.writtenDigits());
    if (exceeds(form, cap)) {
      return refusal(number, BigInteger.class, Numerics.moreDigitsThan(cap));
    }
    return Value.of(whole(form));
  }

  private static Value<BigDecimal> bigDecimal(JsonNumber number) {
    try {
      return Value.of(new BigDecimal(number.toString()));
    } catch (NumberFormatException e) {
      if (NumberForm.of(number.toString()).isZero()) {
        return Value.of(BigDecimal.ZERO);
      }
      return ConversionErrors.failure(JsonNumber.class, BigDecimal.class, number.toString() + OUTSIDE_BIG_DECIMAL, e);
    }
  }

  private static Value<Double> toDouble(JsonNumber number) {
    double parsed = Double.parseDouble(number.toString());
    if (Double.isInfinite(parsed)) {
      return refusal(number, Double.class, Numerics.OVERFLOWS + Double.class.getSimpleName());
    }
    if (parsed == 0.0d && !NumberForm.of(number.toString()).isZero()) {
      return refusal(number, Double.class, UNDERFLOWS + Double.class.getSimpleName());
    }
    return Value.of(parsed);
  }

  private static boolean exceeds(NumberForm form, int maxDigits) {
    BigInteger wholeDigits = form.exponent().add(BigInteger.valueOf(form.digits().length()));
    return wholeDigits.compareTo(BigInteger.valueOf(maxDigits)) > 0;
  }

  private static BigInteger whole(NumberForm form) {
    if (form.isZero()) {
      return BigInteger.ZERO;
    }
    BigInteger magnitude = new BigInteger(form.digits()).multiply(BigInteger.TEN.pow(form.exponent().intValueExact()));
    return form.negative() ? magnitude.negate() : magnitude;
  }

  private static <T> Value<T> refusal(JsonNumber number, Class<T> targetType, String reason) {
    return ConversionErrors.failure(JsonNumber.class, targetType, number.toString() + reason);
  }

  @SuppressWarnings("unchecked")
  private static <T> Value<T> typed(Value<?> value) {
    return (Value<T>) value;
  }
}
