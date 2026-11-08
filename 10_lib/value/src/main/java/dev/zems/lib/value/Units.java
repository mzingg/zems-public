package dev.zems.lib.value;

import java.util.Currency;

/**
 * The catalogue units: the SI base units (with the gram, which carries the kilogram's prefixes), the 22 SI named derived
 * units, the non-SI units minute, hour, day, litre and tonne, bit and byte, and every currency of the running JDK.
 * Prefixed units come from {@link Unit#withPrefix}, compound units from {@link Unit#multiply}, {@link Unit#divide} and
 * {@link Unit#pow}.
 */
public final class Units {

  public static final Unit METRE = unit("m");
  public static final Unit KILOGRAM = unit("kg");
  public static final Unit SECOND = unit("s");
  public static final Unit AMPERE = unit("A");
  public static final Unit KELVIN = unit("K");
  public static final Unit MOLE = unit("mol");
  public static final Unit CANDELA = unit("cd");
  public static final Unit GRAM = unit("g");

  public static final Unit RADIAN = unit("rad");
  public static final Unit STERADIAN = unit("sr");
  public static final Unit HERTZ = unit("Hz");
  public static final Unit NEWTON = unit("N");
  public static final Unit PASCAL = unit("Pa");
  public static final Unit JOULE = unit("J");
  public static final Unit WATT = unit("W");
  public static final Unit COULOMB = unit("C");
  public static final Unit VOLT = unit("V");
  public static final Unit FARAD = unit("F");
  public static final Unit OHM = unit("Ω");
  public static final Unit SIEMENS = unit("S");
  public static final Unit WEBER = unit("Wb");
  public static final Unit TESLA = unit("T");
  public static final Unit HENRY = unit("H");
  public static final Unit DEGREE_CELSIUS = unit("°C");
  public static final Unit LUMEN = unit("lm");
  public static final Unit LUX = unit("lx");
  public static final Unit BECQUEREL = unit("Bq");
  public static final Unit GRAY = unit("Gy");
  public static final Unit SIEVERT = unit("Sv");
  public static final Unit KATAL = unit("kat");

  public static final Unit MINUTE = unit("min");
  public static final Unit HOUR = unit("h");
  public static final Unit DAY = unit("d");
  public static final Unit LITRE = unit("L");
  public static final Unit TONNE = unit("t");

  public static final Unit BIT = unit("bit");
  public static final Unit BYTE = unit("B");

  private Units() {}

  /**
   * The unit of {@code currency}, written as its ISO 4217 code.
   *
   * @throws IllegalArgumentException if {@code currency} is null
   */
  public static Unit currency(Currency currency) {
    if (currency == null) {
      throw new IllegalArgumentException("currency must not be null");
    }
    return Unit.of(UnitCatalogue.currency(currency));
  }

  private static Unit unit(String symbol) {
    return Unit.of(UnitCatalogue.atom(symbol));
  }
}
