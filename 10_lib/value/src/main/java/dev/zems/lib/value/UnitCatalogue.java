package dev.zems.lib.value;

import static dev.zems.lib.value.BaseDimension.AMOUNT_OF_SUBSTANCE;
import static dev.zems.lib.value.BaseDimension.ELECTRIC_CURRENT;
import static dev.zems.lib.value.BaseDimension.INFORMATION;
import static dev.zems.lib.value.BaseDimension.LENGTH;
import static dev.zems.lib.value.BaseDimension.LUMINOUS_INTENSITY;
import static dev.zems.lib.value.BaseDimension.MASS;
import static dev.zems.lib.value.BaseDimension.MONEY;
import static dev.zems.lib.value.BaseDimension.TEMPERATURE;
import static dev.zems.lib.value.BaseDimension.TIME;
import static dev.zems.lib.value.UnitAtom.PrefixRule.ALL_DECIMAL;
import static dev.zems.lib.value.UnitAtom.PrefixRule.DECIMAL_MULTIPLES_FROM_KILO;
import static dev.zems.lib.value.UnitAtom.PrefixRule.DECIMAL_MULTIPLES_FROM_KILO_AND_BINARY;
import static dev.zems.lib.value.UnitAtom.PrefixRule.NONE;
import static dev.zems.lib.value.UnitSystem.DIGITAL_INFORMATION;
import static dev.zems.lib.value.UnitSystem.MONETARY;
import static dev.zems.lib.value.UnitSystem.PHYSICAL;

import dev.zems.lib.value.UnitAtom.PrefixRule;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The closed unit catalogue: every atom by its symbol, prefixed forms and the running JDK's currencies included. Built
 * once; a duplicate symbol fails class initialisation.
 */
final class UnitCatalogue {

  private static final String MICRO_SIGN = "µ";
  private static final String GREEK_MU = "μ";
  private static final String OHM_SIGN = "Ω";
  private static final String GREEK_OMEGA = "Ω";

  private static final Dimension LEN = Dimension.of(LENGTH);
  private static final Dimension ENERGY = Dimension.of(MASS).with(LENGTH, 2).with(TIME, -2);
  private static final Dimension POWER = ENERGY.with(TIME, -1);
  private static final Dimension PER_SECOND = Dimension.NONE.with(TIME, -1);

  private static final List<UnitAtom> UNPREFIXED = List.of(
    physical("m", LEN, ALL_DECIMAL),
    physical("g", Dimension.of(MASS), ALL_DECIMAL),
    physical("s", Dimension.of(TIME), ALL_DECIMAL),
    physical("A", Dimension.of(ELECTRIC_CURRENT), ALL_DECIMAL),
    physical("K", Dimension.of(TEMPERATURE), ALL_DECIMAL),
    physical("mol", Dimension.of(AMOUNT_OF_SUBSTANCE), ALL_DECIMAL),
    physical("cd", Dimension.of(LUMINOUS_INTENSITY), ALL_DECIMAL),
    physical("rad", Dimension.NONE, ALL_DECIMAL),
    physical("sr", Dimension.NONE, ALL_DECIMAL),
    physical("Hz", PER_SECOND, ALL_DECIMAL),
    physical("N", ENERGY.with(LENGTH, -1), ALL_DECIMAL),
    physical("Pa", ENERGY.with(LENGTH, -3), ALL_DECIMAL),
    physical("J", ENERGY, ALL_DECIMAL),
    physical("W", POWER, ALL_DECIMAL),
    physical("C", Dimension.of(ELECTRIC_CURRENT).with(TIME, 1), ALL_DECIMAL),
    physical("V", POWER.with(ELECTRIC_CURRENT, -1), ALL_DECIMAL),
    physical("F", POWER.with(ELECTRIC_CURRENT, -2).pow(-1).with(TIME, 1), ALL_DECIMAL),
    physical(GREEK_OMEGA, POWER.with(ELECTRIC_CURRENT, -2), ALL_DECIMAL),
    physical("S", POWER.with(ELECTRIC_CURRENT, -2).pow(-1), ALL_DECIMAL),
    physical("Wb", ENERGY.with(ELECTRIC_CURRENT, -1), ALL_DECIMAL),
    physical("T", ENERGY.with(ELECTRIC_CURRENT, -1).with(LENGTH, -2), ALL_DECIMAL),
    physical("H", ENERGY.with(ELECTRIC_CURRENT, -2), ALL_DECIMAL),
    physical("°C", Dimension.of(TEMPERATURE), ALL_DECIMAL),
    physical("lm", Dimension.of(LUMINOUS_INTENSITY), ALL_DECIMAL),
    physical("lx", Dimension.of(LUMINOUS_INTENSITY).with(LENGTH, -2), ALL_DECIMAL),
    physical("Bq", PER_SECOND, ALL_DECIMAL),
    physical("Gy", ENERGY.with(MASS, -1), ALL_DECIMAL),
    physical("Sv", ENERGY.with(MASS, -1), ALL_DECIMAL),
    physical("kat", Dimension.of(AMOUNT_OF_SUBSTANCE).with(TIME, -1), ALL_DECIMAL),
    physical("min", Dimension.of(TIME), NONE),
    physical("h", Dimension.of(TIME), NONE),
    physical("d", Dimension.of(TIME), NONE),
    physical("L", LEN.pow(3), ALL_DECIMAL),
    physical("t", Dimension.of(MASS), DECIMAL_MULTIPLES_FROM_KILO),
    UnitAtom.of("bit", Dimension.of(INFORMATION), DIGITAL_INFORMATION, DECIMAL_MULTIPLES_FROM_KILO_AND_BINARY),
    UnitAtom.of("B", Dimension.of(INFORMATION), DIGITAL_INFORMATION, DECIMAL_MULTIPLES_FROM_KILO_AND_BINARY)
  );

  private static final Map<String, UnitAtom> BY_SYMBOL = buildTable();

  private UnitCatalogue() {}

  static UnitAtom atom(String symbol) {
    UnitAtom atom = BY_SYMBOL.get(symbol.replace(MICRO_SIGN, GREEK_MU).replace(OHM_SIGN, GREEK_OMEGA));
    if (atom == null) {
      throw new IllegalArgumentException("unknown unit symbol '" + symbol + "'");
    }
    return atom;
  }

  static UnitAtom prefixed(UnitAtom unprefixed, Prefix prefix) {
    return atom(prefix.symbol() + unprefixed.symbol());
  }

  static UnitAtom currency(Currency currency) {
    UnitAtom known = BY_SYMBOL.get(currency.getCurrencyCode());
    return known != null ? known : currencyAtom(currency);
  }

  private static UnitAtom physical(String symbol, Dimension dimension, PrefixRule prefixRule) {
    return UnitAtom.of(symbol, dimension, PHYSICAL, prefixRule);
  }

  private static UnitAtom currencyAtom(Currency currency) {
    return UnitAtom.of(currency.getCurrencyCode(), Dimension.of(MONEY), MONETARY, NONE);
  }

  private static Map<String, UnitAtom> buildTable() {
    var table = new HashMap<String, UnitAtom>();
    for (UnitAtom unprefixed : UNPREFIXED) {
      register(table, unprefixed);
      for (Prefix prefix : Prefix.values()) {
        if (unprefixed.takes(prefix)) {
          register(table, unprefixed.prefixedWith(prefix));
        }
      }
    }
    for (Currency currency : Currency.getAvailableCurrencies()) {
      register(table, currencyAtom(currency));
    }
    return Map.copyOf(table);
  }

  private static void register(Map<String, UnitAtom> table, UnitAtom atom) {
    UnitAtom prior = table.putIfAbsent(atom.symbol(), atom);
    if (prior != null) {
      throw new IllegalStateException("Duplicate unit symbol '" + atom.symbol() + "' in the unit catalogue");
    }
  }
}
