package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("Units")
class UnitsTest {

  private static final List<Unit> UNPREFIXED_CONSTANTS = List.of(
    Units.METRE,
    Units.GRAM,
    Units.SECOND,
    Units.AMPERE,
    Units.KELVIN,
    Units.MOLE,
    Units.CANDELA,
    Units.RADIAN,
    Units.STERADIAN,
    Units.HERTZ,
    Units.NEWTON,
    Units.PASCAL,
    Units.JOULE,
    Units.WATT,
    Units.COULOMB,
    Units.VOLT,
    Units.FARAD,
    Units.OHM,
    Units.SIEMENS,
    Units.WEBER,
    Units.TESLA,
    Units.HENRY,
    Units.DEGREE_CELSIUS,
    Units.LUMEN,
    Units.LUX,
    Units.BECQUEREL,
    Units.GRAY,
    Units.SIEVERT,
    Units.KATAL,
    Units.MINUTE,
    Units.HOUR,
    Units.DAY,
    Units.LITRE,
    Units.TONNE,
    Units.BIT,
    Units.BYTE
  );

  @Test
  @DisplayName("the physical and information catalogue holds 806 units with 806 distinct symbols")
  void theCatalogueHolds806DistinctSymbols() {
    List<Unit> catalogue = physicalAndInformationCatalogue();
    Set<String> symbols = catalogue.stream().map(Unit::toString).collect(Collectors.toSet());

    assertThat(catalogue).hasSize(806);
    assertThat(symbols).hasSize(806);
    assertThat(new HashSet<>(catalogue)).hasSize(806);
  }

  @Test
  @DisplayName("every catalogue symbol reads back to exactly its own unit")
  void everySymbolReadsBackToItsOwnUnit() {
    for (Unit unit : physicalAndInformationCatalogue()) {
      Value<Quantity> read = Value.measurableOf("1 " + unit);

      assertThat(read.isQuantity()).as(unit.toString()).isTrue();
      assertThat(read.unbox().unit()).as(unit.toString()).isEqualTo(unit);
    }
  }

  @Test
  @DisplayName("no currency code collides with a physical or information symbol")
  void noCurrencyCodeCollidesWithAUnitSymbol() {
    Set<String> symbols = physicalAndInformationCatalogue().stream().map(Unit::toString).collect(Collectors.toSet());
    Set<String> codes = Currency.getAvailableCurrencies()
      .stream()
      .map(Currency::getCurrencyCode)
      .collect(Collectors.toSet());

    assertThat(codes).doesNotContainAnyElementsOf(symbols);
  }

  @Test
  @DisplayName("every JDK currency is a monetary unit written as its ISO 4217 code, equal on every call")
  void everyJdkCurrencyIsAMonetaryUnit() {
    for (Currency currency : Currency.getAvailableCurrencies()) {
      Unit unit = Units.currency(currency);

      assertThat(unit).as(currency.getCurrencyCode()).hasToString(currency.getCurrencyCode());
      assertThat(unit.systems()).as(currency.getCurrencyCode()).containsExactly(UnitSystem.MONETARY);
      assertThat(Units.currency(currency)).as(currency.getCurrencyCode()).isEqualTo(unit);
    }
  }

  @Test
  @DisplayName("two currencies are two different units")
  void twoCurrenciesAreDifferentUnits() {
    assertThat(Units.currency(Currency.getInstance("EUR"))).isNotEqualTo(Units.currency(Currency.getInstance("USD")));
  }

  @Test
  @DisplayName("a null currency is refused")
  void aNullCurrencyIsRefused() {
    assertThatThrownBy(() -> Units.currency(null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("currency must not be null");
  }

  @Test
  @DisplayName("the kilogram is a prefixed unit, the gram carries the mass prefixes")
  void theKilogramIsPrefixed() {
    assertThat(Units.KILOGRAM).isEqualTo(Units.GRAM.withPrefix(Prefix.KILO));
    assertThat(Units.GRAM.withPrefix(Prefix.MILLI)).hasToString("mg");
  }

  private static List<Unit> physicalAndInformationCatalogue() {
    var catalogue = new ArrayList<Unit>();
    for (Unit unit : UNPREFIXED_CONSTANTS) {
      catalogue.add(unit);
      for (Prefix prefix : Prefix.values()) {
        prefixedOrNothing(unit, prefix, catalogue);
      }
    }
    return catalogue;
  }

  private static void prefixedOrNothing(Unit unit, Prefix prefix, List<Unit> catalogue) {
    try {
      catalogue.add(unit.withPrefix(prefix));
    } catch (IllegalArgumentException ignored) {}
  }
}
