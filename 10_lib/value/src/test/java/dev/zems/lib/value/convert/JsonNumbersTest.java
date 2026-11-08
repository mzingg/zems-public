package dev.zems.lib.value.convert;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.json.engine.JsonNumber;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("JsonNumbers — the JSON number rows, lossless per value and decided on the number text")
class JsonNumbersTest {

  private static final Duration AT_ONCE = Duration.ofSeconds(1);

  @Test
  @DisplayName("the table holds exactly the five rows, all keyed on JsonNumber")
  void theTableHoldsTheFiveRows() {
    assertThat(JsonNumbers.rows())
      .extracting(
        conversion -> conversion.sourceType().getSimpleName() + " -> " + conversion.targetType().getSimpleName()
      )
      .containsExactly(
        "JsonNumber -> Integer",
        "JsonNumber -> Long",
        "JsonNumber -> BigInteger",
        "JsonNumber -> BigDecimal",
        "JsonNumber -> Double"
      );
  }

  @Test
  @DisplayName("a zero with a huge exponent converts to zero for every target, at once")
  void aZeroWithAHugeExponentIsZero() {
    assertTimeoutPreemptively(AT_ONCE, () -> {
      assertThat(convert("0e999999999999", Integer.class)).isEqualTo(Value.of(0));
      assertThat(convert("0e999999999999", Long.class)).isEqualTo(Value.of(0L));
      assertThat(convert("0e999999999999", BigInteger.class)).isEqualTo(Value.of(BigInteger.ZERO));
      assertThat(convert("0e999999999999", BigDecimal.class)).isEqualTo(Value.of(BigDecimal.ZERO));
      assertThat(convert("0e999999999999", Double.class)).isEqualTo(Value.of(0.0d));
    });
  }

  @Nested
  @DisplayName("Integer and Long")
  class IntegralTargets {

    @Test
    @DisplayName("both ends of the range answer, one past either end overflows")
    void theRangeIsInclusive() {
      assertThat(convert("-2147483648", Integer.class)).isEqualTo(Value.of(Integer.MIN_VALUE));
      assertThat(convert("2147483647", Integer.class)).isEqualTo(Value.of(Integer.MAX_VALUE));
      assertThat(convert("-2147483649", Integer.class)).isEqualTo(
        refusal("-2147483649 overflows Integer", Integer.class)
      );
      assertThat(convert("-9223372036854775808", Long.class)).isEqualTo(Value.of(Long.MIN_VALUE));
      assertThat(convert("9223372036854775808", Long.class)).isEqualTo(
        refusal("9223372036854775808 overflows Long", Long.class)
      );
    }

    @Test
    @DisplayName("wholeness is decided on the value, not on a fraction or an exponent sign in the text")
    void wholenessIsDecidedOnTheValue() {
      assertThat(convert("1.5e1", Integer.class)).isEqualTo(Value.of(15));
      assertThat(convert("120e-1", Long.class)).isEqualTo(Value.of(12L));
      assertThat(convert("-3.000", Integer.class)).isEqualTo(Value.of(-3));
      assertThat(convert("1.55e1", Integer.class)).isEqualTo(refusal("1.55e1 is not a whole number", Integer.class));
      assertThat(convert("12e-1", Long.class)).isEqualTo(refusal("12e-1 is not a whole number", Long.class));
    }

    @Test
    @DisplayName("too many whole digits overflow, and a huge negative exponent is not whole — both at once")
    void digitCountsDecideAtOnce() {
      assertThat(convert("1e9", Integer.class)).isEqualTo(Value.of(1_000_000_000));
      assertThat(convert("1e10", Integer.class)).isEqualTo(refusal("1e10 overflows Integer", Integer.class));
      assertTimeoutPreemptively(AT_ONCE, () -> {
        assertThat(convert("-1e999999999999", Long.class)).isEqualTo(
          refusal("-1e999999999999 overflows Long", Long.class)
        );
        assertThat(convert("1e-999999999999", Integer.class)).isEqualTo(
          refusal("1e-999999999999 is not a whole number", Integer.class)
        );
      });
    }
  }

  @Nested
  @DisplayName("BigInteger")
  class BigIntegerTarget {

    @Test
    @DisplayName("the cap is 1000 whole digits: 1e999 answers, 1e1000 refuses")
    void theCapIsAThousandDigits() {
      assertThat(convert("1e999", BigInteger.class)).isEqualTo(Value.of(BigInteger.TEN.pow(999)));
      assertThat(convert("-1e3", BigInteger.class)).isEqualTo(Value.of(BigInteger.valueOf(-1000)));
      assertThat(convert("1e1000", BigInteger.class)).isEqualTo(
        refusal("1e1000 has more than 1000 digits", BigInteger.class)
      );
    }

    @Test
    @DisplayName("digits written in the text raise the cap to their own count")
    void writtenDigitsRaiseTheCap() {
      String fifteenHundredDigits = "1".repeat(1500);

      assertThat(convert(fifteenHundredDigits, BigInteger.class)).isEqualTo(
        Value.of(new BigInteger(fifteenHundredDigits))
      );
      assertThat(convert(fifteenHundredDigits + "e1", BigInteger.class)).isEqualTo(
        refusal(fifteenHundredDigits + "e1 has more than 1500 digits", BigInteger.class)
      );
    }

    @Test
    @DisplayName("a fraction is not whole")
    void aFractionIsNotWhole() {
      assertThat(convert("2.5", BigInteger.class)).isEqualTo(refusal("2.5 is not a whole number", BigInteger.class));
    }
  }

  @Nested
  @DisplayName("BigDecimal")
  class BigDecimalTarget {

    @Test
    @DisplayName("the text converts exactly, scale and exponent as written")
    void theTextConvertsExactly() {
      assertThat(convert("1.50", BigDecimal.class)).isEqualTo(Value.of(new BigDecimal("1.50")));
      assertThat(convert("-2E+3", BigDecimal.class)).isEqualTo(Value.of(new BigDecimal("-2E+3")));
    }

    @Test
    @DisplayName("an exponent outside BigDecimal's scale refuses at once, with the JDK's exception as the cause")
    void anExponentOutsideTheScaleRefuses() {
      assertTimeoutPreemptively(AT_ONCE, () -> {
        Value<BigDecimal> refused = convert("1e-999999999999", BigDecimal.class);

        assertThat(refused).isEqualTo(refusal("1e-999999999999 is outside the BigDecimal range", BigDecimal.class));
        assertThat(refused).extractingThrowable().cause().isInstanceOf(NumberFormatException.class);
      });
    }
  }

  @Nested
  @DisplayName("Double")
  class DoubleTarget {

    @Test
    @DisplayName("a subnormal answers, a negative zero keeps its sign, excess digits round")
    void subnormalsAndSignedZeroAnswer() {
      assertThat(convert("1e-320", Double.class)).isEqualTo(Value.of(1.0E-320d));
      assertThat(convert("4.9e-324", Double.class)).isEqualTo(Value.of(Double.MIN_VALUE));
      assertThat(convert("-0", Double.class)).isEqualTo(Value.of(-0.0d));
      assertThat(convert("-0", Double.class)).isNotEqualTo(Value.of(0.0d));
      assertThat(convert("0.1000000000000000055511151231257827", Double.class)).isEqualTo(Value.of(0.1d));
    }

    @Test
    @DisplayName("a non-zero number that would become a zero underflows, either sign, at once")
    void aNonZeroNumberThatBecomesZeroUnderflows() {
      assertThat(convert("2e-324", Double.class)).isEqualTo(refusal("2e-324 underflows Double", Double.class));
      assertThat(convert("-1e-400", Double.class)).isEqualTo(refusal("-1e-400 underflows Double", Double.class));
      assertTimeoutPreemptively(AT_ONCE, () ->
        assertThat(convert("1e-999999999999", Double.class)).isEqualTo(
          refusal("1e-999999999999 underflows Double", Double.class)
        )
      );
    }

    @Test
    @DisplayName("a number beyond the finite range overflows instead of yielding an infinity")
    void aNumberBeyondTheRangeOverflows() {
      assertThat(convert("1.7976931348623157e308", Double.class)).isEqualTo(Value.of(Double.MAX_VALUE));
      assertThat(convert("-1.8e308", Double.class)).isEqualTo(refusal("-1.8e308 overflows Double", Double.class));
    }
  }

  @SuppressWarnings("unchecked") // the row was found under the requested target type
  private static <T> Value<T> convert(String text, Class<T> target) {
    Conversion<JsonNumber, T> row = (Conversion<JsonNumber, T>) JsonNumbers.rows()
      .stream()
      .filter(conversion -> conversion.targetType() == target)
      .findFirst()
      .orElseThrow();
    return row.convert((JsonNumber) Value.parseJson(text).unbox());
  }

  private static <T> Value<T> refusal(String reason, Class<T> target) {
    return Value.errorMessage("Cannot convert JsonNumber to " + target.getSimpleName() + ": " + reason, target);
  }
}
