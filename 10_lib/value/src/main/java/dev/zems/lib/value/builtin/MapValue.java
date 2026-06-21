package dev.zems.lib.value.builtin;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable {@link Map} wrapper. The optional {@code valueDescriptor} pins the wire descriptor for the map values
 * (typically a {@code TypeDescriptor.oneOf(...)}) so a map with <b>heterogeneous</b> values marshals through the
 * inferred-write path; leave it {@code null} to infer a homogeneous value descriptor. The key descriptor is always
 * inferred from the runtime key class. Equality is by entries only — the descriptor is marshalling metadata.
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
    // Snapshot into a LinkedHashMap so iteration order is the source's order (Map.copyOf would discard it,
    // making the wire bytes non-deterministic and breaking FRAMED checksums / golden fixtures).
    entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
  }

  /** Inferred (homogeneous) map — no injected value descriptor. */
  public MapValue(Map<K, Value<V>> entries) {
    this(entries, null);
  }

  /**
   * Returns a map-typed descriptor: the key type is inferred from the first key via {@link TypeDescriptor#find(Class)};
   * the value type is the injected {@code valueDescriptor}, or — when none was injected — inferred from the values when
   * they are homogeneous. Returns {@code null} when the map is empty, the key has no descriptor, or (without an injected
   * descriptor) the values are heterogeneous, so the inferred write fails with a clear {@link IllegalStateException}
   * rather than mis-marshalling.
   */
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
          return null; // heterogeneous values — fail clean on the inferred-write path
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
  public boolean equals(Object o) {
    return o instanceof MapValue<?, ?> that && entries.equals(that.entries);
  }

  @Override
  public int hashCode() {
    return entries.hashCode();
  }
}
