package dev.zems.lib.value.json.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.zems.lib.common.junit.ContractTest;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("JsonNumber")
@ContractTest
class JsonNumberTest {

  private static final Duration AT_ONCE = Duration.ofSeconds(1);
  private static final String EXACT_VALUE = "new BigDecimal(toString()) for the exact value";
  private static final String NOT_A_NUMBER_RULE =
    ": the text is not a valid JSON number; pass text such as 12, -3.5 or 1e10";

  @Nested
  @DisplayName("asInt")
  class AsInt {

    static Stream<Conversion<Integer>> wholeNumbers() {
      return Stream.of(
        new Conversion<>("123.0", 123),
        new Conversion<>("1.23e2", 123),
        new Conversion<>("1500e-2", 15),
        new Conversion<>("-12.50E1", -125),
        new Conversion<>("0.000", 0),
        new Conversion<>("-0", 0),
        new Conversion<>("2147483647", Integer.MAX_VALUE),
        new Conversion<>("-2147483648", Integer.MIN_VALUE)
      );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wholeNumbers")
    @DisplayName("converts a whole number in the int range, whatever its notation")
    void convertsWholeNumbers(Conversion<Integer> conversion) {
      assertThat(JsonNumber.of(conversion.text()).asInt()).isEqualTo(conversion.expected());
      assertThat(Json.parse(conversion.text()).asInt()).isEqualTo(conversion.expected());
    }

    @Test
    @DisplayName("zero digits convert to 0 whatever the exponent, at once")
    void zeroWithHugeExponentIsZero() {
      assertTimeoutPreemptively(AT_ONCE, () -> assertThat(JsonNumber.of("0e999999999999").asInt()).isZero());
    }

    @Test
    @DisplayName("a number with a fraction is refused as not whole (V4)")
    void refusesFractions() {
      assertThatThrownBy(() -> JsonNumber.of("345.6").asInt())
        .isInstanceOf(JsonValueException.class)
        .hasMessage("cannot read the value as an int: 345.6 is not a whole number; use asDouble(), or " + EXACT_VALUE);
      assertThatThrownBy(() -> JsonNumber.of("1e-400").asInt())
        .isInstanceOf(JsonValueException.class)
        .hasMessage("cannot read the value as an int: 1e-400 is not a whole number; use asDouble(), or " + EXACT_VALUE);
      assertThatThrownBy(() -> Json.parse("[1.5]").get(0).asInt())
        .isInstanceOf(JsonValueException.class)
        .hasMessage(
          "cannot read $[0] (line 1, column 2) as an int: 1.5 is not a whole number; use asDouble(), or " + EXACT_VALUE
        );
    }

    @Test
    @DisplayName("a tiny number with a huge negative exponent is refused as not whole, at once")
    void refusesHugeNegativeExponentAtOnce() {
      assertTimeoutPreemptively(AT_ONCE, () ->
        assertThatThrownBy(() -> JsonNumber.of("1e-999999999999").asInt())
          .isInstanceOf(JsonValueException.class)
          .hasMessage(
            "cannot read the value as an int: 1e-999999999999 is not a whole number; use asDouble(), or " + EXACT_VALUE
          )
      );
    }

    @Test
    @DisplayName("a whole number outside the int range is refused (V5)")
    void refusesOutsideTheIntRange() {
      assertThatThrownBy(() -> JsonNumber.of("2147483648").asInt())
        .isInstanceOf(JsonValueException.class)
        .hasMessage(
          "cannot read the value as an int: 2147483648 is outside the int range; use asLong(), or " + EXACT_VALUE
        );
      assertThatThrownBy(() -> JsonNumber.of("-2147483649").asInt())
        .isInstanceOf(JsonValueException.class)
        .hasMessage(
          "cannot read the value as an int: -2147483649 is outside the int range; use asLong(), or " + EXACT_VALUE
        );
      assertThatThrownBy(() -> JsonNumber.of("1e400").asInt())
        .isInstanceOf(JsonValueException.class)
        .hasMessage("cannot read the value as an int: 1e400 is outside the int range; use asLong(), or " + EXACT_VALUE);
    }

    @Test
    @DisplayName("number text in a message is cut at 40 characters")
    void cutsLongNumberText() {
      assertThatThrownBy(() -> JsonNumber.of("1".repeat(50)).asInt())
        .isInstanceOf(JsonValueException.class)
        .hasMessage(
          "cannot read the value as an int: " +
            "1".repeat(40) +
            "… is outside the int range; use asLong(), or " +
            EXACT_VALUE
        );
    }
  }

  @Nested
  @DisplayName("asLong")
  class AsLong {

    static Stream<Conversion<Long>> wholeNumbers() {
      return Stream.of(
        new Conversion<>("234.56E2", 23_456L),
        new Conversion<>("2147483648", 2_147_483_648L),
        new Conversion<>("1e18", 1_000_000_000_000_000_000L),
        new Conversion<>("12345678901234567890e-1", 1_234_567_890_123_456_789L),
        new Conversion<>("9223372036854775807", Long.MAX_VALUE),
        new Conversion<>("-9223372036854775808", Long.MIN_VALUE)
      );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wholeNumbers")
    @DisplayName("converts a whole number in the long range, whatever its notation")
    void convertsWholeNumbers(Conversion<Long> conversion) {
      assertThat(JsonNumber.of(conversion.text()).asLong()).isEqualTo(conversion.expected());
      assertThat(Json.parse(conversion.text()).asLong()).isEqualTo(conversion.expected());
    }

    @Test
    @DisplayName("a number with a fraction is refused as not whole, before its range is checked (V4)")
    void refusesFractions() {
      assertThatThrownBy(() -> JsonNumber.of("0.5").asLong())
        .isInstanceOf(JsonValueException.class)
        .hasMessage("cannot read the value as a long: 0.5 is not a whole number; use asDouble(), or " + EXACT_VALUE);
      assertThatThrownBy(() -> JsonNumber.of("12345678901234567890.5").asLong())
        .isInstanceOf(JsonValueException.class)
        .hasMessage(
          "cannot read the value as a long: 12345678901234567890.5 is not a whole number; use asDouble(), or " +
            EXACT_VALUE
        );
    }

    @Test
    @DisplayName("a whole number outside the long range is refused (V5)")
    void refusesOutsideTheLongRange() {
      assertThatThrownBy(() -> JsonNumber.of("9223372036854775808").asLong())
        .isInstanceOf(JsonValueException.class)
        .hasMessage(
          "cannot read the value as a long: 9223372036854775808 is outside the long range; use " + EXACT_VALUE
        );
      assertThatThrownBy(() -> JsonNumber.of("-9223372036854775809").asLong())
        .isInstanceOf(JsonValueException.class)
        .hasMessage(
          "cannot read the value as a long: -9223372036854775809 is outside the long range; use " + EXACT_VALUE
        );
      assertThatThrownBy(() -> JsonNumber.of("1e19").asLong())
        .isInstanceOf(JsonValueException.class)
        .hasMessage("cannot read the value as a long: 1e19 is outside the long range; use " + EXACT_VALUE);
    }

    @Test
    @DisplayName("a number with a huge exponent is refused as outside the range, at once")
    void refusesHugeExponentAtOnce() {
      assertTimeoutPreemptively(AT_ONCE, () ->
        assertThatThrownBy(() -> JsonNumber.of("1e999999999999").asLong())
          .isInstanceOf(JsonValueException.class)
          .hasMessage("cannot read the value as a long: 1e999999999999 is outside the long range; use " + EXACT_VALUE)
      );
    }
  }

  @Nested
  @DisplayName("asDouble")
  class AsDouble {

    @Test
    @DisplayName("answers the nearest double, rounding excess digits and underflowing to zero")
    void answersTheNearestDouble() {
      assertThat(JsonNumber.of("3.141592653589793238462643383279").asDouble()).isEqualTo(3.141592653589793);
      assertThat(JsonNumber.of("1e308").asDouble()).isEqualTo(1.0e308);
      assertThat(Double.toString(JsonNumber.of("1e-400").asDouble())).isEqualTo("0.0");
      assertThat(Double.toString(JsonNumber.of("-0").asDouble())).isEqualTo("-0.0");
      assertThat(Double.toString(Json.parse("-0").asDouble())).isEqualTo("-0.0");
    }

    @Test
    @DisplayName("a number beyond the finite double range is refused (V6)")
    void refusesBeyondTheFiniteRange() {
      assertThatThrownBy(() -> JsonNumber.of("1e400").asDouble())
        .isInstanceOf(JsonValueException.class)
        .hasMessage("cannot read the value as a double: 1e400 is outside the finite double range; use " + EXACT_VALUE);
      assertThatThrownBy(() -> JsonNumber.of("-1.8E309").asDouble())
        .isInstanceOf(JsonValueException.class)
        .hasMessage(
          "cannot read the value as a double: -1.8E309 is outside the finite double range; use " + EXACT_VALUE
        );
      assertThatThrownBy(() -> Json.parse("{\"x\": 1.8E309}").get("x").asDouble())
        .isInstanceOf(JsonValueException.class)
        .hasMessage(
          "cannot read $.x (line 1, column 7) as a double: 1.8E309 is outside the finite double range; use " +
            EXACT_VALUE
        );
    }
  }

  @Nested
  @DisplayName("toString")
  class ToString {

    @Test
    @DisplayName("a parsed number keeps its source text")
    void parsedNumberKeepsSourceText() {
      JsonValue numbers = Json.parse("[ -0.0E+00 , 1e999999999999 , 3.141592653589793238462643383279 ]");

      assertThat(numbers.get(0).toString()).isEqualTo("-0.0E+00");
      assertThat(numbers.get(1).toString()).isEqualTo("1e999999999999");
      assertThat(new BigDecimal(numbers.get(2).toString())).isEqualTo(
        new BigDecimal("3.141592653589793238462643383279")
      );
    }

    @Test
    @DisplayName("a built number renders the JDK's text for int, long and double")
    void builtNumberRendersJdkText() {
      assertThat(JsonNumber.of(-5).toString()).isEqualTo("-5");
      assertThat(JsonNumber.of(Long.MIN_VALUE).toString()).isEqualTo("-9223372036854775808");
      assertThat(JsonNumber.of(0.1).toString()).isEqualTo("0.1");
      assertThat(JsonNumber.of(1e10).toString()).isEqualTo("1.0E10");
      assertThat(JsonNumber.of(-0.0).toString()).isEqualTo("-0.0");
    }

    @Test
    @DisplayName("a double's text is a JSON number that converts back")
    void doubleTextConvertsBack() {
      assertThat(JsonNumber.of(1e10).asLong()).isEqualTo(10_000_000_000L);
      assertThat(Json.parse(JsonNumber.of(1e10).toString()).asDouble()).isEqualTo(1e10);
    }
  }

  @Nested
  @DisplayName("of")
  class Of {

    @Test
    @DisplayName("of(String) drops the JSON whitespace around the number")
    void ofTextDropsSurroundingWhitespace() {
      assertThat(JsonNumber.of(" 12 ").toString()).isEqualTo("12");
      assertThat(JsonNumber.of(" 12 ").asInt()).isEqualTo(12);
      assertThat(JsonNumber.of("\t-3.5e+2\r\n").toString()).isEqualTo("-3.5e+2");
    }

    @Test
    @DisplayName("of(String) refuses text that is not exactly one JSON number (F3)")
    void ofTextRefusesNonNumbers() {
      assertThatThrownBy(() -> JsonNumber.of("012"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cannot make a JSON number from \"012\"" + NOT_A_NUMBER_RULE);
      assertThatThrownBy(() -> JsonNumber.of("1 2"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cannot make a JSON number from \"1 2\"" + NOT_A_NUMBER_RULE);
      assertThatThrownBy(() -> JsonNumber.of(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cannot make a JSON number from \"\"" + NOT_A_NUMBER_RULE);
      assertThatThrownBy(() -> JsonNumber.of("+1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cannot make a JSON number from \"+1\"" + NOT_A_NUMBER_RULE);
      assertThatThrownBy(() -> JsonNumber.of("1."))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cannot make a JSON number from \"1.\"" + NOT_A_NUMBER_RULE);
      assertThatThrownBy(() -> JsonNumber.of("NaN"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cannot make a JSON number from \"NaN\"" + NOT_A_NUMBER_RULE);
      assertThatThrownBy(() -> JsonNumber.of("1\n2"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cannot make a JSON number from \"1\\n2\"" + NOT_A_NUMBER_RULE);
      assertThatThrownBy(() -> JsonNumber.of("x".repeat(41)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cannot make a JSON number from \"" + "x".repeat(40) + "…\"" + NOT_A_NUMBER_RULE);
    }

    @Test
    @DisplayName("of(double) refuses NaN and the infinities (F2)")
    void ofDoubleRefusesNonFinite() {
      assertThatThrownBy(() -> JsonNumber.of(Double.NaN))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
          "cannot make a JSON number from NaN: JSON numbers must be finite; convert NaN and infinite values to null or a" +
            " string first"
        );
      assertThatThrownBy(() -> JsonNumber.of(Double.POSITIVE_INFINITY))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
          "cannot make a JSON number from Infinity: JSON numbers must be finite; convert NaN and infinite values to null" +
            " or a string first"
        );
      assertThatThrownBy(() -> JsonNumber.of(Double.NEGATIVE_INFINITY))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
          "cannot make a JSON number from -Infinity: JSON numbers must be finite; convert NaN and infinite values to null" +
            " or a string first"
        );
    }

    @Test
    @DisplayName("of(String) refuses a null text with NullPointerException")
    void ofTextRefusesNull() {
      assertThatThrownBy(() -> JsonNumber.of((String) null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("text must not be null");
    }
  }

  record Conversion<T>(String text, T expected) {
    @Override
    public String toString() {
      return text;
    }
  }
}
