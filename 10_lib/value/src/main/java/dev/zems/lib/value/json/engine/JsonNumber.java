package dev.zems.lib.value.json.engine;

import dev.zems.lib.value.json.engine.internal.JsonNumberImpl;

/**
 * A JSON number, kept as its text so that no digit is lost; {@code new BigDecimal(number.toString())} gives the exact
 * value.
 */
public non-sealed interface JsonNumber extends JsonValue {
  /**
   * Reads the number as an int; {@code 123.0} and {@code 1.23e2} convert, {@code 345.6} does not.
   *
   * @throws JsonValueException if the number is not whole, or outside the int range
   */
  @Override
  int asInt();

  /**
   * Reads the number as a long; {@code 123.0} and {@code 1.23e2} convert, {@code 345.6} does not.
   *
   * @throws JsonValueException if the number is not whole, or outside the long range
   */
  @Override
  long asLong();

  /**
   * Reads the number as the nearest double, rounding digits a double cannot hold.
   *
   * @throws JsonValueException if the number is beyond the finite double range
   */
  @Override
  double asDouble();

  /**
   * Answers the number's text: the source text exactly as written for a parsed number, otherwise the text the factory
   * produced.
   */
  @Override
  String toString();

  /** Builds a number whose text is {@link Integer#toString(int)} of the value. */
  static JsonNumber of(int number) {
    return JsonNumberImpl.of(number);
  }

  /** Builds a number whose text is {@link Long#toString(long)} of the value. */
  static JsonNumber of(long number) {
    return JsonNumberImpl.of(number);
  }

  /**
   * Builds a number whose text is {@link Double#toString(double)} of the value.
   *
   * @throws IllegalArgumentException if the value is NaN or infinite
   */
  static JsonNumber of(double number) {
    return JsonNumberImpl.of(number);
  }

  /**
   * Builds a number from JSON number text, without the JSON whitespace around it.
   *
   * @throws NullPointerException if the text is null
   * @throws IllegalArgumentException if the text is not exactly one JSON number
   */
  static JsonNumber of(String text) {
    return JsonNumberImpl.of(text);
  }
}
