package dev.zems.lib.value.valueio.descriptor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common.junit.JourneyTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.junit.RoundTripFormatHarness;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Records with a collection component round-trip")
class RecordCollectionRoundTripJourneyTest {

  static Stream<RoundTripFormatHarness.Format> formats() {
    return RoundTripFormatHarness.all();
  }

  public record WithList(List<String> items) {}

  public record WithSet(Set<String> tags) {}

  public record WithMap(Map<String, Integer> counts) {}

  public record WithSortedMap(SortedMap<String, Integer> ranks) {}

  private static <T> T roundTrip(RoundTripFormatHarness.Format f, T record, Class<T> type) {
    var descriptor = TypeDescriptor.of(type);
    Value<T> back = f.read(f.write(Value.of(record), descriptor), descriptor);
    return back.unbox();
  }

  @ParameterizedTest(name = "[{0}] List<String> component")
  @MethodSource("formats")
  @JourneyTest(speakingId = "record-collection-component-roundtrip", acceptance = "a1")
  void listComponent(RoundTripFormatHarness.Format f) {
    WithList result = roundTrip(f, new WithList(List.of("a", "b", "c")), WithList.class);
    assertThat(result.items()).containsExactly("a", "b", "c");
  }

  @ParameterizedTest(name = "[{0}] Set<String> component")
  @MethodSource("formats")
  @JourneyTest(speakingId = "record-collection-component-roundtrip", acceptance = "a2")
  void setComponent(RoundTripFormatHarness.Format f) {
    WithSet result = roundTrip(f, new WithSet(Set.of("x", "y", "z")), WithSet.class);
    assertThat(result.tags()).containsExactlyInAnyOrder("x", "y", "z");
  }

  @ParameterizedTest(name = "[{0}] Map<String, Integer> component")
  @MethodSource("formats")
  @JourneyTest(speakingId = "record-collection-component-roundtrip", acceptance = "a2")
  void mapComponent(RoundTripFormatHarness.Format f) {
    WithMap result = roundTrip(f, new WithMap(Map.of("one", 1, "two", 2)), WithMap.class);
    assertThat(result.counts()).containsExactlyInAnyOrderEntriesOf(Map.of("one", 1, "two", 2));
  }

  @ParameterizedTest(name = "[{0}] SortedMap<String, Integer> component")
  @MethodSource("formats")
  @JourneyTest(speakingId = "record-collection-component-roundtrip", acceptance = "a2")
  void sortedMapComponent(RoundTripFormatHarness.Format f) {
    var ranks = new TreeMap<String, Integer>();
    ranks.put("b", 2);
    ranks.put("a", 1);
    WithSortedMap result = roundTrip(f, new WithSortedMap(ranks), WithSortedMap.class);
    assertThat(result.ranks()).containsExactly(Map.entry("a", 1), Map.entry("b", 2));
  }
}
