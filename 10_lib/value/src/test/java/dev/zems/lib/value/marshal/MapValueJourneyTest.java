package dev.zems.lib.value.marshal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.JourneyTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * End-to-end round-trip for {@link dev.zems.lib.value.builtin.MapValue}. Validates that insertion order survives the
 * write → wire → read cycle and is reflected in the wire bytes, so FRAMED checksums and golden fixtures stay stable.
 */
@DisplayName("MapValue round-trip preserves insertion order across all wire formats")
class MapValueJourneyTest {

  static Stream<RoundTripFormatHarness.Format> formats() {
    return RoundTripFormatHarness.all();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "collection-wrappers-preserve-ordering", acceptance = "a1")
  @DisplayName("insertion order survives the round-trip and rides the wire bytes")
  void insertionOrderSurvivesRoundTrip(RoundTripFormatHarness.Format format) {
    TypeDescriptor<Map<String, Value<Integer>>> descriptor = TypeDescriptor.ofMap(
      "test.map.s2i",
      BuiltinTypeDescriptors.STRING,
      BuiltinTypeDescriptors.INTEGER
    );
    // Insertion order z, a, m — neither natural nor any hash/salt order.
    Value<Map<String, Value<Integer>>> forward = Value.mapOf(Map.entry("z", 26), Map.entry("a", 1), Map.entry("m", 13));

    byte[] wire = format.write(forward, descriptor);
    Map<String, Value<Integer>> reread = Value.unbox(format.read(wire, descriptor));
    assertThat(List.copyOf(reread.keySet())).containsExactly("z", "a", "m");

    // Identical content+order serialises identically; the same content in a different insertion order does not —
    // so the order genuinely rides the wire rather than being incidental.
    Value<Map<String, Value<Integer>>> reverse = Value.mapOf(Map.entry("m", 13), Map.entry("a", 1), Map.entry("z", 26));
    assertThat(wire).isEqualTo(format.write(forward, descriptor));
    assertThat(wire).isNotEqualTo(format.write(reverse, descriptor));
  }
}
