package dev.zems.lib.value;

import dev.zems.lib.value.json.JsonDocumentValue;
import dev.zems.lib.value.json.engine.Json;
import dev.zems.lib.value.json.engine.JsonArray;
import dev.zems.lib.value.json.engine.JsonBoolean;
import dev.zems.lib.value.json.engine.JsonNull;
import dev.zems.lib.value.json.engine.JsonNumber;
import dev.zems.lib.value.json.engine.JsonObject;
import dev.zems.lib.value.json.engine.JsonParseException;
import dev.zems.lib.value.json.engine.JsonString;
import dev.zems.lib.value.json.engine.JsonValue;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parses and builds JSON documents for {@code Value.parseJson} and {@code Value.jsonOf}; every failure is an error value. */
final class ValueJson {

  private static final String TEXT_NULL = "cannot parse the JSON: the text is null";
  private static final String MEMBERS_NULL = "cannot build a JSON object: the members map is null";
  private static final String MEMBER_NAME_NULL = "cannot build a JSON object: a member name is null";
  private static final String ELEMENTS_NULL = "cannot build a JSON array: the elements list is null";
  private static final String STRING_TEXT_NULL = "cannot build a JSON string: the text is null";
  private static final String NUMBER_NULL = "cannot build a JSON number: the number is null";
  private static final String BUILD_OBJECT = "cannot build a JSON object: member '";
  private static final String BUILD_ARRAY = "cannot build a JSON array: element ";
  private static final String IS_NULL = " is null; use Value.nullValue() for a JSON null";
  private static final String NOT_JSON = ", not a JSON value or the null marker";
  private static final String VOWELS = "AEIOU";

  private static final JsonDocumentValue TRUE = new JsonDocumentValue(JsonBoolean.of(true));
  private static final JsonDocumentValue FALSE = new JsonDocumentValue(JsonBoolean.of(false));

  private ValueJson() {}

  static Value<JsonValue> parse(String text) {
    if (text == null) {
      return failure(TEXT_NULL);
    }
    try {
      return wrap(Json.parse(text));
    } catch (JsonParseException e) {
      return typed(Value.errorOf(new IllegalStateException(e.getMessage(), e), Object.class));
    }
  }

  static Value<JsonValue> object(Map<String, ? extends Value<JsonValue>> members) {
    if (members == null) {
      return failure(MEMBERS_NULL);
    }
    Map<String, JsonValue> engineMembers = LinkedHashMap.newLinkedHashMap(members.size());
    for (Map.Entry<String, ? extends Value<JsonValue>> member : members.entrySet()) {
      String name = member.getKey();
      Value<JsonValue> value = member.getValue();
      if (name == null) {
        return failure(MEMBER_NAME_NULL);
      }
      if (value == null) {
        return failure(BUILD_OBJECT + name + "'" + IS_NULL);
      }
      JsonValue engine = engineOrNull(value);
      if (engine == null) {
        return refusal(BUILD_OBJECT + name + "' holds " + withArticle(value) + NOT_JSON, value);
      }
      engineMembers.put(name, engine);
    }
    return new JsonDocumentValue(JsonObject.of(engineMembers));
  }

  static Value<JsonValue> array(List<? extends Value<JsonValue>> elements) {
    if (elements == null) {
      return failure(ELEMENTS_NULL);
    }
    List<JsonValue> engineElements = new ArrayList<>(elements.size());
    int index = 0;
    for (Value<JsonValue> element : elements) {
      if (element == null) {
        return failure(BUILD_ARRAY + index + IS_NULL);
      }
      JsonValue engine = engineOrNull(element);
      if (engine == null) {
        return refusal(BUILD_ARRAY + index + " holds " + withArticle(element) + NOT_JSON, element);
      }
      engineElements.add(engine);
      index++;
    }
    return new JsonDocumentValue(JsonArray.of(engineElements));
  }

  static Value<JsonValue> string(String text) {
    if (text == null) {
      return failure(STRING_TEXT_NULL);
    }
    return new JsonDocumentValue(JsonString.of(text));
  }

  static Value<JsonValue> string(char character) {
    return new JsonDocumentValue(JsonString.of(String.valueOf(character)));
  }

  static Value<JsonValue> number(long number) {
    return new JsonDocumentValue(JsonNumber.of(number));
  }

  static Value<JsonValue> number(double number) {
    if (!Double.isFinite(number)) {
      return failure("cannot build a JSON number from " + number + ": JSON numbers must be finite");
    }
    return new JsonDocumentValue(JsonNumber.of(number));
  }

  static Value<JsonValue> number(BigInteger number) {
    if (number == null) {
      return failure(NUMBER_NULL);
    }
    return new JsonDocumentValue(JsonNumber.of(number.toString()));
  }

  static Value<JsonValue> number(BigDecimal number) {
    if (number == null) {
      return failure(NUMBER_NULL);
    }
    return new JsonDocumentValue(JsonNumber.of(number.toString()));
  }

  static Value<JsonValue> bool(boolean value) {
    return value ? TRUE : FALSE;
  }

  private static Value<JsonValue> wrap(JsonValue value) {
    return value instanceof JsonNull ? Value.nullValue() : new JsonDocumentValue(value);
  }

  private static JsonValue engineOrNull(Value<JsonValue> value) {
    return switch (value) {
      case JsonDocumentValue(JsonValue root) -> root;
      case NullValue<?> _ -> JsonNull.of();
      default -> null;
    };
  }

  private static String withArticle(Value<?> value) {
    String name = value.getClass().getSimpleName();
    boolean startsWithVowel = !name.isEmpty() && VOWELS.indexOf(name.charAt(0)) >= 0;
    return (startsWithVowel ? "an " : "a ") + name;
  }

  private static <T> Value<T> failure(String message) {
    return typed(Value.errorMessage(message, Object.class));
  }

  private static <T> Value<T> refusal(String message, Value<?> offending) {
    if (offending.isError()) {
      return typed(Value.errorOf(new IllegalStateException(message, offending.error().orElseThrow()), Object.class));
    }
    return failure(message);
  }

  @SuppressWarnings("unchecked")
  private static <T> Value<T> typed(Value<?> value) {
    return (Value<T>) value;
  }
}
