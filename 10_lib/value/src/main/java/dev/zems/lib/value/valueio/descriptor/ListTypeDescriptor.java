package dev.zems.lib.value.valueio.descriptor;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.builtin.ListValue;
import dev.zems.lib.value.valueio.StateReader;
import dev.zems.lib.value.valueio.StateWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Describes a {@code List} whose described type is {@code List<Value<E>>}, so each element keeps its own state marker
 * through the round-trip.
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
    return "ListTypeDescriptor[" + descriptorName + " (" + qualifiedName() + ")]";
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
    return new ListValue<>(raw, elementType);
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
