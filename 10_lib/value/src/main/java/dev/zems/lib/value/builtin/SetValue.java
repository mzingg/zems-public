package dev.zems.lib.value.builtin;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable {@link Set} wrapper. The optional {@code elementType} pins the wire descriptor for the elements (typically a
 * {@code TypeDescriptor.oneOf(...)}) so a <b>heterogeneous</b> set marshals through the inferred-write path; leave it
 * {@code null} to infer a homogeneous descriptor. Equality is by elements only — the descriptor is marshalling metadata.
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

  /** Inferred (homogeneous) set — no injected element descriptor. */
  public SetValue(Set<Value<E>> elements) {
    this(elements, null);
  }

  /**
   * Returns a set-typed descriptor. If an element descriptor was injected, it is used directly; otherwise it is inferred
   * from the elements when they are homogeneous. An empty or heterogeneous set returns {@code null} so the inferred
   * write fails with a clear {@link IllegalStateException} rather than mis-marshalling.
   */
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
        return null; // heterogeneous — fail clean on the inferred-write path
      }
    }
    return TypeDescriptor.ofSet("Set<" + first.qualifiedName() + ">", first);
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
