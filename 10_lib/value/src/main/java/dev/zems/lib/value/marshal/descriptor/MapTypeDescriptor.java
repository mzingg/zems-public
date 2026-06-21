package dev.zems.lib.value.marshal.descriptor;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.builtin.MapValue;
import dev.zems.lib.value.marshal.StateReader;
import dev.zems.lib.value.marshal.StateWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Describes a {@code Map} type with key and value type information. The descriptor's described type is
 * {@code Map<K, Value<V>>} (state-augmented values) — each value is itself a {@link Value} so per-entry state markers
 * (NULL/UNDEFINED/UNRESOLVED/ERROR) survive the round-trip. Keys are raw {@code K}s with no per-entry state.
 *
 * <p>
 * Read iterates {@code reader.read(valueType())} per entry value (peeks state markers + boxes via
 * {@code valueType().box(...)}); write iterates {@code writer.write(entryValue, valueType())} per entry value.
 */
public final class MapTypeDescriptor<K, V> implements TypeDescriptor<Map<K, Value<V>>> {

  private final String descriptorName;
  private final TypeDescriptor<K> keyType;
  private final TypeDescriptor<V> valueType;
  private final Function<Integer, Map<K, Value<V>>> resultMapSupplier;

  private MapTypeDescriptor(
    String descriptorName,
    TypeDescriptor<K> keyType,
    TypeDescriptor<V> valueType,
    Function<Integer, Map<K, Value<V>>> resultMapSupplier
  ) {
    this.descriptorName = descriptorName;
    this.keyType = keyType;
    this.valueType = valueType;
    this.resultMapSupplier = resultMapSupplier;
  }

  static <K, V> MapTypeDescriptor<K, V> of(
    String descriptorName,
    TypeDescriptor<K> keyType,
    TypeDescriptor<V> valueType,
    Function<Integer, Map<K, Value<V>>> resultMapSupplier
  ) {
    Objects.requireNonNull(descriptorName, "descriptorName must not be null");
    if (descriptorName.isBlank()) {
      throw new IllegalArgumentException("descriptorName must not be blank");
    }
    Objects.requireNonNull(keyType, "keyType must not be null");
    Objects.requireNonNull(valueType, "valueType must not be null");
    Objects.requireNonNull(resultMapSupplier, "resultMapSupplier must not be null");

    return new MapTypeDescriptor<>(descriptorName, keyType, valueType, resultMapSupplier);
  }

  static <K, V> MapTypeDescriptor<K, V> of(
    String descriptorName,
    TypeDescriptor<K> keyType,
    TypeDescriptor<V> valueType
  ) {
    return of(descriptorName, keyType, valueType, LinkedHashMap::new);
  }

  /** Returns the TypeDescriptor for the map's key type. */
  public TypeDescriptor<K> keyType() {
    return keyType;
  }

  /** Returns the TypeDescriptor for the map's value type. */
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
    if (!(o instanceof MapTypeDescriptor<?, ?> that)) {
      return false;
    }
    return (
      descriptorName.equals(that.descriptorName) && keyType.equals(that.keyType) && valueType.equals(that.valueType)
    );
  }

  @Override
  public String toString() {
    return ("MapTypeDescriptor[" + descriptorName + " (" + qualifiedName() + ")]");
  }

  @Override
  public String qualifiedName() {
    return ("Map<" + keyType.qualifiedName() + ", " + valueType.qualifiedName() + ">");
  }

  @Override
  public Map<K, Value<V>> read(StateReader reader) {
    return Collections.unmodifiableMap(CollectionCodecs.readEntries(reader, keyType, valueType, resultMapSupplier));
  }

  @Override
  public void write(StateWriter writer, Map<K, Value<V>> value) {
    CollectionCodecs.writeEntries(writer, keyType, valueType, value);
  }

  @Override
  public Value<Map<K, Value<V>>> box(Map<K, Value<V>> raw) {
    return new MapValue<>(raw, valueType); // stamp the value descriptor so the read-back self-describes
  }

  @Override
  public String signature() {
    return Signatures.forMap(descriptorName, keyType.signature(), valueType.signature());
  }

  @Override
  public String descriptorName() {
    return descriptorName;
  }
}
