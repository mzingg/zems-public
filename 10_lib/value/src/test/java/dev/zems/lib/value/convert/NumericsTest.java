package dev.zems.lib.value.convert;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.Value;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("Numerics — exact or refuse, decided per value")
class NumericsTest {

  /** IEEE 754 binary64 has a 53-bit significand: above this value a double skips whole numbers. */
  private static final long TWO_TO_THE_53 = 1L << 53;

  private static final Duration AT_ONCE = Duration.ofSeconds(1);

  @Test
  @DisplayName("losslessness is a property of the value, not of the type pair")
  void exactnessIsDecidedPerValue() {
    assertThat(Numerics.exact(42L, Integer.class)).isEqualTo(Value.of(42));
    assertThat(Numerics.exact(5_000_000_000L, Integer.class)).hasErrorMessage(
      "Cannot convert Long to Integer: 5000000000 overflows Integer"
    );
    assertThat(Numerics.exact(4.0d, Integer.class)).isEqualTo(Value.of(4));
    assertThat(Numerics.exact(3.7d, Integer.class)).hasErrorMessage(
      "Cannot convert Double to Integer: 3.7 is not a whole number"
    );
  }

  @Test
  @DisplayName("the double boundary is 2^53 — the first integer it cannot tell from its neighbour is refused")
  void integersBeyondTheDoubleBoundaryAreRefused() {
    assertThat(Numerics.exact(TWO_TO_THE_53 - 1, Double.class)).isEqualTo(Value.of((double) (TWO_TO_THE_53 - 1)));
    assertThat(Numerics.exact(TWO_TO_THE_53, Double.class)).isEqualTo(Value.of((double) TWO_TO_THE_53));
    assertThat(Numerics.exact(TWO_TO_THE_53 + 1, Double.class)).hasErrorMessage(
      "Cannot convert Long to Double: 9007199254740993 is not exactly representable as Double"
    );
  }

  @Test
  @DisplayName("signed zero survives between the two floating types; every other target answers plain zero")
  void negativeZeroKeepsItsSignAcrossTheFloatingTypes() {
    Value<Float> asFloat = Numerics.exact(-0.0d, Float.class);
    Value<Double> asDouble = Numerics.exact(-0.0f, Double.class);

    assertThat(asFloat).isEqualTo(Value.of(-0.0f));
    assertThat(asFloat).isNotEqualTo(Value.of(0.0f));
    assertThat(asDouble).isEqualTo(Value.of(-0.0d));
    assertThat(asDouble).isNotEqualTo(Value.of(0.0d));

    assertThat(Numerics.exact(0.0d, Float.class)).isEqualTo(Value.of(0.0f));
    assertThat(Numerics.exact(0.0f, Double.class)).isEqualTo(Value.of(0.0d));

    assertThat(Numerics.exact(-0.0d, Integer.class)).isEqualTo(Value.of(0));
    assertThat(Numerics.exact(-0.0d, BigDecimal.class)).isEqualTo(Value.of(BigDecimal.ZERO));
  }

  @Test
  @DisplayName("NaN and the infinities ride the two floating types and are refused by every other target")
  void nonFiniteValuesRideOnlyTheFloatingTypes() {
    assertThat(Numerics.exact(Float.NaN, Double.class)).isEqualTo(Value.of(Double.NaN));
    assertThat(Numerics.exact(Double.NaN, Float.class)).isEqualTo(Value.of(Float.NaN));
    assertThat(Numerics.exact(Double.POSITIVE_INFINITY, Float.class)).isEqualTo(Value.of(Float.POSITIVE_INFINITY));
    assertThat(Numerics.exact(Float.NEGATIVE_INFINITY, Double.class)).isEqualTo(Value.of(Double.NEGATIVE_INFINITY));

    assertThat(Numerics.exact(Double.NaN, Integer.class)).hasErrorMessage(
      "Cannot convert Double to Integer: NaN is not a whole number"
    );
    assertThat(Numerics.exact(Double.NEGATIVE_INFINITY, Long.class)).hasErrorMessage(
      "Cannot convert Double to Long: -Infinity is not a whole number"
    );
    assertThat(Numerics.exact(Double.POSITIVE_INFINITY, BigDecimal.class)).hasErrorMessage(
      "Cannot convert Double to BigDecimal: Infinity is not a whole number"
    );
  }

  @Test
  @DisplayName("Long.MIN_VALUE fits wherever its magnitude fits, and is refused where it does not")
  void longMinValueIsCarriedByMagnitude() {
    assertThat(Numerics.exact(Long.MIN_VALUE, Integer.class)).hasErrorMessage(
      "Cannot convert Long to Integer: -9223372036854775808 overflows Integer"
    );
    assertThat(Numerics.exact(Long.MIN_VALUE, Double.class)).isEqualTo(Value.of((double) Long.MIN_VALUE));
    assertThat(Numerics.exact(Long.MIN_VALUE, BigInteger.class)).isEqualTo(
      Value.of(BigInteger.valueOf(Long.MIN_VALUE))
    );
  }

  @Test
  @DisplayName("the range gate includes both ends of the target's range")
  void theRangeGateIsInclusive() {
    assertThat(Numerics.exact(-128, Byte.class)).isEqualTo(Value.of((byte) -128));
    assertThat(Numerics.exact(127, Byte.class)).isEqualTo(Value.of((byte) 127));
    assertThat(Numerics.exact(128, Byte.class)).hasErrorMessage("Cannot convert Integer to Byte: 128 overflows Byte");
    assertThat(Numerics.exact(-129, Byte.class)).hasErrorMessage("Cannot convert Integer to Byte: -129 overflows Byte");
  }

  @Test
  @DisplayName("trailing zeroes do not make a decimal fractional")
  void scaleAloneDoesNotMakeAValueFractional() {
    assertThat(Numerics.exact(new BigDecimal("3.00"), Integer.class)).isEqualTo(Value.of(3));
    assertThat(Numerics.exact(new BigDecimal("3.01"), Integer.class)).hasErrorMessage(
      "Cannot convert BigDecimal to Integer: 3.01 is not a whole number"
    );
  }

  @Test
  @DisplayName("a float widens to a double exactly, even at the subnormal end, but a double does not narrow back")
  void subnormalsWidenButDoNotNarrow() {
    assertThat(Numerics.exact(Float.MIN_VALUE, Double.class)).isEqualTo(Value.of((double) Float.MIN_VALUE));
    assertThat(Numerics.exact(Double.MIN_VALUE, Float.class)).hasErrorMessage(
      "Cannot convert Double to Float: 4.9E-324 is not exactly representable as Float"
    );
  }

  @Test
  @DisplayName("a BigDecimal target keeps the source's exact binary expansion, not its shortest printed form")
  void aBigDecimalTargetKeepsTheExactExpansion() {
    Value<BigDecimal> converted = Numerics.exact(0.1d, BigDecimal.class);

    assertThat(converted).isEqualTo(Value.of(new BigDecimal(0.1d)));
    assertThat(converted)
      .as("BigDecimal.valueOf would round to the shortest decimal that prints the same")
      .isNotEqualTo(Value.of(BigDecimal.valueOf(0.1d)));
  }

  @Test
  @DisplayName("rounding resolves the fraction only — the range gate and the non-finite refusal still apply")
  void roundingDoesNotLiftTheOtherGates() {
    assertThat(Numerics.rounded(3.7d, Integer.class, Rounding.DOWN)).isEqualTo(Value.of(3));
    assertThat(Numerics.rounded(1000.7d, Byte.class, Rounding.DOWN)).hasErrorMessage(
      "Cannot convert Double to Byte: 1000.7 overflows Byte"
    );
    assertThat(Numerics.rounded(Double.NaN, Integer.class, Rounding.HALF_UP)).hasErrorMessage(
      "Cannot convert Double to Integer: NaN is not a whole number"
    );
  }

  @Test
  @DisplayName("a decimal with a huge exponent is range-checked before the exponent is expanded, so it answers at once")
  void aHugeExponentIsRefusedBeforeItIsExpanded() {
    BigDecimal hundredMillionDigits = new BigDecimal("1E+100000000");

    assertTimeoutPreemptively(AT_ONCE, () ->
      assertThat(Numerics.exact(hundredMillionDigits, Integer.class)).isEqualTo(
        Value.errorMessage("Cannot convert BigDecimal to Integer: 1E+100000000 overflows Integer", Integer.class)
      )
    );
    assertTimeoutPreemptively(AT_ONCE, () ->
      assertThat(Numerics.exact(hundredMillionDigits, BigInteger.class)).isEqualTo(
        Value.errorMessage(
          "Cannot convert BigDecimal to BigInteger: 1E+100000000 has more than 1000 digits",
          BigInteger.class
        )
      )
    );
  }

  @Test
  @DisplayName("an exponent beyond BigInteger's range or at the int edge of the scale refuses, never throws")
  void anExponentAtTheScaleEdgeRefusesWithoutAnArithmeticException() {
    BigDecimal beyondBigInteger = new BigDecimal("1E+2000000000");
    BigDecimal atTheScaleEdge = new BigDecimal("100E+2147483647");

    assertTimeoutPreemptively(AT_ONCE, () ->
      assertThat(Numerics.exact(beyondBigInteger, BigInteger.class)).isEqualTo(
        Value.errorMessage(
          "Cannot convert BigDecimal to BigInteger: 1E+2000000000 has more than 1000 digits",
          BigInteger.class
        )
      )
    );
    assertTimeoutPreemptively(AT_ONCE, () ->
      assertThat(Numerics.exact(atTheScaleEdge, Integer.class)).isEqualTo(
        Value.errorMessage("Cannot convert BigDecimal to Integer: 1.00E+2147483649 overflows Integer", Integer.class)
      )
    );
    assertTimeoutPreemptively(AT_ONCE, () ->
      assertThat(Numerics.exact(atTheScaleEdge, BigInteger.class)).isEqualTo(
        Value.errorMessage(
          "Cannot convert BigDecimal to BigInteger: 1.00E+2147483649 has more than 1000 digits",
          BigInteger.class
        )
      )
    );
  }

  @Test
  @DisplayName("a BigInteger result is capped at 1000 digits, or at the decimal's own precision when that is larger")
  void aBigIntegerResultIsCappedAtAThousandDigits() {
    BigDecimal thousandDigits = new BigDecimal("1E+999");
    BigDecimal thousandAndOneDigits = new BigDecimal("1E+1000");
    BigDecimal fifteenHundredWrittenDigits = new BigDecimal("1".repeat(1500));

    assertThat(Numerics.exact(thousandDigits, BigInteger.class)).isEqualTo(Value.of(BigInteger.TEN.pow(999)));
    assertThat(Numerics.exact(thousandAndOneDigits, BigInteger.class)).isEqualTo(
      Value.errorMessage("Cannot convert BigDecimal to BigInteger: 1E+1000 has more than 1000 digits", BigInteger.class)
    );
    assertThat(Numerics.exact(fifteenHundredWrittenDigits, BigInteger.class)).isEqualTo(
      Value.of(new BigInteger("1".repeat(1500)))
    );
  }

  @Test
  @DisplayName("a zero with a huge exponent is still zero, and answers at once")
  void aZeroWithAHugeExponentAnswersZero() {
    BigDecimal zero = new BigDecimal("0E+100000000");

    assertTimeoutPreemptively(AT_ONCE, () -> assertThat(Numerics.exact(zero, Integer.class)).isEqualTo(Value.of(0)));
  }

  @Test
  @DisplayName("a fraction is refused as not whole before any range check, even when it is also out of range")
  void theFractionCheckComesBeforeTheRangeCheck() {
    assertThat(Numerics.exact(12345678901.5d, Integer.class)).isEqualTo(
      Value.errorMessage("Cannot convert Double to Integer: 1.23456789015E10 is not a whole number", Integer.class)
    );
  }
}
