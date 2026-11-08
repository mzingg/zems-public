package dev.zems.lib.value.json;

import dev.zems.lib.value.NullValue;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.json.engine.JsonNull;
import dev.zems.lib.value.json.engine.JsonValue;

/** Crosses between engine values and JSON document values; an engine null is the null marker. */
final class JsonWrapping {

  private JsonWrapping() {}

  static Value<JsonValue> wrap(JsonValue value) {
    return value instanceof JsonNull ? Value.nullValue() : new JsonDocumentValue(value);
  }

  static JsonValue engineOrNull(Value<?> value) {
    return switch (value) {
      case JsonDocumentValue(JsonValue root) -> root;
      case NullValue<?> _ -> JsonNull.of();
      default -> null;
    };
  }
}
