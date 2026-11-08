package dev.zems.lib.value.json.engine;

import dev.zems.lib.value.json.engine.internal.JsonGenerator;
import dev.zems.lib.value.json.engine.internal.JsonMessages;
import dev.zems.lib.value.json.engine.internal.JsonParser;
import java.util.Objects;

/** Parses JSON text into {@link JsonValue} documents and prints documents as indented JSON. */
public final class Json {

  private Json() {}

  /**
   * Parses a strict RFC 8259 JSON text. Any value may be the root, member names must be unique within an object, and
   * nesting depth is bounded only by the heap.
   *
   * @throws NullPointerException if the text is null
   * @throws JsonParseException if the text is not exactly one JSON value; the message names the error, its line, column
   *     and path
   */
  public static JsonValue parse(String text) {
    Objects.requireNonNull(text, JsonMessages.TEXT_NULL);
    return JsonParser.parse(text.toCharArray());
  }

  /**
   * Parses a strict RFC 8259 JSON text, like {@link #parse(String)}; later changes to the array do not reach the
   * document.
   *
   * @throws NullPointerException if the text is null
   * @throws JsonParseException if the text is not exactly one JSON value
   */
  public static JsonValue parse(char[] text) {
    Objects.requireNonNull(text, JsonMessages.TEXT_NULL);
    return JsonParser.parse(text.clone());
  }

  /**
   * Prints the value as indented JSON: one member or element per line, each line prefixed by the indent once per
   * nesting level, {@code ": "} after a member name; empty objects and arrays stay {@code {}} and {@code []}, and there
   * is no trailing line break.
   *
   * @throws NullPointerException if the value or the indent is null
   * @throws IllegalArgumentException if the indent holds anything but spaces, tabs, line feeds and carriage returns
   */
  public static String toDisplayString(JsonValue value, String indent) {
    Objects.requireNonNull(value, JsonMessages.VALUE_NULL);
    Objects.requireNonNull(indent, JsonMessages.INDENT_NULL);
    if (!indent.chars().allMatch(JsonParser::isWhitespace)) {
      throw new IllegalArgumentException(JsonMessages.invalidIndent(indent));
    }
    return JsonGenerator.display(value, indent);
  }
}
