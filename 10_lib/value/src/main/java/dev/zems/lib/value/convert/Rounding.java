package dev.zems.lib.value.convert;

import java.math.RoundingMode;

/**
 * How a fractional value is turned into a whole one, for the fractional-to-integral conversions in {@link Conversions}.
 * The constants mirror the matching {@link RoundingMode}s; {@code RoundingMode.UNNECESSARY} has no counterpart because
 * refusing a fraction is the standard converter's default.
 */
public enum Rounding {
  /** Towards zero: {@code 3.7} becomes {@code 3}, {@code -3.7} becomes {@code -3}. */
  DOWN(RoundingMode.DOWN),

  /** Away from zero: {@code 3.2} becomes {@code 4}, {@code -3.2} becomes {@code -4}. */
  UP(RoundingMode.UP),

  /** Towards negative infinity: {@code 3.7} becomes {@code 3}, {@code -3.2} becomes {@code -4}. */
  FLOOR(RoundingMode.FLOOR),

  /** Towards positive infinity: {@code 3.2} becomes {@code 4}, {@code -3.7} becomes {@code -3}. */
  CEILING(RoundingMode.CEILING),

  /** To the nearest neighbour, ties away from zero: {@code 2.5} becomes {@code 3}. */
  HALF_UP(RoundingMode.HALF_UP),

  /** To the nearest neighbour, ties towards zero: {@code 2.5} becomes {@code 2}. */
  HALF_DOWN(RoundingMode.HALF_DOWN),

  /** To the nearest neighbour, ties to the even one: {@code 2.5} becomes {@code 2}, {@code 3.5} becomes {@code 4}. */
  HALF_EVEN(RoundingMode.HALF_EVEN);

  private final RoundingMode mode;

  Rounding(RoundingMode mode) {
    this.mode = mode;
  }

  RoundingMode mode() {
    return mode;
  }
}
