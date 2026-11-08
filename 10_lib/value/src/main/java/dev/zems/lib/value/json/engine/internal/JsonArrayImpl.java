package dev.zems.lib.value.json.engine.internal;

import dev.zems.lib.value.json.engine.JsonArray;
import dev.zems.lib.value.json.engine.JsonValue;
import java.util.List;
import java.util.Objects;

/** The library's JSON array. */
public final class JsonArrayImpl implements JsonArray, JsonSourced {

  private final List<JsonValue> elements;
  private final char[] source;
  private final int offset;

  JsonArrayImpl(List<JsonValue> elements, char[] source, int offset) {
    this.elements = elements;
    this.source = source;
    this.offset = offset;
  }

  public static JsonArray of(List<? extends JsonValue> elements) {
    Objects.requireNonNull(elements, JsonMessages.ELEMENTS_NULL);
    int index = 0;
    for (JsonValue element : elements) {
      int position = index;
      Objects.requireNonNull(element, () -> JsonMessages.elementNull(position));
      index++;
    }
    return new JsonArrayImpl(List.copyOf(elements), null, -1);
  }

  @Override
  public List<JsonValue> asList() {
    return elements;
  }

  @Override
  public String toString() {
    return JsonGenerator.compact(this);
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
