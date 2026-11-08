package dev.zems.lib.value.json.engine;

import dev.zems.lib.value.json.engine.internal.JsonNullImpl;

/** The JSON {@code null}. */
public non-sealed interface JsonNull extends JsonValue {
  static JsonNull of() {
    return JsonNullImpl.NULL;
  }
}
