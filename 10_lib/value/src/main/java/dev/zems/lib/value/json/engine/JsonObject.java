package dev.zems.lib.value.json.engine;

import dev.zems.lib.value.json.engine.internal.JsonMessages;
import dev.zems.lib.value.json.engine.internal.JsonObjectImpl;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A JSON object: named members, each name unique within the object.
 *
 * <p>Members keep their order: document order for a parsed object, the source map's iteration order for a built one.
 * {@link #asMap()}, {@link #toString()} and {@link Json#toDisplayString(JsonValue, String)} all follow it.
 */
public non-sealed interface JsonObject extends JsonValue {
  @Override
  Map<String, JsonValue> asMap();

  @Override
  default JsonValue get(String name) {
    Objects.requireNonNull(name, JsonMessages.NAME_NULL);
    JsonValue member = asMap().get(name);
    if (member == null) {
      throw new JsonValueException(JsonMessages.missingMember(this, name));
    }
    return member;
  }

  @Override
  default Optional<JsonValue> tryGet(String name) {
    Objects.requireNonNull(name, JsonMessages.NAME_NULL);
    return Optional.ofNullable(asMap().get(name));
  }

  /**
   * Builds an object holding the map's members in the map's iteration order; later changes to the map do not reach it.
   *
   * @throws NullPointerException if the map, a member name or a member value is null
   * @throws IllegalArgumentException if the map yields the same member name twice
   */
  static JsonObject of(Map<String, ? extends JsonValue> members) {
    return JsonObjectImpl.of(members);
  }
}
