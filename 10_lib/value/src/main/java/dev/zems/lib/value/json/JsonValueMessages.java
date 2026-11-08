package dev.zems.lib.value.json;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.json.engine.JsonArray;
import dev.zems.lib.value.json.engine.JsonBoolean;
import dev.zems.lib.value.json.engine.JsonNull;
import dev.zems.lib.value.json.engine.JsonNumber;
import dev.zems.lib.value.json.engine.JsonObject;
import dev.zems.lib.value.json.engine.JsonString;
import dev.zems.lib.value.json.engine.JsonValue;

/** Every message of the navigator and the emitter. */
final class JsonValueMessages {

  static final String NAME_NULL = "Member name must not be null";
  static final String DOCUMENT_NULL = "Document must not be null";
  static final String INDENT_NULL = "indent must not be null";

  private JsonValueMessages() {}

  static String notJson(Value<?> document) {
    return document.getClass().getSimpleName() + " is not a JSON value";
  }

  static String missingMember(String name) {
    return "member '" + name + "' is missing";
  }

  static String outOfRange(int index, int size) {
    String extent = switch (size) {
      case 0 -> "an empty array";
      case 1 -> "an array of 1 element";
      default -> "an array of " + size + " elements";
    };
    return "index " + index + " is out of range for " + extent;
  }

  static String memberOfKind(String name, JsonValue value) {
    return "cannot step to member '" + name + "' of " + kind(value);
  }

  static String elementOfKind(int index, JsonValue value) {
    return "cannot step to element " + index + " of " + kind(value);
  }

  static String navigationFailure(String path, String detail, String lineAndColumn) {
    String location = lineAndColumn.isEmpty() ? "" : " (" + lineAndColumn + ")";
    return path + ": " + detail + location;
  }

  private static String kind(JsonValue value) {
    return switch (value) {
      case JsonObject _ -> "an object";
      case JsonArray _ -> "an array";
      case JsonString _ -> "a string";
      case JsonNumber _ -> "a number";
      case JsonBoolean _ -> "a boolean";
      case JsonNull _ -> "null";
    };
  }
}
