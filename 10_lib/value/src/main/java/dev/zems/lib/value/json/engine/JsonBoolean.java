package dev.zems.lib.value.json.engine;

import dev.zems.lib.value.json.engine.internal.JsonBooleanImpl;

/** A JSON boolean: {@code true} or {@code false}. */
public non-sealed interface JsonBoolean extends JsonValue {
  @Override
  boolean asBoolean();

  static JsonBoolean of(boolean value) {
    return value ? JsonBooleanImpl.TRUE : JsonBooleanImpl.FALSE;
  }
}
