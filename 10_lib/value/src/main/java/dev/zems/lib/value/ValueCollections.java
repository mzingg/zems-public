package dev.zems.lib.value;

import dev.zems.lib.value.builtin.ListValue;
import dev.zems.lib.value.builtin.MapValue;
import dev.zems.lib.value.builtin.SetValue;
import dev.zems.lib.value.builtin.SortedMapValue;
import dev.zems.lib.value.cache.ValueCache;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Package-private factory implementations for {@link ListValue}, {@link SetValue}, and {@link MapValue}: varargs,
 * pre-wrapped, and map-and-wrap variants of {@code listOf}, {@code setOf}, and {@code mapOf} plus the empty-collection
 * singletons. Reachable through {@code Value.*} delegates only.
 */
final class ValueCollections {

  private static final TypeDescriptor<Object> ANY = TypeDescriptor.of(Object.class.getName(), Object.class);
  private static final String ELEMENTS_NOT_NULL = "elements must not be null";
  private static final String MAP_KEYS_NOT_NULL = "Map keys must not be null";
  private static final String SORTED_MAP_KEYS_NOT_NULL = "SortedMap keys must not be null";

  private ValueCollections() {}

  @SafeVarargs
  static <E> Value<List<Value<E>>> listOf(E... rawElements) {
    if (rawElements == null) {
      return Value.errorMessage(ELEMENTS_NOT_NULL, listErrorType());
    }
    var copy = new ArrayList<Value<E>>(rawElements.length);
    for (var e : rawElements) {
      copy.add(Value.of(e));
    }
    return new ListValue<>(List.copyOf(copy));
  }

  static <E> Value<List<Value<E>>> listOf(List<Value<E>> wrappedElements) {
    if (wrappedElements == null) {
      return Value.errorMessage("List must not be null", listErrorType());
    }
    for (var e : wrappedElements) {
      if (e == null) {
        return Value.errorMessage("List elements must not be null - use Value.nullValue() instead", listErrorType());
      }
    }
    return new ListValue<>(Collections.unmodifiableList(wrappedElements));
  }

  static <T, E> Value<List<Value<E>>> listOf(Iterable<T> source, Function<T, E> rawMapper) {
    if (source == null) {
      return Value.errorMessage("source must not be null", listErrorType());
    }
    if (rawMapper == null) {
      return Value.errorMessage("mapper must not be null", listErrorType());
    }
    var copy = new ArrayList<Value<E>>();
    for (var item : source) {
      copy.add(Value.of(rawMapper.apply(item)));
    }
    return new ListValue<>(Collections.unmodifiableList(copy));
  }

  static <E> Value<List<Value<E>>> emptyList() {
    return ValueCache.INSTANCE.emptyList();
  }

  @SafeVarargs
  static <E> Value<Set<Value<E>>> setOf(E... rawElements) {
    if (rawElements == null) {
      return Value.errorMessage(ELEMENTS_NOT_NULL, setErrorType());
    }
    var copy = new LinkedHashSet<Value<E>>(rawElements.length);
    for (var e : rawElements) {
      copy.add(Value.of(e));
    }
    return new SetValue<>(Collections.unmodifiableSet(copy));
  }

  static <E> Value<Set<Value<E>>> setOf(Set<Value<E>> wrappedElements) {
    if (wrappedElements == null) {
      return Value.errorMessage("Set must not be null", setErrorType());
    }
    for (var e : wrappedElements) {
      if (e == null) {
        return Value.errorMessage("Set elements must not be null - use Value.nullValue() instead", setErrorType());
      }
    }
    return new SetValue<>(Collections.unmodifiableSet(wrappedElements));
  }

  static <T, E> Value<Set<Value<E>>> setOf(Iterable<T> source, Function<T, E> rawMapper) {
    if (source == null) {
      return Value.errorMessage("source must not be null", setErrorType());
    }
    if (rawMapper == null) {
      return Value.errorMessage("mapper must not be null", setErrorType());
    }
    var copy = new LinkedHashSet<Value<E>>();
    for (var item : source) {
      copy.add(Value.of(rawMapper.apply(item)));
    }
    return new SetValue<>(Collections.unmodifiableSet(copy));
  }

  static <E> Value<Set<Value<E>>> emptySet() {
    return ValueCache.INSTANCE.emptySet();
  }

  @SafeVarargs
  static <K, V> Value<Map<K, Value<V>>> mapOf(Map.Entry<K, V>... rawEntries) {
    if (rawEntries == null) {
      return Value.errorMessage("entries must not be null", mapErrorType());
    }
    var copy = new LinkedHashMap<K, Value<V>>();
    for (var entry : rawEntries) {
      if (entry == null) {
        return Value.errorMessage("entries must not contain nulls", mapErrorType());
      }
      if (entry.getKey() == null) {
        return Value.errorMessage(MAP_KEYS_NOT_NULL, mapErrorType());
      }
      copy.put(entry.getKey(), Value.of(entry.getValue()));
    }
    return new MapValue<>(Collections.unmodifiableMap(copy));
  }

  static <K, V> Value<Map<K, Value<V>>> mapOf(Map<K, Value<V>> wrappedEntries) {
    if (wrappedEntries == null) {
      return Value.errorMessage("Map must not be null", mapErrorType());
    }
    for (var entry : wrappedEntries.entrySet()) {
      // there are map implementations that support null keys
      if (entry.getKey() == null) {
        return Value.errorMessage(MAP_KEYS_NOT_NULL, mapErrorType());
      }
      if (entry.getValue() == null) {
        return Value.errorMessage("Map values must not be null - use Value.nullValue() instead", mapErrorType());
      }
    }
    return new MapValue<>(Collections.unmodifiableMap(wrappedEntries));
  }

  static <T, K, V> Value<Map<K, Value<V>>> mapOf(Iterable<T> source, Function<T, K> keyFn, Function<T, V> rawValueFn) {
    if (source == null || keyFn == null || rawValueFn == null) {
      return Value.errorMessage("source / keyFn / rawValueFn must not be null", mapErrorType());
    }
    var copy = new LinkedHashMap<K, Value<V>>();
    for (var item : source) {
      K key = keyFn.apply(item);
      if (key == null) {
        return Value.errorMessage(MAP_KEYS_NOT_NULL, mapErrorType());
      }
      copy.put(key, Value.of(rawValueFn.apply(item)));
    }
    return new MapValue<>(Collections.unmodifiableMap(copy));
  }

  static <K, V> Value<Map<K, Value<V>>> emptyMap() {
    return ValueCache.INSTANCE.emptyMap();
  }

  @SafeVarargs
  static <K extends Comparable<K>, V> Value<SortedMap<K, Value<V>>> sortedMapOf(Map.Entry<K, V>... rawEntries) {
    if (rawEntries == null) {
      return Value.errorMessage("entries must not be null", sortedMapErrorType());
    }
    var copy = new TreeMap<K, Value<V>>();
    for (var entry : rawEntries) {
      if (entry == null) {
        return Value.errorMessage("entries must not contain nulls", sortedMapErrorType());
      }
      if (entry.getKey() == null) {
        return Value.errorMessage(SORTED_MAP_KEYS_NOT_NULL, sortedMapErrorType());
      }
      copy.put(entry.getKey(), Value.of(entry.getValue()));
    }
    return new SortedMapValue<>(Collections.unmodifiableSortedMap(copy));
  }

  static <K, V> Value<SortedMap<K, Value<V>>> sortedMapOf(SortedMap<K, Value<V>> wrappedEntries) {
    if (wrappedEntries == null) {
      return Value.errorMessage("SortedMap must not be null", sortedMapErrorType());
    }
    for (var entry : wrappedEntries.entrySet()) {
      if (entry.getKey() == null) {
        return Value.errorMessage(SORTED_MAP_KEYS_NOT_NULL, sortedMapErrorType());
      }
      if (entry.getValue() == null) {
        return Value.errorMessage(
          "SortedMap values must not be null - use Value.nullValue() instead",
          sortedMapErrorType()
        );
      }
    }
    return new SortedMapValue<>(Collections.unmodifiableSortedMap(new TreeMap<>(wrappedEntries)));
  }

  static <T, K extends Comparable<K>, V> Value<SortedMap<K, Value<V>>> sortedMapOf(
    Iterable<T> source,
    Function<T, K> keyFn,
    Function<T, V> rawValueFn
  ) {
    if (source == null || keyFn == null || rawValueFn == null) {
      return Value.errorMessage("source / keyFn / rawValueFn must not be null", sortedMapErrorType());
    }
    var copy = new TreeMap<K, Value<V>>();
    for (var item : source) {
      K key = keyFn.apply(item);
      if (key == null) {
        return Value.errorMessage(SORTED_MAP_KEYS_NOT_NULL, sortedMapErrorType());
      }
      copy.put(key, Value.of(rawValueFn.apply(item)));
    }
    return new SortedMapValue<>(Collections.unmodifiableSortedMap(copy));
  }

  static <K, V> Value<SortedMap<K, Value<V>>> emptySortedMap() {
    return ValueCache.INSTANCE.emptySortedMap();
  }

  // --- Typed (descriptor-injecting) factories. The element/value descriptor — typically a
  // TypeDescriptor.oneOf(...) — is stamped onto the collection value so a heterogeneous collection
  // self-describes on the inferred-write path. Heterogeneous elements can't be pinned to one concrete
  // E, so the element type binds to the descriptor's (usually Object) and the elements arrive as
  // Value<? extends E>. ---

  @SafeVarargs
  @SuppressWarnings("unchecked") // Value<? extends E> -> Value<E>: erased at runtime, the descriptor governs the wire
  static <E> Value<List<Value<E>>> listOfTyped(TypeDescriptor<E> elementType, Value<? extends E>... elements) {
    if (elementType == null) {
      return Value.errorMessage("elementType must not be null", listErrorType());
    }
    if (elements == null) {
      return Value.errorMessage(ELEMENTS_NOT_NULL, listErrorType());
    }
    var copy = new ArrayList<Value<E>>(elements.length);
    for (Value<? extends E> e : elements) {
      if (e == null) {
        return Value.errorMessage("List elements must not be null - use Value.nullValue() instead", listErrorType());
      }
      copy.add((Value<E>) e);
    }
    // `copy` is method-local; the ListValue constructor snapshots it (List.copyOf), so no factory-side wrap is needed.
    return new ListValue<>(copy, elementType);
  }

  @SafeVarargs
  @SuppressWarnings("unchecked")
  static <E> Value<Set<Value<E>>> setOfTyped(TypeDescriptor<E> elementType, Value<? extends E>... elements) {
    if (elementType == null) {
      return Value.errorMessage("elementType must not be null", setErrorType());
    }
    if (elements == null) {
      return Value.errorMessage(ELEMENTS_NOT_NULL, setErrorType());
    }
    var copy = new LinkedHashSet<Value<E>>(elements.length);
    for (Value<? extends E> e : elements) {
      if (e == null) {
        return Value.errorMessage("Set elements must not be null - use Value.nullValue() instead", setErrorType());
      }
      copy.add((Value<E>) e);
    }
    return new SetValue<>(copy, elementType); // SetValue's constructor snapshots (Set.copyOf)
  }

  @SuppressWarnings("unchecked")
  static <K, V> Value<Map<K, Value<V>>> mapOfTyped(
    TypeDescriptor<V> valueType,
    Map<K, ? extends Value<? extends V>> entries
  ) {
    if (valueType == null) {
      return Value.errorMessage("valueType must not be null", mapErrorType());
    }
    if (entries == null) {
      return Value.errorMessage("Map must not be null", mapErrorType());
    }
    var copy = new LinkedHashMap<K, Value<V>>();
    for (var entry : entries.entrySet()) {
      if (entry.getKey() == null) {
        return Value.errorMessage(MAP_KEYS_NOT_NULL, mapErrorType());
      }
      if (entry.getValue() == null) {
        return Value.errorMessage("Map values must not be null - use Value.nullValue() instead", mapErrorType());
      }
      copy.put(entry.getKey(), (Value<V>) entry.getValue());
    }
    return new MapValue<>(copy, valueType); // MapValue's constructor snapshots (Map.copyOf)
  }

  @SuppressWarnings("unchecked")
  static <K extends Comparable<K>, V> Value<SortedMap<K, Value<V>>> sortedMapOfTyped(
    TypeDescriptor<V> valueType,
    Map<K, ? extends Value<? extends V>> entries
  ) {
    if (valueType == null) {
      return Value.errorMessage("valueType must not be null", sortedMapErrorType());
    }
    if (entries == null) {
      return Value.errorMessage("SortedMap must not be null", sortedMapErrorType());
    }
    var copy = new TreeMap<K, Value<V>>();
    for (var entry : entries.entrySet()) {
      if (entry.getKey() == null) {
        return Value.errorMessage(SORTED_MAP_KEYS_NOT_NULL, sortedMapErrorType());
      }
      if (entry.getValue() == null) {
        return Value.errorMessage(
          "SortedMap values must not be null - use Value.nullValue() instead",
          sortedMapErrorType()
        );
      }
      copy.put(entry.getKey(), (Value<V>) entry.getValue());
    }
    return new SortedMapValue<>(copy, valueType); // SortedMapValue's constructor snapshots (new TreeMap)
  }

  @SuppressWarnings("unchecked")
  private static <E> TypeDescriptor<List<Value<E>>> listErrorType() {
    return (TypeDescriptor<List<Value<E>>>) (TypeDescriptor<?>) TypeDescriptor.ofList("List", ANY);
  }

  @SuppressWarnings("unchecked")
  private static <E> TypeDescriptor<Set<Value<E>>> setErrorType() {
    return (TypeDescriptor<Set<Value<E>>>) (TypeDescriptor<?>) TypeDescriptor.ofSet("Set", ANY);
  }

  @SuppressWarnings("unchecked")
  private static <K, V> TypeDescriptor<Map<K, Value<V>>> mapErrorType() {
    return (TypeDescriptor<Map<K, Value<V>>>) (TypeDescriptor<?>) TypeDescriptor.ofMap("Map", ANY, ANY);
  }

  @SuppressWarnings("unchecked")
  private static <K, V> TypeDescriptor<SortedMap<K, Value<V>>> sortedMapErrorType() {
    return (TypeDescriptor<SortedMap<K, Value<V>>>) (TypeDescriptor<?>) TypeDescriptor.ofSortedMap(
      "SortedMap",
      ANY,
      ANY
    );
  }
}
