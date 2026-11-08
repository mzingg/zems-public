package dev.zems.lib.value.valueio;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common.junit.JourneyTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.junit.RoundTripFormatHarness;
import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
    Value<Map<String, Value<Integer>>> forward = Value.mapOf(Map.entry("z", 26), Map.entry("a", 1), Map.entry("m", 13));

    byte[] wire = format.write(forward, descriptor);
    Map<String, Value<Integer>> reread = format.read(wire, descriptor).unbox();
    assertThat(List.copyOf(reread.keySet())).containsExactly("z", "a", "m");

    Value<Map<String, Value<Integer>>> reverse = Value.mapOf(Map.entry("m", 13), Map.entry("a", 1), Map.entry("z", 26));
    assertThat(wire).isEqualTo(format.write(forward, descriptor));
    assertThat(wire).isNotEqualTo(format.write(reverse, descriptor));
  }
}
