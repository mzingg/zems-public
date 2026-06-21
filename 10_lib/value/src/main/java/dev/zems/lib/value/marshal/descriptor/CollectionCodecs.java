package dev.zems.lib.value.marshal.descriptor;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.StateReader;
import dev.zems.lib.value.marshal.StateWriter;
import dev.zems.lib.value.marshal.wire.WireConstraintEnforcer;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Shared wire codecs for the collection descriptors. Two byte-shapes, each reused by two descriptors:
 *
 * <ul>
 * <li>{@code [size][element]*} — {@link ListTypeDescriptor} and {@link SetTypeDescriptor}
 * <li>{@code [size][entry]*} — {@link MapTypeDescriptor} and {@link SortedMapTypeDescriptor}
 * </ul>
 *
 * <p>
 * Each {@code read*} accumulates into the collection produced by the caller's {@code resultSupplier} (e.g. a
 * {@code TreeMap} to restore natural-key order, or a {@code LinkedHashSet} to preserve wire-encounter order) and returns
 * the raw accumulator — callers wrap it unmodifiable with the wrapper matching their described type.
 */
final class CollectionCodecs {

  private static final String SIZE = "size";

  private CollectionCodecs() {}

  static <E, C extends Collection<Value<E>>> C readElements(
    StateReader reader,
    TypeDescriptor<E> elementType,
    Function<Integer, C> resultSupplier
  ) {
    Objects.requireNonNull(reader, "reader must not be null");
    Objects.requireNonNull(elementType, "elementType must not be null");
    Objects.requireNonNull(resultSupplier, "resultSupplier must not be null");
    int size = reader.readInt(0, SIZE);
    WireConstraintEnforcer.checkArrayLength(size, reader.wireConstraints());
    C result = resultSupplier.apply(size);
    for (int i = 0; i < size; i++) {
      result.add(reader.read(i + 1, String.valueOf(i), elementType));
    }
    return result;
  }

  static <E> void writeElements(StateWriter writer, TypeDescriptor<E> elementType, Collection<Value<E>> value) {
    Objects.requireNonNull(writer, "writer must not be null");
    Objects.requireNonNull(elementType, "elementType must not be null");
    Objects.requireNonNull(value, "collection must not be null");
    WireConstraintEnforcer.checkArrayLength(value.size(), writer.wireConstraints());
    writer.writeInt(0, SIZE, value.size());
    int index = 0;
    for (Value<E> element : value) {
      writer.write(index + 1, String.valueOf(index), element, elementType);
      index++;
    }
  }

  static <K, V, M extends Map<K, Value<V>>> M readEntries(
    StateReader reader,
    TypeDescriptor<K> keyType,
    TypeDescriptor<V> valueType,
    Function<Integer, M> resultSupplier
  ) {
    Objects.requireNonNull(reader, "reader must not be null");
    Objects.requireNonNull(keyType, "keyType must not be null");
    Objects.requireNonNull(valueType, "valueType must not be null");
    Objects.requireNonNull(resultSupplier, "resultSupplier must not be null");
    int size = reader.readInt(0, SIZE);
    WireConstraintEnforcer.checkMapEntries(size, reader.wireConstraints());
    M result = resultSupplier.apply(size);
    for (int i = 0; i < size; i++) {
      reader.beginNested(i + 1, String.valueOf(i));
      K key = reader.readRecord(0, "key", keyType);
      Value<V> value = reader.read(1, "value", valueType);
      reader.endNested(i + 1, String.valueOf(i));
      result.put(key, value);
    }
    return result;
  }

  static <K, V> void writeEntries(
    StateWriter writer,
    TypeDescriptor<K> keyType,
    TypeDescriptor<V> valueType,
    Map<K, Value<V>> value
  ) {
    Objects.requireNonNull(writer, "writer must not be null");
    Objects.requireNonNull(keyType, "keyType must not be null");
    Objects.requireNonNull(valueType, "valueType must not be null");
    Objects.requireNonNull(value, "map must not be null");
    WireConstraintEnforcer.checkMapEntries(value.size(), writer.wireConstraints());
    writer.writeInt(0, SIZE, value.size());
    int index = 0;
    for (var entry : value.entrySet()) {
      writer.beginNested(index + 1, String.valueOf(index));
      writer.writeRecord(0, "key", keyType, entry.getKey());
      writer.write(1, "value", entry.getValue(), valueType);
      writer.endNested(index + 1, String.valueOf(index));
      index++;
    }
  }
}
