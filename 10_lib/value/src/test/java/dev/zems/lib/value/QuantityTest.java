package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@ContractTest
@DisplayName("Quantity")
class QuantityTest {

  @Nested
  @DisplayName("parse")
  class Parse {

    @Test
    @DisplayName("null text is refused")
    void nullTextIsRefused() {
      assertThatThrownBy(() -> Quantity.parse(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Quantity text must not be null or empty");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = { "", "   " })
    @DisplayName("blank text is refused")
    void blankTextIsRefused(String text) {
      assertThatThrownBy(() -> Quantity.parse(text))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Quantity text must not be null or empty");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = { "5km", "5  km", " 5 km", "5 km ", "5 m / s", "km", "5\tkm" })
    @DisplayName("anything but a magnitude, exactly one space and a unit is refused")
    void theShapeIsOneMagnitudeOneSpaceOneUnit(String text) {
      assertThatThrownBy(() -> Quantity.parse(text))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid quantity '" + text + "': expected a magnitude, one space and a unit");
    }

    @Test
    @DisplayName("a magnitude that is not a decimal number is named")
    void aNonDecimalMagnitudeIsNamed() {
      assertThatThrownBy(() -> Quantity.parse("abc m"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid quantity 'abc m': magnitude 'abc' is not a decimal number");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @CsvSource(
      delimiter = '|',
      value = { "5 m^x|m^x", "5 m//s|m//s", "5 m/m|m/m", "5 m^99999999999|m^99999999999", "5 m/|m/", "5 m^0|m^0" }
    )
    @DisplayName("a malformed unit expression is named")
    void aMalformedExpressionIsNamed(String text, String expression) {
      assertThatThrownBy(() -> Quantity.parse(text))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid quantity '" + text + "': malformed unit expression '" + expression + "'");
    }

    @Test
    @DisplayName("an unknown symbol is named, whether it looks like a unit or a currency code")
    void anUnknownSymbolIsNamed() {
      assertThatThrownBy(() -> Quantity.parse("5 kg*furlong"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid quantity '5 kg*furlong': unknown unit symbol 'furlong'");
      assertThatThrownBy(() -> Quantity.parse("5 XYZ/kg"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid quantity '5 XYZ/kg': unknown unit symbol 'XYZ'");
    }

    @ParameterizedTest(name = "\"{0}\" is written \"{1}\"")
    @CsvSource(delimiter = '|', value = { "+5 m|5 m", ".5 m|0.5 m", "1e3 m|1E+3 m", "-2.50 m|-2.50 m" })
    @DisplayName("the magnitude reads leniently and writes in its canonical form")
    void theMagnitudeReadsLenientlyAndWritesCanonically(String text, String canonical) {
      Quantity quantity = Quantity.parse(text);

      assertThat(quantity).hasToString(canonical);
      assertThat(Quantity.parse(canonical)).isEqualTo(quantity);
    }
  }

  @Nested
  @DisplayName("equality and hash")
  class Equality {

    @Test
    @DisplayName("equality is numeric within one unit")
    void equalityIsNumeric() {
      Quantity plain = Quantity.of(new BigDecimal("1000"), Units.METRE);
      Quantity scaled = Quantity.of(new BigDecimal("1000.000"), Units.METRE);
      Quantity negativeScale = Quantity.of(new BigDecimal("1E+3"), Units.METRE);

      assertThat(scaled).isEqualTo(plain).hasSameHashCodeAs(plain);
      assertThat(negativeScale).isEqualTo(plain).hasSameHashCodeAs(plain);
      assertThat(Quantity.of(new BigDecimal("1001"), Units.METRE)).isNotEqualTo(plain);
      assertThat(Quantity.of(new BigDecimal("-1000"), Units.METRE)).isNotEqualTo(plain);
    }

    @Test
    @DisplayName("the same magnitude in another unit is a different quantity")
    void anotherUnitIsDifferent() {
      assertThat(Quantity.of(BigDecimal.ONE, Units.METRE)).isNotEqualTo(Quantity.of(BigDecimal.ONE, Units.SECOND));
      assertThat(Quantity.of(BigDecimal.ONE, Units.METRE)).isNotEqualTo(
        Quantity.of(BigDecimal.ONE, Units.METRE.withPrefix(Prefix.KILO))
      );
    }

    @Test
    @DisplayName("every spelling of zero is equal and hashes alike")
    void everyZeroHashesAlike() {
      Quantity zero = Quantity.of(new BigDecimal("0"), Units.METRE);
      Quantity zeroPointZero = Quantity.of(new BigDecimal("0.0"), Units.METRE);
      Quantity zeroNegativeScale = Quantity.of(new BigDecimal("0E+3"), Units.METRE);

      assertThat(zeroPointZero).isEqualTo(zero).hasSameHashCodeAs(zero);
      assertThat(zeroNegativeScale).isEqualTo(zero).hasSameHashCodeAs(zero);
    }

    @Test
    @DisplayName("the hash does not throw at the extreme scales")
    void theHashDoesNotThrowAtExtremeScales() {
      Quantity minimumScale = Quantity.of(new BigDecimal(BigInteger.TEN, Integer.MIN_VALUE), Units.METRE);
      Quantity maximumScale = Quantity.of(new BigDecimal(BigInteger.TEN, Integer.MAX_VALUE), Units.METRE);

      assertThatCode(minimumScale::hashCode).doesNotThrowAnyException();
      assertThatCode(maximumScale::hashCode).doesNotThrowAnyException();
      assertThat(minimumScale).isNotEqualTo(maximumScale);
    }
  }

  @Test
  @DisplayName("a magnitude with scale Integer.MIN_VALUE round-trips through its text")
  void theMinimumScaleRoundTrips() {
    Quantity quantity = Quantity.of(new BigDecimal(BigInteger.TEN, Integer.MIN_VALUE), Units.METRE);

    Quantity reread = Quantity.parse(quantity.toString());

    assertThat(reread).isEqualTo(quantity);
    assertThat(reread.magnitude().scale()).isEqualTo(Integer.MIN_VALUE);
    assertThat(reread.magnitude().unscaledValue()).isEqualTo(BigInteger.TEN);
  }
}
