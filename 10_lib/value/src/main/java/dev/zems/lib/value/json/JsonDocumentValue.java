package dev.zems.lib.value.json;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.json.engine.JsonNull;
import dev.zems.lib.value.json.engine.JsonValue;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.util.Objects;

/**
 * A JSON document: an object, an array, a string, a number or a boolean, held as its engine value. A JSON
 * {@code null} is {@link Value#nullValue()}, never a document; inside the root it is the engine's {@link JsonNull}.
 *
 * <p>Documents compare by value: numbers numerically, objects by their members in any order, arrays element by
 * element. Build them through {@code Value.parseJson} and {@code Value.jsonOf}; a foreign engine implementation that
 * throws makes {@code equals}, {@code hashCode}, {@code toString} and the navigator throw.
 *
 * @param root the engine value, never null and never a {@link JsonNull}
 */
public record JsonDocumentValue(JsonValue root) implements Value<JsonValue> {
  /**
   * Holds the engine value as a document.
   *
   * @throws NullPointerException if {@code root} is null
   * @throws IllegalArgumentException if {@code root} is a {@link JsonNull}
   */
  public JsonDocumentValue {
    Objects.requireNonNull(root, "root must not be null");
    if (root instanceof JsonNull) {
      throw new IllegalArgumentException("a JSON null is Value.nullValue(), not a JSON document value");
    }
  }

  @Override
  public JsonValue unbox() {
    return root;
  }

  @Override
  public TypeDescriptor<JsonValue> valueType() {
    return null;
  }

  /** Answers a JSON navigator rooted at this document; a failing step names its path from here as {@code $}. */
  @Override
  public JsonNavigator navigate() {
    return JsonNavigator.root(root);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof JsonDocumentValue that && JsonEquality.equal(root, that.root);
  }

  @Override
  public int hashCode() {
    return JsonEquality.hash(root);
  }

  /** Answers compact JSON: no whitespace, member order kept, a parsed string or number exactly as written. */
  @Override
  public String toString() {
    return root.toString();
  }
}
