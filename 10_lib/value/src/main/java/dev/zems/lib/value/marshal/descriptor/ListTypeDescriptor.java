package dev.zems.lib.value.marshal.descriptor;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.builtin.ListValue;
import dev.zems.lib.value.marshal.StateReader;
import dev.zems.lib.value.marshal.StateWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Describes a {@code List} type with element type information. The descriptor's described type is
 * {@code List<Value<E>>} (state-augmented elements) — each element is itself a {@link Value} so per-element states
 * (NULL/UNDEFINED/UNRESOLVED/ERROR) are preserved through the round-trip.
 *
 * <p>
 * Read iterates {@link StateReader#read(int, String, TypeDescriptor)} per element (which peeks state markers and lifts
 * the typed payload via {@link Value#of(Object)}); write iterates
 * {@link StateWriter#write(int, String, Value, TypeDescriptor)} per element, unwrapping each payload via
 * {@link Value#unbox(Value)}.
 */
public final class ListTypeDescriptor<E> implements TypeDescriptor<List<Value<E>>> {

  private final String descriptorName;
  private final TypeDescriptor<E> elementType;
  private final Function<Integer, List<Value<E>>> resultListSupplier;

  private ListTypeDescriptor(
    String descriptorName,
    TypeDescriptor<E> elementType,
    Function<Integer, List<Value<E>>> resultListSupplier
  ) {
    this.descriptorName = descriptorName;
    this.elementType = elementType;
    this.resultListSupplier = resultListSupplier;
  }

  static <E> ListTypeDescriptor<E> of(
    String descriptorName,
    TypeDescriptor<E> elementType,
    Function<Integer, List<Value<E>>> resultListSupplier
  ) {
    Objects.requireNonNull(descriptorName, "descriptorName must not be null");
    Objects.requireNonNull(elementType, "elementType must not be null");
    Objects.requireNonNull(resultListSupplier, "resultListSupplier must not be null");
    if (descriptorName.isBlank()) {
      throw new IllegalArgumentException("descriptorName must not be blank");
    }
    return new ListTypeDescriptor<>(descriptorName, elementType, resultListSupplier);
  }

  static <E> ListTypeDescriptor<E> of(String descriptorName, TypeDescriptor<E> elementType) {
    return of(descriptorName, elementType, ArrayList::new);
  }

  /** Returns the TypeDescriptor for the list's element type. */
  public TypeDescriptor<E> elementType() {
    return elementType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(descriptorName, elementType);
  }

  @Override
  public boolean equals(Object o) {
    return (
      o instanceof ListTypeDescriptor<?> that &&
      descriptorName.equals(that.descriptorName) &&
      elementType.equals(that.elementType)
    );
  }

  @Override
  public String toString() {
    return ("ListTypeDescriptor[" + descriptorName + " (" + qualifiedName() + ")]");
  }

  @Override
  public String qualifiedName() {
    return "List<" + elementType.qualifiedName() + ">";
  }

  @Override
  public List<Value<E>> read(StateReader reader) {
    return Collections.unmodifiableList(CollectionCodecs.readElements(reader, elementType, resultListSupplier));
  }

  @Override
  public void write(StateWriter writer, List<Value<E>> value) {
    CollectionCodecs.writeElements(writer, elementType, value);
  }

  @Override
  public Value<List<Value<E>>> box(List<Value<E>> raw) {
    return new ListValue<>(raw, elementType); // stamp the element descriptor so the read-back self-describes
  }

  @Override
  public String signature() {
    return Signatures.forList(descriptorName, elementType.signature());
  }

  @Override
  public String descriptorName() {
    return descriptorName;
  }
}
