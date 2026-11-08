package dev.zems.lib.value;

/** One catalogue unit with its symbol, optionally prefixed; a {@link Unit} is a product of atoms. Equal by symbol. */
final class UnitAtom {

  /** Which prefixes an unprefixed atom takes. */
  enum PrefixRule {
    NONE,
    ALL_DECIMAL,
    DECIMAL_MULTIPLES_FROM_KILO,
    DECIMAL_MULTIPLES_FROM_KILO_AND_BINARY;

    boolean takes(Prefix prefix) {
      return switch (this) {
        case NONE -> false;
        case ALL_DECIMAL -> !prefix.isBinary();
        case DECIMAL_MULTIPLES_FROM_KILO -> prefix.isDecimalMultipleFromKilo();
        case DECIMAL_MULTIPLES_FROM_KILO_AND_BINARY -> prefix.isBinary() || prefix.isDecimalMultipleFromKilo();
      };
    }
  }

  private final String symbol;
  private final Dimension dimension;
  private final UnitSystem system;
  private final PrefixRule prefixRule;
  private final boolean prefixed;

  private UnitAtom(String symbol, Dimension dimension, UnitSystem system, PrefixRule prefixRule, boolean prefixed) {
    this.symbol = symbol;
    this.dimension = dimension;
    this.system = system;
    this.prefixRule = prefixRule;
    this.prefixed = prefixed;
  }

  static UnitAtom of(String symbol, Dimension dimension, UnitSystem system, PrefixRule prefixRule) {
    return new UnitAtom(symbol, dimension, system, prefixRule, false);
  }

  UnitAtom prefixedWith(Prefix prefix) {
    return new UnitAtom(prefix.symbol() + symbol, dimension, system, PrefixRule.NONE, true);
  }

  String symbol() {
    return symbol;
  }

  Dimension dimension() {
    return dimension;
  }

  UnitSystem system() {
    return system;
  }

  boolean isPrefixed() {
    return prefixed;
  }

  boolean takesAnyPrefix() {
    return prefixRule != PrefixRule.NONE;
  }

  boolean takes(Prefix prefix) {
    return prefixRule.takes(prefix);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof UnitAtom other && symbol.equals(other.symbol);
  }

  @Override
  public int hashCode() {
    return symbol.hashCode();
  }

  @Override
  public String toString() {
    return symbol;
  }
}
