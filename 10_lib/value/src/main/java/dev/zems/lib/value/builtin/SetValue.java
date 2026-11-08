package dev.zems.lib.value.builtin;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable {@link Set} wrapper. A non-null {@code elementType} (typically a {@code TypeDescriptor.oneOf(...)}) pins the
 * element descriptor so a set with mixed element types can be written without an explicit descriptor; when it is
 * {@code null} the descriptor is inferred from the elements. Equality is by elements only.
 */
public record SetValue<E>(
  Set<Value<E>> elements,
  TypeDescriptor<E> elementType
) implements BuiltInValue<Set<Value<E>>> {
  public SetValue {
    Objects.requireNonNull(elements, "Set must not be null");
    for (var element : elements) {
      if (element == null) {
        throw new IllegalArgumentException("Set elements must not be null - use Value.nullValue() instead");
      }
    }
    elements = Set.copyOf(elements);
  }

  /** Without an element descriptor; {@link #valueType()} infers one from the elements. */
  public SetValue(Set<Value<E>> elements) {
    this(elements, null);
  }

  @Override
  public TypeDescriptor<Set<Value<E>>> valueType() {
    if (elementType != null) {
      return TypeDescriptor.ofSet("Set<" + elementType.qualifiedName() + ">", elementType);
    }
    if (elements.isEmpty()) {
      return null;
    }
    TypeDescriptor<E> first = null;
    for (Value<E> element : elements) {
      TypeDescriptor<E> next = element.valueType();
      if (next == null) {
        return null;
      }
      if (first == null) {
        first = next;
      } else if (!first.equals(next)) {
        return null;
      }
    }
    return TypeDescriptor.ofSet("Set<" + first.qualifiedName() + ">", first);
  }

  @Override
  public Set<Value<E>> unbox() {
    return elements;
  }

  /**
   * Unboxes every element, one level deep, into an unmodifiable set.
   *
   * @throws IllegalStateException when an element is a state marker
   */
  public Set<E> unboxElements() {
    return elements.stream().map(Value::unbox).collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof SetValue<?> that && elements.equals(that.elements);
  }

  @Override
  public int hashCode() {
    return elements.hashCode();
  }
}
