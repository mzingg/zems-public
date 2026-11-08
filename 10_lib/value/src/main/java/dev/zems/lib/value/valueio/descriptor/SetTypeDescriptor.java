package dev.zems.lib.value.valueio.descriptor;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.builtin.SetValue;
import dev.zems.lib.value.valueio.StateReader;
import dev.zems.lib.value.valueio.StateWriter;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Describes a {@code Set} whose described type is {@code Set<Value<E>>}, so each element keeps its own state marker.
 * Read collects into a {@link LinkedHashSet}: wire order is kept and duplicates collapse.
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
    return "SetTypeDescriptor[" + descriptorName + " (" + qualifiedName() + ")]";
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
    return new SetValue<>(raw, elementType);
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
