package dev.zems.lib.value.json.engine.internal;

import dev.zems.lib.value.json.engine.JsonNull;

/** The library's JSON null. */
public final class JsonNullImpl implements JsonNull, JsonSourced {

  public static final JsonNullImpl NULL = new JsonNullImpl(null, -1);

  private final char[] source;
  private final int offset;

  JsonNullImpl(char[] source, int offset) {
    this.source = source;
    this.offset = offset;
  }

  @Override
  public String toString() {
    return "null";
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
