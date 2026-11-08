package dev.zems.lib.value.json.engine;

import dev.zems.lib.value.json.engine.internal.JsonStringImpl;

/** A JSON string. */
public non-sealed interface JsonString extends JsonValue {
  @Override
  String asString();

  /**
   * Answers the string as JSON, quotes included: the source text exactly as written for a parsed string, otherwise the
   * text with {@code "}, {@code \} and the control characters escaped.
   */
  @Override
  String toString();

  /**
   * Builds a string holding the given text unchanged.
   *
   * @throws NullPointerException if the text is null
   */
  static JsonString of(String text) {
    return JsonStringImpl.of(text);
  }
}
