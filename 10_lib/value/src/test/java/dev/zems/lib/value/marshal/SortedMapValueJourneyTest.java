package dev.zems.lib.value.marshal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.JourneyTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.util.Comparator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * End-to-end round-trip for {@link dev.zems.lib.value.builtin.SortedMapValue} through every wire format. Validates that
 * natural-key ordering is restored on read regardless of write order, and that {@code TypeDescriptor.ofSortedMap}
 * composes cleanly with the existing scalar descriptors.
 */
@DisplayName("SortedMapValue round-trip across all wire formats")
class SortedMapValueJourneyTest {

  static Stream<RoundTripFormatHarness.Format> formats() {
    return RoundTripFormatHarness.all();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "set-and-sortedmap-value-first-class", acceptance = "a4")
  @DisplayName("round-trips a SortedMap<String, Integer>")
  void roundTripsSortedMapStringInt(RoundTripFormatHarness.Format format) {
    TypeDescriptor<SortedMap<String, Value<Integer>>> descriptor = TypeDescriptor.ofSortedMap(
      "test.sortedmap.s2i",
      BuiltinTypeDescriptors.STRING,
      BuiltinTypeDescriptors.INTEGER
    );
    Value<SortedMap<String, Value<Integer>>> original = Value.sortedMapOf(
      Map.entry("c", 3),
      Map.entry("a", 1),
      Map.entry("b", 2)
    );

    byte[] wire = format.write(original, descriptor);
    Value<SortedMap<String, Value<Integer>>> reread = format.read(wire, descriptor);

    SortedMap<String, Value<Integer>> unwrapped = Value.unbox(reread);
    assertThat(unwrapped.firstKey()).isEqualTo("a");
    assertThat(unwrapped.lastKey()).isEqualTo("c");
    assertThat(unwrapped).containsExactly(
      Map.entry("a", Value.of(1)),
      Map.entry("b", Value.of(2)),
      Map.entry("c", Value.of(3))
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "set-and-sortedmap-value-first-class", acceptance = "a5")
  @DisplayName("round-trips an empty SortedMap")
  void roundTripsEmptySortedMap(RoundTripFormatHarness.Format format) {
    TypeDescriptor<SortedMap<String, Value<Integer>>> descriptor = TypeDescriptor.ofSortedMap(
      "test.sortedmap.empty",
      BuiltinTypeDescriptors.STRING,
      BuiltinTypeDescriptors.INTEGER
    );
    Value<SortedMap<String, Value<Integer>>> original = Value.emptySortedMap();

    byte[] wire = format.write(original, descriptor);
    Value<SortedMap<String, Value<Integer>>> reread = format.read(wire, descriptor);

    assertThat(Value.unbox(reread)).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "collection-wrappers-preserve-ordering", acceptance = "a3")
  @DisplayName("serialises in natural key order regardless of the source map's comparator")
  void serialisesInNaturalOrderRegardlessOfSource(RoundTripFormatHarness.Format format) {
    TypeDescriptor<SortedMap<String, Value<Integer>>> descriptor = TypeDescriptor.ofSortedMap(
      "test.sortedmap.reverse",
      BuiltinTypeDescriptors.STRING,
      BuiltinTypeDescriptors.INTEGER
    );
    SortedMap<String, Value<Integer>> reverseSource = new TreeMap<>(Comparator.reverseOrder());
    reverseSource.put("a", Value.of(1));
    reverseSource.put("b", Value.of(2));
    reverseSource.put("c", Value.of(3));

    Value<SortedMap<String, Value<Integer>>> fromReverse = Value.sortedMapOf(reverseSource);
    Value<SortedMap<String, Value<Integer>>> fromNatural = Value.sortedMapOf(
      Map.entry("a", 1),
      Map.entry("b", 2),
      Map.entry("c", 3)
    );

    // The wire bytes must be identical no matter the source ordering — natural order is pinned at construction.
    byte[] wire = format.write(fromReverse, descriptor);
    assertThat(wire).isEqualTo(format.write(fromNatural, descriptor));

    // And the round-trip still reads back in natural order.
    SortedMap<String, Value<Integer>> reread = Value.unbox(format.read(wire, descriptor));
    assertThat(reread.firstKey()).isEqualTo("a");
    assertThat(reread.lastKey()).isEqualTo("c");
  }
}
