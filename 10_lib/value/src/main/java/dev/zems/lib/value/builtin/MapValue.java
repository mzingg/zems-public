package dev.zems.lib.value.builtin;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable {@link Map} wrapper that keeps the source's iteration order. A non-null {@code valueDescriptor} (typically
 * a {@code TypeDescriptor.oneOf(...)}) pins the value descriptor so a map with mixed value types can be written without
 * an explicit descriptor; when it is {@code null} the descriptor is inferred from the values. The key descriptor is
 * always inferred from the key class. Equality is by entries only.
 */
public record MapValue<K, V>(
  Map<K, Value<V>> entries,
  TypeDescriptor<V> valueDescriptor
) implements BuiltInValue<Map<K, Value<V>>> {
  public MapValue {
    Objects.requireNonNull(entries, "Map must not be null");
    for (var entry : entries.entrySet()) {
      if (entry.getKey() == null) {
        throw new IllegalArgumentException("Map keys must not be null");
      }
      if (entry.getValue() == null) {
        throw new IllegalArgumentException("Map values must not be null - use Value.nullValue() instead");
      }
    }
    // Map.copyOf would drop the source's iteration order and make the wire bytes non-deterministic, which breaks
    // FRAMED checksums and golden fixtures.
    entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
  }

  /** Without a value descriptor; {@link #valueType()} infers one from the values. */
  public MapValue(Map<K, Value<V>> entries) {
    this(entries, null);
  }

  @Override
  @SuppressWarnings("unchecked")
  public TypeDescriptor<Map<K, Value<V>>> valueType() {
    if (entries.isEmpty()) {
      return null;
    }
    Map.Entry<K, Value<V>> first = entries.entrySet().iterator().next();
    TypeDescriptor<K> keyType = (TypeDescriptor<K>) TypeDescriptor.find(first.getKey().getClass()).orElse(null);
    if (keyType == null) {
      return null;
    }
    TypeDescriptor<V> resolvedValueType = valueDescriptor;
    if (resolvedValueType == null) {
      resolvedValueType = first.getValue().valueType();
      if (resolvedValueType == null) {
        return null;
      }
      for (var entry : entries.entrySet()) {
        if (!resolvedValueType.equals(entry.getValue().valueType())) {
          return null;
        }
      }
    }
    return TypeDescriptor.ofMap(
      "Map<" + keyType.qualifiedName() + ", " + resolvedValueType.qualifiedName() + ">",
      keyType,
      resolvedValueType
    );
  }

  @Override
  public Map<K, Value<V>> unbox() {
    return entries;
  }

  /**
   * Unboxes every entry value, one level deep, into an unmodifiable map in insertion order; keys are returned as
   * stored.
   *
   * @throws IllegalStateException when an entry value is a state marker
   */
  public Map<K, V> unboxEntries() {
    var unboxed = new LinkedHashMap<K, V>();
    entries.forEach((key, value) -> unboxed.put(key, value.unbox()));
    return Collections.unmodifiableMap(unboxed);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof MapValue<?, ?> that && entries.equals(that.entries);
  }

  @Override
  public int hashCode() {
    return entries.hashCode();
  }
}
