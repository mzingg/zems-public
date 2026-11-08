package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.Unit.Factor;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@ContractTest
@DisplayName("UnitExpression")
class UnitExpressionTest {

  @Nested
  @DisplayName("read")
  class Read {

    @ParameterizedTest(name = "\"{0}\" reads as \"{1}\"")
    @CsvSource(
      delimiter = '|',
      value = {
        "m|m",
        "s*m|m*s",
        "m^1|m",
        "m*m|m^2",
        "m*s/s|m",
        "m/s/s|m/s^2",
        "m/s*s|m",
        "m/s*kg|kg*m/s",
        "m*s^-1|m/s",
        "s^-1|s^-1",
        "m^-2*kg^-1|kg^-1*m^-2",
        "EUR/kg|EUR/kg",
        "kg*m/s^2|kg*m/s^2",
      }
    )
    @DisplayName("a slash negates only the next factor, and equal symbols merge")
    void readNormalises(String expression, String canonical) {
      assertThat(UnitExpression.read(expression)).hasToString(canonical);
    }

    @Test
    @DisplayName("an exponent of Integer.MIN_VALUE reads back from both of its spellings")
    void theMinimumExponentReadsBack() {
      Unit perHugeMetre = Units.METRE.pow(Integer.MIN_VALUE).multiply(Units.SECOND);

      assertThat(UnitExpression.read("s/m^2147483648")).isEqualTo(perHugeMetre);
      assertThat(UnitExpression.read("m^-2147483648")).isEqualTo(Units.METRE.pow(Integer.MIN_VALUE));
      assertThat(UnitExpression.read("m^2147483647")).isEqualTo(Units.METRE.pow(Integer.MAX_VALUE));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(
      strings = {
        "",
        "m/",
        "m*",
        "*m",
        "/m",
        "m//s",
        "m**s",
        "^2",
        "m^",
        "m^0",
        "m^-0",
        "m^01",
        "m^+2",
        "m^x",
        "m^1^2",
        "m^-",
        "m s",
        "m/m",
        "m*s/m/s",
        "m^2147483648",
        "m^-2147483649",
        "m^99999999999",
        "m^2147483647*m",
      }
    )
    @DisplayName("a malformed expression is refused, named whole")
    void aMalformedExpressionIsRefused(String expression) {
      assertThatThrownBy(() -> UnitExpression.read(expression))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("malformed unit expression '" + expression + "'");
    }

    @Test
    @DisplayName("an unknown symbol is refused by its own name, not as a malformed expression")
    void anUnknownSymbolIsNamed() {
      assertThatThrownBy(() -> UnitExpression.read("kg*furlong/s"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("unknown unit symbol 'furlong'");
    }
  }

  @Nested
  @DisplayName("write")
  class Write {

    @Test
    @DisplayName("positive factors join with a star, negative ones follow a slash, ^n only above 1")
    void positiveThenNegativeFactors() {
      List<Factor> factors = List.of(
        new Factor(UnitCatalogue.atom("kg"), 1),
        new Factor(UnitCatalogue.atom("m"), 2),
        new Factor(UnitCatalogue.atom("s"), -3),
        new Factor(UnitCatalogue.atom("A"), -1)
      );

      assertThat(UnitExpression.write(factors)).isEqualTo("kg*m^2/s^3/A");
    }

    @Test
    @DisplayName("with no positive factor every factor carries its negative exponent")
    void onlyNegativeFactors() {
      List<Factor> factors = List.of(new Factor(UnitCatalogue.atom("m"), -1), new Factor(UnitCatalogue.atom("s"), -2));

      assertThat(UnitExpression.write(factors)).isEqualTo("m^-1*s^-2");
    }
  }
}
