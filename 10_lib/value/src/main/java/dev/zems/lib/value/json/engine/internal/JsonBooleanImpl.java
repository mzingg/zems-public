package dev.zems.lib.value.json.engine.internal;

import dev.zems.lib.value.json.engine.JsonBoolean;

/** The library's JSON boolean. */
public final class JsonBooleanImpl implements JsonBoolean, JsonSourced {

  public static final JsonBooleanImpl TRUE = new JsonBooleanImpl(true, null, -1);
  public static final JsonBooleanImpl FALSE = new JsonBooleanImpl(false, null, -1);

  private final boolean value;
  private final char[] source;
  private final int offset;

  JsonBooleanImpl(boolean value, char[] source, int offset) {
    this.value = value;
    this.source = source;
    this.offset = offset;
  }

  @Override
  public boolean asBoolean() {
    return value;
  }

  @Override
  public String toString() {
    return Boolean.toString(value);
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
