package dev.zems.lib.value.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.Value;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contract test for {@link MapValue} — the immutable map-of-Values wrapper that preserves the source iteration order.
 */
@ContractTest
@DisplayName("MapValue")
class MapValueTest {

  @Test
  @DisplayName("Value.mapOf preserves insertion order through the canonical constructor")
  void preservesInsertionOrder() {
    // Insertion order z, a, m — neither natural (a, m, z) nor any hash/salt order, so a re-copying
    // constructor that drops the order would be caught.
    Value<Map<String, Value<Integer>>> result = Value.mapOf(Map.entry("z", 26), Map.entry("a", 1), Map.entry("m", 13));
    assertThat(result).isInstanceOf(MapValue.class);
    Map<String, Value<Integer>> unwrapped = Value.unbox(result);
    assertThat(List.copyOf(unwrapped.keySet())).containsExactly("z", "a", "m");
  }
}
