package dev.zems.lib.value.builtin;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Immutable {@link SortedMap} wrapper in natural key order, whatever comparator the source carried. Keys must be
 * {@link Comparable}; a key that is not throws {@link ClassCastException} at construction. A non-null
 * {@code valueDescriptor} (typically a {@code TypeDescriptor.oneOf(...)}) pins the value descriptor so a map with mixed
 * value types can be written without an explicit descriptor; when it is {@code null} the descriptor is inferred from
 * the values. Equality is by entries only.
 */
public record SortedMapValue<K, V>(
  SortedMap<K, Value<V>> entries,
  TypeDescriptor<V> valueDescriptor
) implements BuiltInValue<SortedMap<K, Value<V>>> {
  public SortedMapValue {
    Objects.requireNonNull(entries, "SortedMap must not be null");
    // The TreeMap copy constructor would inherit the source's comparator; per-entry puts into an empty TreeMap pin
    // natural order and throw ClassCastException here for a key that is not Comparable.
    SortedMap<K, Value<V>> natural = new TreeMap<>();
    for (var entry : entries.entrySet()) {
      if (entry.getKey() == null) {
        throw new IllegalArgumentException("SortedMap keys must not be null");
      }
      if (entry.getValue() == null) {
        throw new IllegalArgumentException("SortedMap values must not be null - use Value.nullValue() instead");
      }
      natural.put(entry.getKey(), entry.getValue());
    }
    entries = Collections.unmodifiableSortedMap(natural);
  }

  /** Without a value descriptor; {@link #valueType()} infers one from the values. */
  public SortedMapValue(SortedMap<K, Value<V>> entries) {
    this(entries, null);
  }

  @Override
  @SuppressWarnings("unchecked")
  public TypeDescriptor<SortedMap<K, Value<V>>> valueType() {
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
    return TypeDescriptor.ofSortedMap(
      "SortedMap<" + keyType.qualifiedName() + ", " + resolvedValueType.qualifiedName() + ">",
      keyType,
      resolvedValueType
    );
  }

  @Override
  public SortedMap<K, Value<V>> unbox() {
    return entries;
  }

  /**
   * Unboxes every entry value, one level deep, into an unmodifiable sorted map in natural key order; keys are returned
   * as stored.
   *
   * @throws IllegalStateException when an entry value is a state marker
   */
  public SortedMap<K, V> unboxEntries() {
    var unboxed = new TreeMap<K, V>();
    entries.forEach((key, value) -> unboxed.put(key, value.unbox()));
    return Collections.unmodifiableSortedMap(unboxed);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof SortedMapValue<?, ?> that && entries.equals(that.entries);
  }

  @Override
  public int hashCode() {
    return entries.hashCode();
  }
}
