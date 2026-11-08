package dev.zems.lib.value.builtin;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.util.List;
import java.util.Objects;

/**
 * Immutable {@link List} wrapper. A non-null {@code elementType} (typically a {@code TypeDescriptor.oneOf(...)}) pins
 * the element descriptor so a list with mixed element types can be written without an explicit descriptor; when it is
 * {@code null} the descriptor is inferred from the elements. Equality is by elements only.
 */
public record ListValue<E>(
  List<Value<E>> elements,
  TypeDescriptor<E> elementType
) implements BuiltInValue<List<Value<E>>> {
  public ListValue {
    Objects.requireNonNull(elements, "List must not be null");
    for (var element : elements) {
      if (element == null) {
        throw new IllegalArgumentException("List elements must not be null - use Value.nullValue() instead");
      }
    }
    elements = List.copyOf(elements);
  }

  /** Without an element descriptor; {@link #valueType()} infers one from the elements. */
  public ListValue(List<Value<E>> elements) {
    this(elements, null);
  }

  @Override
  public TypeDescriptor<List<Value<E>>> valueType() {
    if (elementType != null) {
      return TypeDescriptor.ofList("List<" + elementType.qualifiedName() + ">", elementType);
    }
    if (elements.isEmpty()) {
      return null;
    }
    TypeDescriptor<E> first = elements.getFirst().valueType();
    if (first == null) {
      return null;
    }
    for (int i = 1; i < elements.size(); i++) {
      if (!first.equals(elements.get(i).valueType())) {
        return null;
      }
    }
    return TypeDescriptor.ofList("List<" + first.qualifiedName() + ">", first);
  }

  @Override
  public List<Value<E>> unbox() {
    return elements;
  }

  /**
   * Unboxes every element, one level deep, into an unmodifiable list.
   *
   * @throws IllegalStateException when an element is a state marker
   */
  public List<E> unboxElements() {
    return elements.stream().map(Value::unbox).toList();
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof ListValue<?> that && elements.equals(that.elements);
  }

  @Override
  public int hashCode() {
    return elements.hashCode();
  }
}
