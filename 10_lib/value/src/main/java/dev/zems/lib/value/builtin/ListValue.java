package dev.zems.lib.value.builtin;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.util.List;
import java.util.Objects;

/**
 * Immutable {@link List} wrapper. The optional {@code elementType} pins the wire descriptor for the elements: pass it
 * (typically a {@code TypeDescriptor.oneOf(...)}) to marshal a <b>heterogeneous</b> list through the inferred-write path
 * ({@code write(value)} without an explicit descriptor); leave it {@code null} to infer a homogeneous descriptor from
 * the elements. Equality is by elements only — the descriptor is marshalling metadata, not value identity.
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

  /** Inferred (homogeneous) list — no injected element descriptor. */
  public ListValue(List<Value<E>> elements) {
    this(elements, null);
  }

  /**
   * Returns a list-typed descriptor. If an element descriptor was injected, it is used directly. Otherwise the
   * descriptor is inferred from the elements when they are homogeneous (all share the same non-null {@code valueType()});
   * an empty or heterogeneous list returns {@code null}, so the inferred write ({@code write(value)}) fails with a clear
   * {@link IllegalStateException} pointing at the explicit {@code write(value, descriptor)} overload rather than
   * mis-marshalling.
   */
  @Override
  public TypeDescriptor<List<Value<E>>> valueType() {
    if (elementType != null) {
      return TypeDescriptor.ofList("List<" + elementType.qualifiedName() + ">", elementType);
    }
    if (elements.isEmpty()) {
      return null;
    }
    TypeDescriptor<E> first = elements.get(0).valueType();
    if (first == null) {
      return null;
    }
    for (int i = 1; i < elements.size(); i++) {
      if (!first.equals(elements.get(i).valueType())) {
        return null; // heterogeneous — fail clean on the inferred-write path
      }
    }
    return TypeDescriptor.ofList("List<" + first.qualifiedName() + ">", first);
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
