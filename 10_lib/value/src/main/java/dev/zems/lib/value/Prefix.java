package dev.zems.lib.value;

/**
 * The 24 SI prefixes (SI Brochure, 9th edition, 2022 revision) and the 10 binary prefixes (IEC 80000-13:2025). Which
 * unit takes which prefix is the catalogue's rule; {@link Unit#withPrefix} refuses a prefix the unit does not take.
 */
public enum Prefix {
  QUECTO("q", 10, -30),
  RONTO("r", 10, -27),
  YOCTO("y", 10, -24),
  ZEPTO("z", 10, -21),
  ATTO("a", 10, -18),
  FEMTO("f", 10, -15),
  PICO("p", 10, -12),
  NANO("n", 10, -9),
  MICRO("μ", 10, -6),
  MILLI("m", 10, -3),
  CENTI("c", 10, -2),
  DECI("d", 10, -1),
  DECA("da", 10, 1),
  HECTO("h", 10, 2),
  KILO("k", 10, 3),
  MEGA("M", 10, 6),
  GIGA("G", 10, 9),
  TERA("T", 10, 12),
  PETA("P", 10, 15),
  EXA("E", 10, 18),
  ZETTA("Z", 10, 21),
  YOTTA("Y", 10, 24),
  RONNA("R", 10, 27),
  QUETTA("Q", 10, 30),
  KIBI("Ki", 1024, 1),
  MEBI("Mi", 1024, 2),
  GIBI("Gi", 1024, 3),
  TEBI("Ti", 1024, 4),
  PEBI("Pi", 1024, 5),
  EXBI("Ei", 1024, 6),
  ZEBI("Zi", 1024, 7),
  YOBI("Yi", 1024, 8),
  ROBI("Ri", 1024, 9),
  QUEBI("Qi", 1024, 10);

  private static final int DECIMAL = 10;
  private static final int KILO_EXPONENT = 3;

  private final String symbol;
  private final int radix;
  private final int exponent;

  Prefix(String symbol, int radix, int exponent) {
    this.symbol = symbol;
    this.radix = radix;
    this.exponent = exponent;
  }

  /** The prefix symbol as written in a unit expression; micro is the Greek letter mu (U+03BC). */
  public String symbol() {
    return symbol;
  }

  boolean isBinary() {
    return radix != DECIMAL;
  }

  boolean isDecimalMultipleFromKilo() {
    return radix == DECIMAL && exponent >= KILO_EXPONENT;
  }
}
