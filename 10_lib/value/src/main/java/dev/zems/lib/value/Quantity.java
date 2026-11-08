package dev.zems.lib.value;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * An exact, unbounded magnitude with its {@link Unit}, the payload of a
 * {@link dev.zems.lib.value.builtin.MeasurableValue}; money is a quantity in a currency unit.
 *
 * <p>
 * Equality is numeric: {@code 5 m} equals {@code 5.0 m}, while each keeps its magnitude as written.
 * {@link #toString()} is the canonical form {@code <magnitude> <unit>} ({@code 9.99 EUR}, {@code 1E+3 m}) that
 * {@link #parse} reads back exactly, scale included.
 */
public final class Quantity {

  private static final char SEPARATOR = ' ';

  private final BigDecimal magnitude;
  private final Unit unit;

  private Quantity(BigDecimal magnitude, Unit unit) {
    this.magnitude = magnitude;
    this.unit = unit;
  }

  static Quantity of(BigDecimal magnitude, Unit unit) {
    return new Quantity(magnitude, unit);
  }

  /**
   * Parses the canonical form: a decimal magnitude, exactly one space and a unit expression.
   *
   * @throws IllegalArgumentException on null or blank text, a missing or extra space, a magnitude that is not a decimal
   *     number, an unknown unit symbol or currency code, or a malformed unit expression; the message names the text
   */
  public static Quantity parse(String text) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("Quantity text must not be null or empty");
    }
    int separator = text.indexOf(SEPARATOR);
    if (separator <= 0 || separator == text.length() - 1 || hasWhitespaceBesides(text, separator)) {
      throw new IllegalArgumentException(invalidPrefix(text) + "expected a magnitude, one space and a unit");
    }
    String magnitudeText = text.substring(0, separator);
    BigDecimal magnitude;
    try {
      magnitude = new BigDecimal(magnitudeText);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
        invalidPrefix(text) + "magnitude '" + magnitudeText + "' is not a decimal number",
        e
      );
    }
    try {
      return new Quantity(magnitude, UnitExpression.read(text.substring(separator + 1)));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(invalidPrefix(text) + e.getMessage(), e);
    }
  }

  /** The magnitude exactly as constructed or read, scale included. */
  public BigDecimal magnitude() {
    return magnitude;
  }

  public Unit unit() {
    return unit;
  }

  /** The canonical form, such as {@code 4.50 EUR/kg}. */
  @Override
  public String toString() {
    return magnitude.toString() + SEPARATOR + unit;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Quantity other && unit.equals(other.unit) && magnitude.compareTo(other.magnitude) == 0;
  }

  @Override
  public int hashCode() {
    int signum = magnitude.signum();
    // Not stripTrailingZeros(): it throws on overflow at scale Integer.MIN_VALUE. Equal non-zero values share
    // precision - scale; zero has several of those, so it hashes on its signum alone.
    long decade = signum == 0 ? 0 : magnitude.precision() - (long) magnitude.scale();
    return Objects.hash(unit, signum, decade);
  }

  private static boolean hasWhitespaceBesides(String text, int separator) {
    for (int i = 0; i < text.length(); i++) {
      if (i != separator && Character.isWhitespace(text.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  private static String invalidPrefix(String text) {
    return "Invalid quantity '" + text + "': ";
  }
}
