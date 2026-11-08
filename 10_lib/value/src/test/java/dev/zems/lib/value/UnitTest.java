package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import java.util.Currency;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("Unit")
class UnitTest {

  private static final Unit EURO = Units.currency(Currency.getInstance("EUR"));

  @Nested
  @DisplayName("multiply, divide and pow")
  class Combine {

    @Test
    @DisplayName("exponents of a shared unit add on multiply and subtract on divide")
    void exponentsAddAndSubtract() {
      assertThat(Units.METRE.multiply(Units.METRE.pow(2))).isEqualTo(Units.METRE.pow(3));
      assertThat(Units.METRE.pow(3).divide(Units.METRE)).isEqualTo(Units.METRE.pow(2));
      assertThat(Units.METRE.divide(Units.SECOND).multiply(Units.SECOND)).isEqualTo(Units.METRE);
      assertThat(Units.METRE.divide(Units.SECOND).pow(-2)).hasToString("s^2/m^2");
    }

    @Test
    @DisplayName("the construction order changes neither equality, hash nor text")
    void theConstructionOrderDoesNotShow() {
      Unit first = Units.KILOGRAM.multiply(Units.METRE).divide(Units.SECOND.pow(2));
      Unit second = Units.SECOND.pow(-2).multiply(Units.METRE).multiply(Units.KILOGRAM);

      assertThat(second).isEqualTo(first).hasSameHashCodeAs(first).hasToString("kg*m/s^2");
    }

    @Test
    @DisplayName("factors are ordered by symbol code point, ASCII before the Greek and degree symbols")
    void factorsAreOrderedByCodePoint() {
      assertThat(Units.KILOGRAM.multiply(Units.AMPERE)).hasToString("A*kg");
      assertThat(Units.OHM.multiply(Units.METRE)).hasToString("m*\u03A9");
      assertThat(Units.DEGREE_CELSIUS.multiply(Units.SECOND)).hasToString("s*\u00B0C");
      assertThat(EURO.divide(Units.KILOGRAM.multiply(Units.SECOND))).hasToString("EUR/kg/s");
    }

    @Test
    @DisplayName("a unit with only negative exponents writes every factor with its exponent")
    void onlyNegativeExponentsAreWrittenWithTheirSign() {
      assertThat(Units.SECOND.pow(-1)).hasToString("s^-1");
      assertThat(Units.METRE.pow(-2).multiply(Units.KILOGRAM.pow(-1))).hasToString("kg^-1*m^-2");
    }

    @Test
    @DisplayName("an exponent of Integer.MIN_VALUE after a slash is written as its magnitude")
    void theMinimumExponentIsWrittenAsItsMagnitude() {
      assertThat(Units.METRE.pow(Integer.MIN_VALUE).multiply(Units.SECOND)).hasToString("s/m^2147483648");
      assertThat(Units.METRE.pow(Integer.MIN_VALUE)).hasToString("m^-2147483648");
    }

    @Test
    @DisplayName("units that cancel out are refused")
    void cancellingUnitsAreRefused() {
      assertThatThrownBy(() -> Units.METRE.divide(Units.METRE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Units 'm' and 'm' cancel out");
      assertThatThrownBy(() -> Units.METRE.divide(Units.SECOND).multiply(Units.SECOND.divide(Units.METRE)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Units 'm/s' and 's/m' cancel out");
    }

    @Test
    @DisplayName("an exponent of 0 is refused")
    void aZeroExponentIsRefused() {
      assertThatThrownBy(() -> Units.METRE.pow(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Exponent must not be 0");
    }

    @Test
    @DisplayName("an exponent overflowing int is refused, naming the unit")
    void anOverflowingExponentIsRefused() {
      assertThatThrownBy(() -> Units.METRE.pow(Integer.MAX_VALUE).multiply(Units.METRE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Exponent of 'm' overflows");
      assertThatThrownBy(() -> Units.METRE.pow(Integer.MIN_VALUE).divide(Units.METRE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Exponent of 'm' overflows");
      assertThatThrownBy(() -> Units.SECOND.pow(2).pow(Integer.MAX_VALUE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Exponent of 's' overflows");
      assertThatThrownBy(() -> Units.SECOND.pow(Integer.MIN_VALUE).pow(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Exponent of 's' overflows");
    }

    @Test
    @DisplayName("a null operand is refused")
    void aNullOperandIsRefused() {
      assertThatThrownBy(() -> Units.METRE.multiply(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("other must not be null");
      assertThatThrownBy(() -> Units.METRE.divide(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("other must not be null");
    }
  }

  @Nested
  @DisplayName("withPrefix")
  class WithPrefix {

    @Test
    @DisplayName("a unit taking a prefix answers the prefixed catalogue unit")
    void aPrefixedUnitIsTheCatalogueUnit() {
      assertThat(Units.GRAM.withPrefix(Prefix.KILO)).isEqualTo(Units.KILOGRAM);
      assertThat(Units.TONNE.withPrefix(Prefix.KILO)).hasToString("kt");
      assertThat(Units.BIT.withPrefix(Prefix.KIBI)).hasToString("Kibit");
      assertThat(Units.DEGREE_CELSIUS.withPrefix(Prefix.MILLI)).hasToString("m\u00B0C");
      assertThat(Units.LITRE.withPrefix(Prefix.MICRO)).hasToString("\u03BCL");
    }

    @Test
    @DisplayName("minute, hour, day and currencies take no prefix")
    void someUnitsTakeNoPrefix() {
      assertThatThrownBy(() -> Units.MINUTE.withPrefix(Prefix.KILO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unit 'min' takes no prefix");
      assertThatThrownBy(() -> Units.HOUR.withPrefix(Prefix.MILLI))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unit 'h' takes no prefix");
      assertThatThrownBy(() -> Units.DAY.withPrefix(Prefix.KILO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unit 'd' takes no prefix");
      assertThatThrownBy(() -> EURO.withPrefix(Prefix.KILO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unit 'EUR' takes no prefix");
    }

    @Test
    @DisplayName("tonne, bit and byte refuse the submultiples, and only bit and byte take the binary prefixes")
    void theRestrictedPrefixRules() {
      assertThatThrownBy(() -> Units.TONNE.withPrefix(Prefix.MILLI))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unit 't' does not take the prefix 'm'");
      assertThatThrownBy(() -> Units.TONNE.withPrefix(Prefix.HECTO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unit 't' does not take the prefix 'h'");
      assertThatThrownBy(() -> Units.TONNE.withPrefix(Prefix.KIBI))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unit 't' does not take the prefix 'Ki'");
      assertThatThrownBy(() -> Units.BYTE.withPrefix(Prefix.DECI))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unit 'B' does not take the prefix 'd'");
      assertThatThrownBy(() -> Units.BIT.withPrefix(Prefix.DECA))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unit 'bit' does not take the prefix 'da'");
      assertThatThrownBy(() -> Units.METRE.withPrefix(Prefix.KIBI))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unit 'm' does not take the prefix 'Ki'");
    }

    @Test
    @DisplayName("only a single unprefixed unit with exponent 1 takes a prefix")
    void onlyASingleUnprefixedUnitTakesAPrefix() {
      assertThatThrownBy(() -> Units.KILOGRAM.withPrefix(Prefix.MILLI))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Only a single unprefixed unit takes a prefix, not 'kg'");
      assertThatThrownBy(() -> Units.METRE.divide(Units.SECOND).withPrefix(Prefix.KILO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Only a single unprefixed unit takes a prefix, not 'm/s'");
      assertThatThrownBy(() -> Units.METRE.pow(2).withPrefix(Prefix.KILO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Only a single unprefixed unit takes a prefix, not 'm^2'");
    }

    @Test
    @DisplayName("a null prefix is refused")
    void aNullPrefixIsRefused() {
      assertThatThrownBy(() -> Units.METRE.withPrefix(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("prefix must not be null");
    }
  }

  @Test
  @DisplayName("systems() names the system of every part, once")
  void systemsNamesEveryPart() {
    assertThat(Units.BYTE.divide(Units.SECOND).systems()).containsExactlyInAnyOrder(
      UnitSystem.DIGITAL_INFORMATION,
      UnitSystem.PHYSICAL
    );
    assertThat(EURO.divide(Units.BYTE.multiply(Units.SECOND)).systems()).containsExactlyInAnyOrder(
      UnitSystem.MONETARY,
      UnitSystem.DIGITAL_INFORMATION,
      UnitSystem.PHYSICAL
    );
    assertThat(Units.METRE.multiply(Units.SECOND).systems()).isEqualTo(Set.of(UnitSystem.PHYSICAL));
  }

  @Nested
  @DisplayName("dimension")
  class DimensionOfAUnit {

    @Test
    @DisplayName("a named derived unit has the dimension of its SI definition")
    void aDerivedUnitHasItsSiDimension() {
      assertThat(Units.NEWTON.dimension()).isEqualTo(
        Units.KILOGRAM.multiply(Units.METRE).divide(Units.SECOND.pow(2)).dimension()
      );
      assertThat(Units.JOULE.dimension()).isEqualTo(Units.NEWTON.multiply(Units.METRE).dimension());
      assertThat(Units.WATT.dimension()).isEqualTo(Units.JOULE.divide(Units.SECOND).dimension());
      assertThat(Units.PASCAL.dimension()).isEqualTo(Units.NEWTON.divide(Units.METRE.pow(2)).dimension());
      assertThat(Units.COULOMB.dimension()).isEqualTo(Units.AMPERE.multiply(Units.SECOND).dimension());
      assertThat(Units.VOLT.dimension()).isEqualTo(Units.WATT.divide(Units.AMPERE).dimension());
      assertThat(Units.OHM.dimension()).isEqualTo(Units.VOLT.divide(Units.AMPERE).dimension());
      assertThat(Units.SIEMENS.dimension()).isEqualTo(Units.OHM.pow(-1).dimension());
      assertThat(Units.FARAD.dimension()).isEqualTo(Units.COULOMB.divide(Units.VOLT).dimension());
      assertThat(Units.WEBER.dimension()).isEqualTo(Units.VOLT.multiply(Units.SECOND).dimension());
      assertThat(Units.TESLA.dimension()).isEqualTo(Units.WEBER.divide(Units.METRE.pow(2)).dimension());
      assertThat(Units.HENRY.dimension()).isEqualTo(Units.WEBER.divide(Units.AMPERE).dimension());
      assertThat(Units.LUMEN.dimension()).isEqualTo(Units.CANDELA.multiply(Units.STERADIAN).dimension());
      assertThat(Units.LUX.dimension()).isEqualTo(Units.LUMEN.divide(Units.METRE.pow(2)).dimension());
      assertThat(Units.GRAY.dimension()).isEqualTo(Units.JOULE.divide(Units.KILOGRAM).dimension());
      assertThat(Units.SIEVERT.dimension()).isEqualTo(Units.GRAY.dimension());
      assertThat(Units.KATAL.dimension()).isEqualTo(Units.MOLE.divide(Units.SECOND).dimension());
      assertThat(Units.HERTZ.dimension()).isEqualTo(Units.SECOND.pow(-1).dimension());
      assertThat(Units.BECQUEREL.dimension()).isEqualTo(Units.HERTZ.dimension());
    }

    @Test
    @DisplayName("the non-SI units share the dimension of their SI counterparts")
    void nonSiUnitsShareTheirSiDimension() {
      assertThat(Units.MINUTE.dimension()).isEqualTo(Units.SECOND.dimension());
      assertThat(Units.HOUR.dimension()).isEqualTo(Units.SECOND.dimension());
      assertThat(Units.DAY.dimension()).isEqualTo(Units.SECOND.dimension());
      assertThat(Units.LITRE.dimension()).isEqualTo(Units.METRE.pow(3).dimension());
      assertThat(Units.TONNE.dimension()).isEqualTo(Units.KILOGRAM.dimension());
      assertThat(Units.DEGREE_CELSIUS.dimension()).isEqualTo(Units.KELVIN.dimension());
      assertThat(Units.BYTE.withPrefix(Prefix.KIBI).dimension()).isEqualTo(Units.BIT.dimension());
    }

    @Test
    @DisplayName("radian and steradian are dimensionless, while bit and money have base dimensions of their own")
    void informationAndMoneyAreBaseDimensions() {
      assertThat(Units.RADIAN.dimension()).isEqualTo(Dimension.NONE);
      assertThat(Units.STERADIAN.dimension()).isEqualTo(Dimension.NONE);
      assertThat(Units.BIT.dimension()).isEqualTo(Dimension.of(BaseDimension.INFORMATION));
      assertThat(Units.BIT.dimension()).isNotEqualTo(Units.RADIAN.dimension());
      assertThat(EURO.dimension()).isEqualTo(Dimension.of(BaseDimension.MONEY));
      assertThat(EURO.dimension()).isEqualTo(Units.currency(Currency.getInstance("USD")).dimension());
      assertThat(EURO.divide(Units.KILOGRAM).dimension()).isEqualTo(
        Dimension.of(BaseDimension.MONEY).with(BaseDimension.MASS, -1)
      );
    }
  }
}
