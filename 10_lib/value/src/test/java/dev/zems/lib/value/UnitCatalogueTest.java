package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import java.util.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ContractTest
@DisplayName("UnitCatalogue")
class UnitCatalogueTest {

  @Test
  @DisplayName("atom resolves unprefixed, prefixed and currency symbols")
  void atomResolvesEveryKindOfSymbol() {
    assertThat(UnitCatalogue.atom("m").isPrefixed()).isFalse();
    assertThat(UnitCatalogue.atom("km").isPrefixed()).isTrue();
    assertThat(UnitCatalogue.atom("km").dimension()).isEqualTo(Dimension.of(BaseDimension.LENGTH));
    assertThat(UnitCatalogue.atom("EUR").system()).isEqualTo(UnitSystem.MONETARY);
  }

  @Test
  @DisplayName("the micro sign and the ohm sign resolve to the atoms of their Greek letters")
  void theCompatibilitySignsResolveToTheGreekLetters() {
    assertThat(UnitCatalogue.atom("\u00B5m")).isEqualTo(UnitCatalogue.atom("\u03BCm"));
    assertThat(UnitCatalogue.atom("\u00B5m").symbol()).isEqualTo("\u03BCm");
    assertThat(UnitCatalogue.atom("k\u2126")).isEqualTo(UnitCatalogue.atom("k\u03A9"));
    assertThat(UnitCatalogue.atom("k\u2126").symbol()).isEqualTo("k\u03A9");
  }

  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(strings = { "furlong", "eur", "l", "\u2103", "u", "um", "", "M", "kkg", "mt" })
  @DisplayName("a symbol outside the catalogue is refused, named as written")
  void anUnknownSymbolIsRefused(String symbol) {
    assertThatThrownBy(() -> UnitCatalogue.atom(symbol))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("unknown unit symbol '" + symbol + "'");
  }

  @Test
  @DisplayName("prefixed answers the catalogue atom of the prefixed symbol")
  void prefixedAnswersTheCatalogueAtom() {
    assertThat(UnitCatalogue.prefixed(UnitCatalogue.atom("g"), Prefix.KILO)).isEqualTo(UnitCatalogue.atom("kg"));
    assertThat(UnitCatalogue.prefixed(UnitCatalogue.atom("B"), Prefix.QUEBI)).isEqualTo(UnitCatalogue.atom("QiB"));
  }

  @Test
  @DisplayName("currency answers the atom registered under the ISO 4217 code")
  void currencyAnswersTheRegisteredAtom() {
    UnitAtom euro = UnitCatalogue.currency(Currency.getInstance("EUR"));

    assertThat(euro).isEqualTo(UnitCatalogue.atom("EUR"));
    assertThat(euro.dimension()).isEqualTo(Dimension.of(BaseDimension.MONEY));
    assertThat(euro.takesAnyPrefix()).isFalse();
  }
}
