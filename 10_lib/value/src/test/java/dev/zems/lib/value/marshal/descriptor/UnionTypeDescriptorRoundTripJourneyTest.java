package dev.zems.lib.value.marshal.descriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.JourneyTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.builtin.ByteValue;
import dev.zems.lib.value.builtin.DoubleValue;
import dev.zems.lib.value.builtin.FloatValue;
import dev.zems.lib.value.builtin.IntegerValue;
import dev.zems.lib.value.builtin.LongValue;
import dev.zems.lib.value.builtin.ShortValue;
import dev.zems.lib.value.marshal.JournalingStateWriter;
import dev.zems.lib.value.marshal.RoundTripFormatHarness;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * End-to-end round-trips for {@link UnionTypeDescriptor} (the explicit {@code oneOf}) across every wire format
 * ({@code ValueIo.framed(...)} binary + JSON). Each method pins one acceptance bullet of {@code oneof-union-descriptor}.
 */
@DisplayName("oneOf union descriptor round-trip across all wire formats")
class UnionTypeDescriptorRoundTripJourneyTest {

  static Stream<RoundTripFormatHarness.Format> formats() {
    return RoundTripFormatHarness.all();
  }

  @SuppressWarnings("unchecked")
  private static Value<Object> up(Value<?> value) {
    return (Value<Object>) value;
  }

  private static <T> Value<T> roundTrip(RoundTripFormatHarness.Format f, Value<T> value, TypeDescriptor<T> descriptor) {
    return f.read(f.write(value, descriptor), descriptor);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "oneof-union-descriptor", acceptance = "a1")
  @DisplayName("the branch discriminator selects the right branch on read")
  void discriminatorSelectsBranch(RoundTripFormatHarness.Format f) {
    var union = TypeDescriptor.oneOf("u", BuiltinTypeDescriptors.STRING, BuiltinTypeDescriptors.LONG);

    assertThat(roundTrip(f, up(Value.of("hi")), union)).isEqualTo(Value.of("hi"));
    assertThat(roundTrip(f, up(Value.of(42L)), union)).isEqualTo(Value.of(42L));
  }

  @Test
  @JourneyTest(speakingId = "oneof-union-descriptor", acceptance = "a2")
  @DisplayName("a value outside the union fails fast naming the type and the permitted set")
  void offSetValueThrowsSharpened() {
    var union = TypeDescriptor.oneOf("u", BuiltinTypeDescriptors.STRING);
    var writer = new JournalingStateWriter();

    assertThatThrownBy(() -> union.write(writer, 42L))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("java.lang.Long")
      .hasMessageContaining("OneOf<java.lang.String>");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "oneof-union-descriptor", acceptance = "a3")
  @DisplayName("read reconstructs the exact numeric subtype")
  void exactNumericSubtypePreserved(RoundTripFormatHarness.Format f) {
    var union = TypeDescriptor.oneOf(
      "nums",
      BuiltinTypeDescriptors.INTEGER,
      BuiltinTypeDescriptors.LONG,
      BuiltinTypeDescriptors.DOUBLE,
      BuiltinTypeDescriptors.SHORT,
      BuiltinTypeDescriptors.BYTE,
      BuiltinTypeDescriptors.FLOAT
    );

    assertThat(roundTrip(f, up(Value.of(42L)), union))
      .isInstanceOf(LongValue.class)
      .isEqualTo(Value.of(42L));
    assertThat(roundTrip(f, up(Value.of(7)), union))
      .isInstanceOf(IntegerValue.class)
      .isEqualTo(Value.of(7));
    assertThat(roundTrip(f, up(Value.of(3.5)), union))
      .isInstanceOf(DoubleValue.class)
      .isEqualTo(Value.of(3.5));
    assertThat(roundTrip(f, up(Value.of((short) 5)), union)).isInstanceOf(ShortValue.class);
    assertThat(roundTrip(f, up(Value.of((byte) 9)), union)).isInstanceOf(ByteValue.class);
    assertThat(roundTrip(f, up(Value.of(2.5f)), union)).isInstanceOf(FloatValue.class);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "oneof-union-descriptor", acceptance = "a4")
  @DisplayName("composes with ofMap / ofList to marshal heterogeneous collections")
  void composesWithMapAndList(RoundTripFormatHarness.Format f) {
    var union = TypeDescriptor.oneOf(
      "v",
      BuiltinTypeDescriptors.STRING,
      BuiltinTypeDescriptors.INTEGER,
      BuiltinTypeDescriptors.LONG,
      BuiltinTypeDescriptors.BOOLEAN
    );

    var mapDescriptor = TypeDescriptor.ofMap("m", BuiltinTypeDescriptors.STRING, union);
    Map<String, Value<Object>> rawMap = new LinkedHashMap<>();
    rawMap.put("name", up(Value.of("John")));
    rawMap.put("age", up(Value.of(42L)));
    rawMap.put("rank", up(Value.of(7)));
    rawMap.put("active", up(Value.of(true)));
    var map = Value.mapOf(rawMap);
    assertThat(roundTrip(f, map, mapDescriptor)).isEqualTo(map);

    var listDescriptor = TypeDescriptor.ofList("l", union);
    List<Value<Object>> rawList = List.of(up(Value.of("a")), up(Value.of(1)), up(Value.of(2L)), up(Value.of(false)));
    var list = Value.listOf(rawList);
    assertThat(roundTrip(f, list, listDescriptor)).isEqualTo(list);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "oneof-union-descriptor", acceptance = "a5")
  @DisplayName("state markers in a union slot round-trip via the state path, bypassing the union")
  void stateMarkersBypassTheUnion(RoundTripFormatHarness.Format f) {
    var union = TypeDescriptor.oneOf("v", BuiltinTypeDescriptors.STRING, BuiltinTypeDescriptors.LONG);
    var mapDescriptor = TypeDescriptor.ofMap("m", BuiltinTypeDescriptors.STRING, union);
    Map<String, Value<Object>> rawMap = new LinkedHashMap<>();
    rawMap.put("present", up(Value.of("x")));
    rawMap.put("removed", up(Value.tombstone()));
    rawMap.put("missing", up(Value.undefined()));
    var map = Value.mapOf(rawMap);

    Map<String, Value<Object>> back = Value.unbox(roundTrip(f, map, mapDescriptor));

    assertThat(back.get("present")).isEqualTo(Value.of("x"));
    assertThat(back.get("removed").isTombstone()).isTrue();
    assertThat(back.get("missing").isUndefined()).isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "oneof-union-descriptor", acceptance = "a6")
  @DisplayName("a mixed map round-trips equal with all six numeric subtypes preserved")
  void mixedMapRoundTripsEqual(RoundTripFormatHarness.Format f) {
    var union = TypeDescriptor.oneOf(
      "v",
      BuiltinTypeDescriptors.STRING,
      BuiltinTypeDescriptors.BOOLEAN,
      BuiltinTypeDescriptors.INTEGER,
      BuiltinTypeDescriptors.LONG,
      BuiltinTypeDescriptors.DOUBLE,
      BuiltinTypeDescriptors.SHORT,
      BuiltinTypeDescriptors.BYTE,
      BuiltinTypeDescriptors.FLOAT
    );
    var mapDescriptor = TypeDescriptor.ofMap("m", BuiltinTypeDescriptors.STRING, union);
    Map<String, Value<Object>> rawMap = new LinkedHashMap<>();
    rawMap.put("s", up(Value.of("John")));
    rawMap.put("b", up(Value.of(true)));
    rawMap.put("i", up(Value.of(7)));
    rawMap.put("l", up(Value.of(42L)));
    rawMap.put("d", up(Value.of(3.5)));
    rawMap.put("sh", up(Value.of((short) 5)));
    rawMap.put("by", up(Value.of((byte) 9)));
    rawMap.put("fl", up(Value.of(2.5f)));
    var map = Value.mapOf(rawMap);

    assertThat(roundTrip(f, map, mapDescriptor)).isEqualTo(map);
  }
}
