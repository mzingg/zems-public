package dev.zems.lib.value.json.engine;

import dev.zems.lib.value.json.engine.internal.JsonArrayImpl;
import dev.zems.lib.value.json.engine.internal.JsonMessages;
import java.util.List;

/** A JSON array: an ordered list of values. */
public non-sealed interface JsonArray extends JsonValue {
  @Override
  List<JsonValue> asList();

  @Override
  default JsonValue get(int index) {
    List<JsonValue> elements = asList();
    if (index < 0 || index >= elements.size()) {
      throw new JsonValueException(JsonMessages.elementOutOfBounds(this, index, elements.size()));
    }
    return elements.get(index);
  }

  /**
   * Builds an array holding the list's elements; later changes to the list do not reach it.
   *
   * @throws NullPointerException if the list or one of its elements is null
   */
  static JsonArray of(List<? extends JsonValue> elements) {
    return JsonArrayImpl.of(elements);
  }
}
