package dev.zems.lib.value.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.Value;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("MapValue")
class MapValueTest {

  @Test
  @DisplayName("Value.mapOf preserves insertion order through the canonical constructor")
  void preservesInsertionOrder() {
    Value<Map<String, Value<Integer>>> result = Value.mapOf(Map.entry("z", 26), Map.entry("a", 1), Map.entry("m", 13));
    assertThat(result).isInstanceOf(MapValue.class);
    Map<String, Value<Integer>> unwrapped = result.unbox();
    assertThat(List.copyOf(unwrapped.keySet())).containsExactly("z", "a", "m");
  }

  @Test
  @DisplayName("unboxEntries answers the unboxed entry values in insertion order, as an unmodifiable map")
  void unboxEntriesKeepsInsertionOrderUnmodifiable() {
    var source = new LinkedHashMap<String, Value<Integer>>();
    source.put("b", Value.of(1));
    source.put("a", Value.of(2));
    MapValue<String, Integer> map = (MapValue<String, Integer>) Value.mapOf(source);

    Map<String, Integer> entries = map.unboxEntries();

    assertThat(List.copyOf(entries.keySet())).containsExactly("b", "a");
    assertThat(entries).containsExactly(Map.entry("b", 1), Map.entry("a", 2));
    assertThatThrownBy(() -> entries.put("c", 3)).isInstanceOf(UnsupportedOperationException.class);
  }
}
