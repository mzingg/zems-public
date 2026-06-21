package dev.zems.lib.value.marshal.descriptor;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.builtin.SortedMapValue;
import dev.zems.lib.value.marshal.StateReader;
import dev.zems.lib.value.marshal.StateWriter;
import java.util.Collections;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Describes a {@link SortedMap} type with key and value type information. The descriptor's described type is
 * {@code SortedMap<K, Value<V>>} — keys are kept in natural ordering across the wire round-trip.
 *
 * <p>
 * Wire shape is identical to {@link MapTypeDescriptor}: {@code [size][entry]*} with each entry carrying a key and a
 * state-aware value. The reader puts entries into a {@link TreeMap} so natural-key order is restored on read regardless
 * of wire order.
 */
public final class SortedMapTypeDescriptor<K, V> implements TypeDescriptor<SortedMap<K, Value<V>>> {

  private final String descriptorName;
  private final TypeDescriptor<K> keyType;
  private final TypeDescriptor<V> valueType;

  private SortedMapTypeDescriptor(String descriptorName, TypeDescriptor<K> keyType, TypeDescriptor<V> valueType) {
    this.descriptorName = descriptorName;
    this.keyType = keyType;
    this.valueType = valueType;
  }

  static <K, V> SortedMapTypeDescriptor<K, V> of(
    String descriptorName,
    TypeDescriptor<K> keyType,
    TypeDescriptor<V> valueType
  ) {
    Objects.requireNonNull(descriptorName, "descriptorName must not be null");
    if (descriptorName.isBlank()) {
      throw new IllegalArgumentException("descriptorName must not be blank");
    }
    Objects.requireNonNull(keyType, "keyType must not be null");
    Objects.requireNonNull(valueType, "valueType must not be null");
    return new SortedMapTypeDescriptor<>(descriptorName, keyType, valueType);
  }

  /** Returns the TypeDescriptor for the sorted-map's key type. */
  public TypeDescriptor<K> keyType() {
    return keyType;
  }

  /** Returns the TypeDescriptor for the sorted-map's value type. */
  public TypeDescriptor<V> valueType() {
    return valueType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(descriptorName, keyType, valueType);
  }

  @Override
  @SuppressWarnings("PMD.SimplifyBooleanReturns")
  public boolean equals(Object o) {
    if (!(o instanceof SortedMapTypeDescriptor<?, ?> that)) {
      return false;
    }
    return (
      descriptorName.equals(that.descriptorName) && keyType.equals(that.keyType) && valueType.equals(that.valueType)
    );
  }

  @Override
  public String toString() {
    return ("SortedMapTypeDescriptor[" + descriptorName + " (" + qualifiedName() + ")]");
  }

  @Override
  public String qualifiedName() {
    return ("SortedMap<" + keyType.qualifiedName() + ", " + valueType.qualifiedName() + ">");
  }

  @SuppressWarnings("SortedCollectionWithNonComparableKeys")
  @Override
  public SortedMap<K, Value<V>> read(StateReader reader) {
    // Same [size][entry]* body as MapTypeDescriptor; a TreeMap accumulator restores natural-key order.
    TreeMap<K, Value<V>> result = CollectionCodecs.readEntries(reader, keyType, valueType, _ -> new TreeMap<>());
    return Collections.unmodifiableSortedMap(result);
  }

  @Override
  public void write(StateWriter writer, SortedMap<K, Value<V>> value) {
    CollectionCodecs.writeEntries(writer, keyType, valueType, value);
  }

  @Override
  public Value<SortedMap<K, Value<V>>> box(SortedMap<K, Value<V>> raw) {
    return new SortedMapValue<>(raw, valueType); // stamp the value descriptor so the read-back self-describes
  }

  @Override
  public String signature() {
    return Signatures.forSortedMap(descriptorName, keyType.signature(), valueType.signature());
  }

  @Override
  public String descriptorName() {
    return descriptorName;
  }
}
