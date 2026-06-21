package dev.zems.lib.value.marshal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.JourneyTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * End-to-end coverage for heterogeneous collections: without an injected descriptor they fail clean on the
 * inferred-write path; with one (a {@code TypeDescriptor.oneOf(...)}) they self-describe and round-trip; homogeneous
 * collections still infer unchanged. Delivers {@code value-type-heterogeneous-collections}.
 */
@DisplayName("Heterogeneous collections across all wire formats")
class HeterogeneousCollectionsJourneyTest {

  static Stream<RoundTripFormatHarness.Format> formats() {
    return RoundTripFormatHarness.all();
  }

  @SuppressWarnings("unchecked")
  private static Value<Object> up(Value<?> value) {
    return (Value<Object>) value;
  }

  @Test
  @JourneyTest(speakingId = "value-type-heterogeneous-collections", acceptance = "a1")
  @DisplayName(
    "a mixed collection with no injected descriptor returns null valueType and fails the inferred write clean"
  )
  void mixedCollectionWithoutDescriptorFailsClean() {
    List<Value<Object>> rawList = List.of(up(Value.of("a")), up(Value.of(1)));
    var mixed = Value.listOf(rawList);

    assertThat(mixed.valueType()).isNull();
    assertThatThrownBy(() -> new JournalingStateWriter().write(mixed))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Cannot infer TypeDescriptor");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "value-type-heterogeneous-collections", acceptance = "a2")
  @DisplayName("an injected oneOf descriptor lets a heterogeneous list and map self-describe and round-trip")
  void injectedDescriptorSelfDescribesAndRoundTrips(RoundTripFormatHarness.Format f) {
    var union = TypeDescriptor.oneOf(
      "v",
      BuiltinTypeDescriptors.STRING,
      BuiltinTypeDescriptors.INTEGER,
      BuiltinTypeDescriptors.LONG
    );

    var list = Value.listOfTyped(union, Value.of("a"), Value.of(1), Value.of(2L));
    var listDescriptor = list.valueType(); // self-describes via the injected union
    assertThat(listDescriptor).isNotNull();
    assertThat(f.read(f.write(list, listDescriptor), listDescriptor)).isEqualTo(list);

    Map<String, Value<Object>> rawMap = new LinkedHashMap<>();
    rawMap.put("name", up(Value.of("John")));
    rawMap.put("age", up(Value.of(99L)));
    rawMap.put("rank", up(Value.of(3)));
    var map = Value.mapOfTyped(union, rawMap);
    var mapDescriptor = map.valueType();
    assertThat(mapDescriptor).isNotNull();
    assertThat(f.read(f.write(map, mapDescriptor), mapDescriptor)).isEqualTo(map);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "value-type-heterogeneous-collections", acceptance = "a3")
  @DisplayName("homogeneous collections still infer their descriptor and round-trip unchanged")
  void homogeneousCollectionsStillInfer(RoundTripFormatHarness.Format f) {
    var list = Value.listOf(1, 2, 3);
    var descriptor = list.valueType();
    assertThat(descriptor).isNotNull();
    assertThat(f.read(f.write(list, descriptor), descriptor)).isEqualTo(list);
  }
}
