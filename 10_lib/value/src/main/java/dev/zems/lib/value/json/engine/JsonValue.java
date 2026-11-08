package dev.zems.lib.value.json.engine;

import dev.zems.lib.value.json.engine.internal.JsonMessages;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A JSON value: an object, an array, a string, a number, a boolean or null.
 *
 * <p>Values are immutable and safe to share between threads without synchronisation. They have no value equality:
 * {@code equals} and {@code hashCode} are those of {@link Object}, so two documents are compared through
 * {@link #toString()} or their contents. Every conversion or access that does not fit the value throws
 * {@link JsonValueException}; for a parsed value the message names its path from the document root and its line and
 * column in the parsed text.
 */
public sealed interface JsonValue permits JsonString, JsonNumber, JsonObject, JsonArray, JsonBoolean, JsonNull {
  /** Answers the value as compact JSON, with no whitespace between tokens. */
  @Override
  String toString();

  /**
   * Reads the value as a boolean.
   *
   * @throws JsonValueException if the value is not a {@link JsonBoolean}
   */
  default boolean asBoolean() {
    throw new JsonValueException(JsonMessages.typeError(this, JsonMessages.BOOLEAN));
  }

  /**
   * Reads the value as an int.
   *
   * @throws JsonValueException if the value is not a {@link JsonNumber}, or not a whole number in the int range
   */
  default int asInt() {
    throw new JsonValueException(JsonMessages.typeError(this, JsonMessages.INT));
  }

  /**
   * Reads the value as a long.
   *
   * @throws JsonValueException if the value is not a {@link JsonNumber}, or not a whole number in the long range
   */
  default long asLong() {
    throw new JsonValueException(JsonMessages.typeError(this, JsonMessages.LONG));
  }

  /**
   * Reads the value as a double.
   *
   * @throws JsonValueException if the value is not a {@link JsonNumber}, or beyond the finite double range
   */
  default double asDouble() {
    throw new JsonValueException(JsonMessages.typeError(this, JsonMessages.DOUBLE));
  }

  /**
   * Reads the value as a string.
   *
   * @throws JsonValueException if the value is not a {@link JsonString}
   */
  default String asString() {
    throw new JsonValueException(JsonMessages.typeError(this, JsonMessages.STRING));
  }

  /**
   * Reads the value as an unmodifiable list of its elements.
   *
   * @throws JsonValueException if the value is not a {@link JsonArray}
   */
  default List<JsonValue> asList() {
    throw new JsonValueException(JsonMessages.typeError(this, JsonMessages.ARRAY));
  }

  /**
   * Reads the value as an unmodifiable map of its members, in member order.
   *
   * @throws JsonValueException if the value is not a {@link JsonObject}
   */
  default Map<String, JsonValue> asMap() {
    throw new JsonValueException(JsonMessages.typeError(this, JsonMessages.OBJECT));
  }

  /**
   * Answers the member with the given name.
   *
   * @throws JsonValueException if the value is not a {@link JsonObject}, or has no member of that name
   */
  default JsonValue get(String name) {
    Objects.requireNonNull(name, JsonMessages.NAME_NULL);
    throw new JsonValueException(JsonMessages.memberTypeError(this, name));
  }

  /**
   * Answers the element at the given index.
   *
   * @throws JsonValueException if the value is not a {@link JsonArray}, or the index is outside it
   */
  default JsonValue get(int index) {
    throw new JsonValueException(JsonMessages.elementTypeError(this, index));
  }

  /**
   * Answers the member with the given name, or an empty optional when the object has no such member.
   *
   * @throws JsonValueException if the value is not a {@link JsonObject}
   */
  default Optional<JsonValue> tryGet(String name) {
    Objects.requireNonNull(name, JsonMessages.NAME_NULL);
    throw new JsonValueException(JsonMessages.memberTypeError(this, name));
  }

  /** Answers this value, or an empty optional for a {@link JsonNull}. */
  default Optional<JsonValue> tryValue() {
    return this instanceof JsonNull ? Optional.empty() : Optional.of(this);
  }
}
