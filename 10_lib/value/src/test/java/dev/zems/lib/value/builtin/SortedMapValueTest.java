package dev.zems.lib.value.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.ErrorValue;
import dev.zems.lib.value.Value;
import java.util.AbstractMap.SimpleEntry;
import java.util.Comparator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("SortedMapValue")
class SortedMapValueTest {

  @Test
  @DisplayName("Value.sortedMapOf(entries) restores natural-key order regardless of insertion order")
  void naturalOrderRestoredOnConstruction() {
    Value<SortedMap<String, Value<Integer>>> result = Value.sortedMapOf(
      Map.entry("c", 3),
      Map.entry("a", 1),
      Map.entry("b", 2)
    );
    assertThat(result).isInstanceOf(SortedMapValue.class);
    SortedMap<String, Value<Integer>> unwrapped = result.unbox();
    assertThat(unwrapped.firstKey()).isEqualTo("a");
    assertThat(unwrapped.lastKey()).isEqualTo("c");
    assertThat(unwrapped).containsExactly(
      Map.entry("a", Value.of(1)),
      Map.entry("b", Value.of(2)),
      Map.entry("c", Value.of(3))
    );
  }

  @Test
  @DisplayName("Value.emptySortedMap returns an empty SortedMapValue")
  void emptySortedMap() {
    Value<SortedMap<String, Value<Integer>>> result = Value.emptySortedMap();
    assertThat(result).isInstanceOf(SortedMapValue.class);
    assertThat(result.unbox()).isEmpty();
  }

  @Test
  @DisplayName("sortedMapOf(null) returns ErrorValue")
  void rejectsNullVarargs() {
    Map.Entry<String, Integer>[] entries = null;
    Value<SortedMap<String, Value<Integer>>> result = Value.sortedMapOf(entries);
    assertThat(result).isInstanceOf(ErrorValue.class);
  }

  @Test
  @DisplayName("sortedMapOf(map with null key) returns ErrorValue")
  void rejectsNullKey() {
    // Map.entry rejects a null key with an NPE before the factory sees it; SimpleEntry lets the null reach
    // Value.sortedMapOf so the ErrorValue answer can be asserted.
    Value<SortedMap<String, Value<Integer>>> result = Value.sortedMapOf(
      new SimpleEntry<>("a", 1),
      new SimpleEntry<>(null, 2)
    );
    assertThat(result).isInstanceOf(ErrorValue.class);
  }

  @Test
  @DisplayName("isSortedMap / asSortedMap recognise SortedMapValue and distinguish from MapValue")
  void isSortedMapAndAsSortedMap() {
    Value<SortedMap<String, Value<Integer>>> sortedV = Value.sortedMapOf(Map.entry("a", 1));
    assertThat(sortedV.isSortedMap()).isTrue();
    assertThat(sortedV.isMap()).isFalse();
    assertThat(sortedV.asSortedMap()).isPresent();
    assertThat(sortedV.asMap()).isEmpty();

    Value<Map<String, Value<Integer>>> plainV = Value.mapOf(Map.entry("a", 1));
    assertThat(plainV.isSortedMap()).isFalse();
    assertThat(plainV.isMap()).isTrue();
  }

  @Test
  @DisplayName("isSortedMapOf returns true when every key/value wraps the requested types")
  void isSortedMapOfMatchesTypes() {
    Value<SortedMap<String, Value<Integer>>> v = Value.sortedMapOf(Map.entry("a", 1), Map.entry("b", 2));
    assertThat(v.isSortedMapOf(String.class, Integer.class)).isTrue();
    assertThat(v.isSortedMapOf(String.class, String.class)).isFalse();
  }

  @Test
  @DisplayName("sortedMapOf(iterable, keyFn, valueFn) builds a naturally-ordered map")
  void sortedMapOfIterableMapper() {
    record Item(String name, int value) {}
    var src = java.util.List.of(new Item("zeta", 9), new Item("alpha", 1), new Item("middle", 5));
    Value<SortedMap<String, Value<Integer>>> result = Value.sortedMapOf(src, Item::name, Item::value);
    SortedMap<String, Value<Integer>> unwrapped = result.unbox();
    assertThat(unwrapped.firstKey()).isEqualTo("alpha");
    assertThat(unwrapped.lastKey()).isEqualTo("zeta");
  }

  @Test
  @DisplayName("Value.of((Object) SortedMap) rejects with IAE pointing to Value.sortedMapOf")
  void valueOfRejectsSortedMap() {
    SortedMap<String, Value<Integer>> input = new TreeMap<>();
    input.put("a", Value.of(1));
    assertThatThrownBy(() -> Value.of((Object) input))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Value.sortedMapOf");
  }

  @Test
  @DisplayName("forces natural-key order even when the source SortedMap carries a reverse comparator")
  void naturalOrderForcedOverSourceComparator() {
    SortedMap<String, Value<Integer>> reverse = new TreeMap<>(Comparator.reverseOrder());
    reverse.put("a", Value.of(1));
    reverse.put("b", Value.of(2));
    reverse.put("c", Value.of(3));
    Value<SortedMap<String, Value<Integer>>> result = Value.sortedMapOf(reverse);
    SortedMap<String, Value<Integer>> unwrapped = result.unbox();
    assertThat(unwrapped.firstKey()).isEqualTo("a");
    assertThat(unwrapped.lastKey()).isEqualTo("c");
    assertThat(unwrapped).containsExactly(
      Map.entry("a", Value.of(1)),
      Map.entry("b", Value.of(2)),
      Map.entry("c", Value.of(3))
    );
  }

  @Test
  @DisplayName("a non-comparable key fails with ClassCastException at construction")
  void nonComparableKeyFailsAtConstruction() {
    record Key(int n) {}
    SortedMap<Key, Value<Integer>> source = new TreeMap<>(Comparator.comparingInt(Key::n));
    source.put(new Key(2), Value.of(2));
    source.put(new Key(1), Value.of(1));
    assertThatThrownBy(() -> Value.sortedMapOf(source)).isInstanceOf(ClassCastException.class);
  }

  @Test
  @DisplayName("unboxEntries answers the unboxed entry values in natural key order, as an unmodifiable sorted map")
  void unboxEntriesKeepsNaturalKeyOrderUnmodifiable() {
    SortedMapValue<String, Integer> sorted = (SortedMapValue<String, Integer>) Value.sortedMapOf(
      Map.entry("c", 3),
      Map.entry("b", 2),
      Map.entry("a", 1)
    );

    SortedMap<String, Integer> entries = sorted.unboxEntries();

    assertThat(entries.firstKey()).isEqualTo("a");
    assertThat(entries.lastKey()).isEqualTo("c");
    assertThat(entries).containsExactly(Map.entry("a", 1), Map.entry("b", 2), Map.entry("c", 3));
    assertThatThrownBy(() -> entries.put("d", 4)).isInstanceOf(UnsupportedOperationException.class);
  }
}
