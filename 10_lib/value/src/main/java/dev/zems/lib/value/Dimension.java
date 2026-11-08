package dev.zems.lib.value;

import java.util.Arrays;

/** The dimension of a unit: an integer exponent per {@link BaseDimension}; two units of one dimension are compatible. */
final class Dimension {

  static final Dimension NONE = new Dimension(new int[BaseDimension.values().length]);

  private final int[] exponents;

  private Dimension(int[] exponents) {
    this.exponents = exponents;
  }

  static Dimension of(BaseDimension base) {
    return NONE.with(base, 1);
  }

  Dimension with(BaseDimension base, int exponent) {
    int[] next = exponents.clone();
    next[base.ordinal()] = Math.addExact(next[base.ordinal()], exponent);
    return new Dimension(next);
  }

  int exponent(BaseDimension base) {
    return exponents[base.ordinal()];
  }

  Dimension multiply(Dimension other) {
    int[] next = exponents.clone();
    for (int i = 0; i < next.length; i++) {
      next[i] = Math.addExact(next[i], other.exponents[i]);
    }
    return new Dimension(next);
  }

  Dimension pow(int exponent) {
    int[] next = exponents.clone();
    for (int i = 0; i < next.length; i++) {
      next[i] = Math.multiplyExact(next[i], exponent);
    }
    return new Dimension(next);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Dimension other && Arrays.equals(exponents, other.exponents);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(exponents);
  }

  @Override
  public String toString() {
    var text = new StringBuilder();
    for (BaseDimension base : BaseDimension.values()) {
      if (exponent(base) != 0) {
        text
          .append(text.isEmpty() ? "" : "*")
          .append(base)
          .append('^')
          .append(exponent(base));
      }
    }
    return text.isEmpty() ? "1" : text.toString();
  }
}
