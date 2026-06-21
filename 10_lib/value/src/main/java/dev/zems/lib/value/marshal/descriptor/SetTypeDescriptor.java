package dev.zems.lib.value.marshal.descriptor;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.builtin.SetValue;
import dev.zems.lib.value.marshal.StateReader;
import dev.zems.lib.value.marshal.StateWriter;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Describes a {@code Set} type with element type information. The descriptor's described type is {@code Set<Value<E>>}
 * (state-augmented elements) — each element is itself a {@link Value} so per-element states
 * (NULL/UNDEFINED/UNRESOLVED/ERROR) are preserved through the round-trip.
 *
 * <p>
 * Read iterates {@link StateReader#read(int, String, TypeDescriptor)} per element and collects into a
 * {@link LinkedHashSet} (preserves wire-encounter order; duplicates are silently deduplicated). Write iterates
 * {@link StateWriter#write(int, String, Value, TypeDescriptor)} per element using the underlying set's iteration
 * order.
 */
public final class SetTypeDescriptor<E> implements TypeDescriptor<Set<Value<E>>> {

  private final String descriptorName;
  private final TypeDescriptor<E> elementType;
  private final Function<Integer, Set<Value<E>>> resultSetSupplier;

  private SetTypeDescriptor(
    String descriptorName,
    TypeDescriptor<E> elementType,
    Function<Integer, Set<Value<E>>> resultSetSupplier
  ) {
    this.descriptorName = descriptorName;
    this.elementType = elementType;
    this.resultSetSupplier = resultSetSupplier;
  }

  static <E> SetTypeDescriptor<E> of(
    String descriptorName,
    TypeDescriptor<E> elementType,
    Function<Integer, Set<Value<E>>> resultSetSupplier
  ) {
    Objects.requireNonNull(descriptorName, "descriptorName must not be null");
    if (descriptorName.isBlank()) {
      throw new IllegalArgumentException("descriptorName must not be blank");
    }
    Objects.requireNonNull(elementType, "elementType must not be null");
    Objects.requireNonNull(resultSetSupplier, "resultSetSupplier must not be null");

    return new SetTypeDescriptor<>(descriptorName, elementType, resultSetSupplier);
  }

  static <E> SetTypeDescriptor<E> of(String descriptorName, TypeDescriptor<E> elementType) {
    return of(descriptorName, elementType, LinkedHashSet::new);
  }

  /** Returns the TypeDescriptor for the set's element type. */
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
      o instanceof SetTypeDescriptor<?> that &&
      descriptorName.equals(that.descriptorName) &&
      elementType.equals(that.elementType)
    );
  }

  @Override
  public String toString() {
    return ("SetTypeDescriptor[" + descriptorName + " (" + qualifiedName() + ")]");
  }

  @Override
  public String qualifiedName() {
    return "Set<" + elementType.qualifiedName() + ">";
  }

  @Override
  public Set<Value<E>> read(StateReader reader) {
    return Collections.unmodifiableSet(CollectionCodecs.readElements(reader, elementType, resultSetSupplier));
  }

  @Override
  public void write(StateWriter writer, Set<Value<E>> value) {
    CollectionCodecs.writeElements(writer, elementType, value);
  }

  @Override
  public Value<Set<Value<E>>> box(Set<Value<E>> raw) {
    return new SetValue<>(raw, elementType); // stamp the element descriptor so the read-back self-describes
  }

  @Override
  public String signature() {
    return Signatures.forSet(descriptorName, elementType.signature());
  }

  @Override
  public String descriptorName() {
    return descriptorName;
  }
}
