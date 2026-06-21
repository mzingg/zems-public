package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.THROWABLE;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.builtin.ListValue;
import dev.zems.lib.value.builtin.MapValue;
import dev.zems.lib.value.builtin.SetValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contract test for {@link ValueCollections} — the package-private factory implementations behind
 * {@code Value.listOf(...)}, {@code Value.mapOf(...)}, {@code Value.emptyList()}, and {@code Value.emptyMap()}. Pins
 * null/blank-input rejection branches and the basic wrapping behaviour for each variant.
 */
@ContractTest
@DisplayName("ValueCollections")
class ValueCollectionsTest {

  // ============ listOf(E... rawElements) ============

  @Test
  @DisplayName("listOf(varargs) wraps each raw element via Value.of")
  void listOfVarargsWraps() {
    Value<List<Value<String>>> result = Value.listOf("a", "b", "c");
    assertThat(result).isInstanceOf(ListValue.class);
    assertThat(Value.unbox(result)).containsExactly(Value.of("a"), Value.of("b"), Value.of("c"));
  }

  @Test
  @DisplayName("listOf() with no elements returns empty ListValue")
  void listOfEmptyVarargs() {
    Value<List<Value<String>>> result = Value.listOf();
    assertThat(result).isInstanceOf(ListValue.class);
    assertThat(Value.unbox(result)).isEmpty();
  }

  @Test
  @DisplayName("listOf((E[]) null) returns ErrorValue")
  void listOfNullVarargs() {
    String[] elements = null;
    Value<List<Value<String>>> result = Value.listOf(elements);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).hasMessage("elements must not be null");
  }

  // ============ listOf(List<Value<E>>) — pre-wrapped variant ============

  @Test
  @DisplayName("listOf(pre-wrapped list) wraps directly")
  void listOfPreWrapped() {
    var pre = List.of(Value.of("x"), Value.of("y"));
    Value<List<Value<String>>> result = Value.listOf(pre);
    assertThat(result).isInstanceOf(ListValue.class);
    assertThat(Value.unbox(result)).containsExactly(Value.of("x"), Value.of("y"));
  }

  @Test
  @DisplayName("listOf((List) null) returns ErrorValue")
  void listOfNullList() {
    List<Value<String>> nullList = null;
    Value<List<Value<String>>> result = Value.listOf(nullList);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).hasMessage("List must not be null");
  }

  @Test
  @DisplayName("listOf(list with null element) returns ErrorValue")
  void listOfListWithNullElement() {
    var withNull = new ArrayList<Value<String>>();
    withNull.add(Value.of("a"));
    withNull.add(null);
    Value<List<Value<String>>> result = Value.listOf(withNull);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).hasMessageContaining("List elements must not be null");
  }

  // ============ listOf(Iterable, mapper) ============

  @Test
  @DisplayName("listOf(iterable, mapper) maps + wraps each element")
  void listOfIterableMapper() {
    var src = List.of(1, 2, 3);
    Value<List<Value<String>>> result = Value.listOf(src, Object::toString);
    assertThat(Value.unbox(result)).containsExactly(Value.of("1"), Value.of("2"), Value.of("3"));
  }

  @Test
  @DisplayName("listOf((Iterable) null, mapper) returns ErrorValue")
  void listOfNullIterable() {
    Value<List<Value<String>>> result = Value.listOf((Iterable<Integer>) null, Object::toString);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).hasMessage("source must not be null");
  }

  @Test
  @DisplayName("listOf(iterable, null mapper) returns ErrorValue")
  void listOfNullMapper() {
    Value<List<Value<String>>> result = Value.listOf(List.of(1, 2), null);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).hasMessage("mapper must not be null");
  }

  // ============ emptyList ============

  @Test
  @DisplayName("emptyList() returns ListValue containing no elements")
  void emptyListReturnsEmpty() {
    Value<List<Value<String>>> result = Value.emptyList();
    assertThat(result).isInstanceOf(ListValue.class);
    assertThat(Value.unbox(result)).isEmpty();
  }

  // ============ setOf(E... rawElements) ============

  @Test
  @DisplayName("setOf(varargs) wraps each raw element via Value.of")
  void setOfVarargsWraps() {
    Value<Set<Value<String>>> result = Value.setOf("a", "b", "c");
    assertThat(result).isInstanceOf(SetValue.class);
    assertThat(Value.unbox(result)).containsExactlyInAnyOrder(Value.of("a"), Value.of("b"), Value.of("c"));
  }

  @Test
  @DisplayName("setOf(varargs) dedupes equal raw elements")
  void setOfVarargsDedupes() {
    Value<Set<Value<String>>> result = Value.setOf("a", "a", "b");
    assertThat(Value.unbox(result)).containsExactlyInAnyOrder(Value.of("a"), Value.of("b"));
  }

  @Test
  @DisplayName("setOf() with no elements returns empty SetValue")
  void setOfEmptyVarargs() {
    Value<Set<Value<String>>> result = Value.setOf();
    assertThat(result).isInstanceOf(SetValue.class);
    assertThat(Value.unbox(result)).isEmpty();
  }

  @Test
  @DisplayName("setOf((E[]) null) returns ErrorValue")
  void setOfNullVarargs() {
    String[] elements = null;
    Value<Set<Value<String>>> result = Value.setOf(elements);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).hasMessage("elements must not be null");
  }

  // ============ setOf(Set<Value<E>>) — pre-wrapped variant ============

  @Test
  @DisplayName("setOf(pre-wrapped set) wraps directly")
  void setOfPreWrapped() {
    var pre = Set.of(Value.of("x"), Value.of("y"));
    Value<Set<Value<String>>> result = Value.setOf(pre);
    assertThat(result).isInstanceOf(SetValue.class);
    assertThat(Value.unbox(result)).containsExactlyInAnyOrder(Value.of("x"), Value.of("y"));
  }

  @Test
  @DisplayName("setOf((Set) null) returns ErrorValue")
  void setOfNullSet() {
    Set<Value<String>> nullSet = null;
    Value<Set<Value<String>>> result = Value.setOf(nullSet);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).hasMessage("Set must not be null");
  }

  @Test
  @DisplayName("setOf(set with null element) returns ErrorValue")
  void setOfSetWithNullElement() {
    var withNull = new LinkedHashSet<Value<String>>();
    withNull.add(Value.of("a"));
    withNull.add(null);
    Value<Set<Value<String>>> result = Value.setOf(withNull);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).hasMessageContaining("Set elements must not be null");
  }

  // ============ setOf(Iterable, mapper) ============

  @Test
  @DisplayName("setOf(iterable, mapper) maps + wraps each element")
  void setOfIterableMapper() {
    var src = List.of(1, 2, 3);
    Value<Set<Value<String>>> result = Value.setOf(src, Object::toString);
    assertThat(Value.unbox(result)).containsExactlyInAnyOrder(Value.of("1"), Value.of("2"), Value.of("3"));
  }

  @Test
  @DisplayName("setOf((Iterable) null, mapper) returns ErrorValue")
  void setOfNullIterable() {
    Value<Set<Value<String>>> result = Value.setOf((Iterable<Integer>) null, Object::toString);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).hasMessage("source must not be null");
  }

  @Test
  @DisplayName("setOf(iterable, null mapper) returns ErrorValue")
  void setOfNullMapper() {
    Value<Set<Value<String>>> result = Value.setOf(new HashSet<>(List.of(1, 2)), null);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).hasMessage("mapper must not be null");
  }

  // ============ emptySet ============

  @Test
  @DisplayName("emptySet() returns SetValue containing no elements")
  void emptySetReturnsEmpty() {
    Value<Set<Value<String>>> result = Value.emptySet();
    assertThat(result).isInstanceOf(SetValue.class);
    assertThat(Value.unbox(result)).isEmpty();
  }

  // ============ mapOf(Map.Entry... rawEntries) ============

  @Test
  @DisplayName("mapOf(varargs entries) wraps values via Value.of")
  void mapOfVarargsWraps() {
    Value<Map<String, Value<Integer>>> result = Value.mapOf(Map.entry("a", 1), Map.entry("b", 2));
    assertThat(result).isInstanceOf(MapValue.class);
    Map<String, Value<Integer>> unwrapped = Value.unbox(result);
    assertThat(unwrapped).containsEntry("a", Value.of(1)).containsEntry("b", Value.of(2));
  }

  @Test
  @DisplayName("mapOf((Map.Entry[]) null) returns ErrorValue")
  void mapOfNullVarargs() {
    @SuppressWarnings("unchecked")
    Map.Entry<String, Integer>[] entries = (Map.Entry<String, Integer>[]) (Map.Entry<?, ?>[]) null;
    Value<Map<String, Value<Integer>>> result = Value.mapOf(entries);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).hasMessage("entries must not be null");
  }

  @Test
  @DisplayName("mapOf(varargs containing null entry) returns ErrorValue")
  void mapOfNullEntry() {
    @SuppressWarnings("unchecked")
    Map.Entry<String, Integer>[] withNull = new Map.Entry[] { Map.entry("a", 1), null };
    Value<Map<String, Value<Integer>>> result = Value.mapOf(withNull);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).hasMessage("entries must not contain nulls");
  }

  // ============ mapOf(Map<K, Value<V>>) — pre-wrapped variant ============

  @Test
  @DisplayName("mapOf(pre-wrapped map) wraps directly")
  void mapOfPreWrapped() {
    var pre = Map.of("k", Value.of(7));
    Value<Map<String, Value<Integer>>> result = Value.mapOf(pre);
    assertThat(Value.unbox(result)).containsEntry("k", Value.of(7));
  }

  @Test
  @DisplayName("mapOf((Map) null) returns ErrorValue")
  void mapOfNullMap() {
    Map<String, Value<Integer>> nullMap = null;
    Value<Map<String, Value<Integer>>> result = Value.mapOf(nullMap);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).hasMessage("Map must not be null");
  }

  @Test
  @DisplayName("mapOf(map with null key) returns ErrorValue")
  void mapOfMapWithNullKey() {
    var m = new HashMap<String, Value<Integer>>();
    m.put(null, Value.of(1));
    Value<Map<String, Value<Integer>>> result = Value.mapOf(m);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).hasMessage("Map keys must not be null");
  }

  @Test
  @DisplayName("mapOf(map with null value) returns ErrorValue")
  void mapOfMapWithNullValue() {
    var m = new HashMap<String, Value<Integer>>();
    m.put("k", null);
    Value<Map<String, Value<Integer>>> result = Value.mapOf(m);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).hasMessageContaining("Map values must not be null");
  }

  // ============ mapOf(Iterable, keyFn, rawValueFn) ============

  @Test
  @DisplayName("mapOf(iterable, keyFn, valueFn) maps + wraps each entry")
  void mapOfIterableMapper() {
    record Item(String name, int value) {}
    var src = List.of(new Item("a", 1), new Item("b", 2));
    Value<Map<String, Value<Integer>>> result = Value.mapOf(src, Item::name, Item::value);
    Map<String, Value<Integer>> unwrapped = Value.unbox(result);
    assertThat(unwrapped).containsEntry("a", Value.of(1)).containsEntry("b", Value.of(2));
  }

  @Test
  @DisplayName("mapOf((Iterable) null, ...) returns ErrorValue")
  void mapOfNullIterable() {
    Value<Map<String, Value<Integer>>> result = Value.mapOf((Iterable<String>) null, s -> s, String::length);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).hasMessageContaining("must not be null");
  }

  @Test
  @DisplayName("mapOf(iterable, null keyFn, ...) returns ErrorValue")
  void mapOfNullKeyFn() {
    Value<Map<String, Value<Integer>>> result = Value.mapOf(List.of("a"), null, String::length);
    assertThat(result).isInstanceOf(ErrorValue.class);
  }

  @Test
  @DisplayName("mapOf(iterable, keyFn, null valueFn) returns ErrorValue")
  void mapOfNullValueFn() {
    Value<Map<String, Value<Integer>>> result = Value.mapOf(List.of("a"), s -> s, null);
    assertThat(result).isInstanceOf(ErrorValue.class);
  }

  @Test
  @DisplayName("mapOf(iterable, keyFn returning null, valueFn) returns ErrorValue")
  void mapOfKeyFnReturnsNull() {
    Value<Map<String, Value<Integer>>> result = Value.mapOf(List.of("a"), s -> null, String::length);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).hasMessage("Map keys must not be null");
  }

  // ============ emptyMap ============

  @Test
  @DisplayName("emptyMap() returns MapValue containing no entries")
  void emptyMapReturnsEmpty() {
    Value<Map<String, Value<Integer>>> result = Value.emptyMap();
    assertThat(result).isInstanceOf(MapValue.class);
    assertThat(Value.unbox(result)).isEmpty();
  }
}
