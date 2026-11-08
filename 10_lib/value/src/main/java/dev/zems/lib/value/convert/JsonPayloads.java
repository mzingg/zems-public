package dev.zems.lib.value.convert;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.json.engine.JsonArray;
import dev.zems.lib.value.json.engine.JsonBoolean;
import dev.zems.lib.value.json.engine.JsonNull;
import dev.zems.lib.value.json.engine.JsonNumber;
import dev.zems.lib.value.json.engine.JsonObject;
import dev.zems.lib.value.json.engine.JsonString;

final class JsonPayloads {

  private JsonPayloads() {}

  static Class<?> lookupKeyOf(Object payload) {
    return switch (payload) {
      case JsonObject _ -> JsonObject.class;
      case JsonArray _ -> JsonArray.class;
      case JsonString _ -> JsonString.class;
      case JsonNumber _ -> JsonNumber.class;
      case JsonBoolean _ -> JsonBoolean.class;
      case JsonNull _ -> JsonNull.class;
      default -> payload.getClass();
    };
  }

  static Value<?> scalarOrNull(Object payload) {
    return switch (payload) {
      case JsonString string -> Value.of(string.asString());
      case JsonBoolean bool -> Value.of(bool.asBoolean());
      default -> null;
    };
  }
}
