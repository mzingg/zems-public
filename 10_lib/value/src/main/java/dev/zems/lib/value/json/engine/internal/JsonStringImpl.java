package dev.zems.lib.value.json.engine.internal;

import dev.zems.lib.value.json.engine.JsonString;
import java.util.Objects;

/** The library's JSON string. */
public final class JsonStringImpl implements JsonString, JsonSourced {

  private final String text;
  private final char[] source;
  private final int offset;
  private final int end;

  JsonStringImpl(String text, char[] source, int offset, int end) {
    this.text = text;
    this.source = source;
    this.offset = offset;
    this.end = end;
  }

  public static JsonString of(String text) {
    Objects.requireNonNull(text, JsonMessages.TEXT_NULL);
    return new JsonStringImpl(text, null, -1, -1);
  }

  @Override
  public String asString() {
    return text;
  }

  @Override
  public String toString() {
    if (source != null) {
      return new String(source, offset, end - offset);
    }
    StringBuilder quoted = new StringBuilder(text.length() + 2);
    JsonGenerator.appendQuoted(quoted, text);
    return quoted.toString();
  }

  @Override
  public char[] source() {
    return source;
  }

  @Override
  public int offset() {
    return offset;
  }
}
