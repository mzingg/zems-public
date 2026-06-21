package dev.zems.lib.value.builtin;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Immutable {@link SortedMap} wrapper with natural-key ordering. Distinct from {@link MapValue} (which preserves
 * insertion order via {@link java.util.LinkedHashMap}) — this value type pins natural ordering at construction so
 * iteration, first-key/last-key, and subMap views behave deterministically.
 *
 * <p>
 * Keys must implement {@link Comparable}; the compact constructor copies entries into a {@link TreeMap} with the
 * natural-ordering comparator, which surfaces a {@link ClassCastException} at construction time if any key is
 * non-comparable. The optional {@code valueDescriptor} pins the wire descriptor for the values (typically a
 * {@code TypeDescriptor.oneOf(...)}) so a sorted map with <b>heterogeneous</b> values marshals through the
 * inferred-write path; leave it {@code null} to infer a homogeneous value descriptor. Equality is by entries only.
 */
public record SortedMapValue<K, V>(
  SortedMap<K, Value<V>> entries,
  TypeDescriptor<V> valueDescriptor
) implements BuiltInValue<SortedMap<K, Value<V>>> {
  public SortedMapValue {
    Objects.requireNonNull(entries, "SortedMap must not be null");
    // Force natural-key ordering regardless of any comparator the source carries — a TreeMap copy constructor
    // would inherit the source's comparator. Per-entry puts into a natural-ordering TreeMap also surface a
    // ClassCastException at construction when a key is not Comparable, as the class doc promises.
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

  /** Inferred (homogeneous) sorted map — no injected value descriptor. */
  public SortedMapValue(SortedMap<K, Value<V>> entries) {
    this(entries, null);
  }

  /**
   * Returns a sorted-map-typed descriptor: the key type is inferred from the first key; the value type is the injected
   * {@code valueDescriptor}, or — when none was injected — inferred from the values when they are homogeneous. Returns
   * {@code null} when the map is empty, the key has no descriptor, or (without an injected descriptor) the values are
   * heterogeneous, so the inferred write fails with a clear {@link IllegalStateException} rather than mis-marshalling.
   */
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
          return null; // heterogeneous values — fail clean on the inferred-write path
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
  public boolean equals(Object o) {
    return o instanceof SortedMapValue<?, ?> that && entries.equals(that.entries);
  }

  @Override
  public int hashCode() {
    return entries.hashCode();
  }
}
