package dev.zems.lib.value.marshal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.JourneyTest;
import dev.zems.lib.value.BoxedValue;
import dev.zems.lib.value.ErrorValue;
import dev.zems.lib.value.NullValue;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.builtin.IntegerValue;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import dev.zems.lib.value.marshal.wire.WireConstraintViolationException;
import dev.zems.lib.value.marshal.wire.WireConstraints;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * End-to-end round-trip tests for the recursive Value boundary introduced by the containers refactor:
 * {@code Value<List<Value<E>>>} / {@code Value<Map<K, Value<V>>>} flow uniformly through
 * {@code writer.write(value, descriptor)} and {@code reader.read(descriptor)} default methods on {@link StateReader} /
 * {@link StateWriter}, with state markers preserved per-element / per-entry. Parameterised across binary and JSON wire
 * formats.
 */
@DisplayName("Recursive Value round-trip (containers refactor)")
class RecursiveValueRoundTripTest {

  static Stream<RoundTripFormatHarness.Format> formats() {
    return RoundTripFormatHarness.all();
  }

  // -- Format harness — extracted to RoundTripFormatHarness for reuse across round-trip tests.

  static <T> Value<T> roundTrip(RoundTripFormatHarness.Format f, Value<T> value, TypeDescriptor<T> descriptor) {
    return f.read(f.write(value, descriptor), descriptor);
  }

  // -- Helpers --------------------------------------------------------------------------

  // Test record for structured-descriptor coverage.
  public record Point(int x, int y) {}

  // -- Recursive list / map round-trip --------------------------------------------------

  @Nested
  @DisplayName("Recursive list / map round-trip")
  class Recursive {

    @ParameterizedTest(name = "[{0}] List<Value<Integer>> [1, 2, 3]")
    @MethodSource("dev.zems.lib.value.marshal.RecursiveValueRoundTripTest#formats")
    @JourneyTest(speakingId = "recursive-list-map-roundtrip", acceptance = "a1")
    void listOfIntegers(RoundTripFormatHarness.Format f) {
      var descriptor = TypeDescriptor.of(List.class, Integer.class);
      Value<List<Value<Integer>>> v = Value.listOf(1, 2, 3);

      @SuppressWarnings({ "unchecked", "rawtypes" })
      Value<List<Value<Integer>>> v2 = (Value) roundTrip(f, (Value) v, (TypeDescriptor) descriptor);

      assertThat(v2).isInstanceOf(dev.zems.lib.value.builtin.ListValue.class);
      assertThat(v2.asList()).isPresent();
      var elements = v2.asList().get();
      assertThat(elements).hasSize(3);
      assertThat(elements.get(0)).isEqualTo(Value.of(1));
      assertThat(elements.get(1)).isEqualTo(Value.of(2));
      assertThat(elements.get(2)).isEqualTo(Value.of(3));
    }

    @ParameterizedTest(name = "[{0}] List<Value<Point>> with user record")
    @MethodSource("dev.zems.lib.value.marshal.RecursiveValueRoundTripTest#formats")
    @JourneyTest(speakingId = "boxed-value-coverage-and-npe", acceptance = "a3")
    void listOfPoints(RoundTripFormatHarness.Format f) {
      var descriptor = TypeDescriptor.of(List.class, Point.class);
      Value<List<Value<Point>>> v = Value.listOf(new Point(1, 2), new Point(3, 4));

      @SuppressWarnings({ "unchecked", "rawtypes" })
      Value<List<Value<Point>>> v2 = (Value) roundTrip(f, (Value) v, (TypeDescriptor) descriptor);

      assertThat(v2.asList()).isPresent();
      var elements = v2.asList().get();
      assertThat(elements).hasSize(2);
      assertThat(elements.get(0)).isInstanceOf(BoxedValue.class);
      assertThat(((BoxedValue<?>) elements.get(0)).inner()).isEqualTo(new Point(1, 2));
      assertThat(((BoxedValue<?>) elements.get(1)).inner()).isEqualTo(new Point(3, 4));
    }

    @ParameterizedTest(name = "[{0}] Map<String, Value<Integer>>")
    @MethodSource("dev.zems.lib.value.marshal.RecursiveValueRoundTripTest#formats")
    @JourneyTest(speakingId = "recursive-list-map-roundtrip", acceptance = "a2")
    void mapStringInteger(RoundTripFormatHarness.Format f) {
      var descriptor = TypeDescriptor.of(Map.class, String.class, Integer.class);
      Value<Map<String, Value<Integer>>> v = Value.mapOf(Map.entry("a", 1), Map.entry("b", 2));

      @SuppressWarnings({ "unchecked", "rawtypes" })
      Value<Map<String, Value<Integer>>> v2 = (Value) roundTrip(f, (Value) v, (TypeDescriptor) descriptor);

      assertThat(v2.asMap()).isPresent();
      var entries = v2.asMap().get();
      assertThat(entries).hasSize(2);
      assertThat(entries.get("a")).isEqualTo(Value.of(1));
      assertThat(entries.get("b")).isEqualTo(Value.of(2));
    }

    @ParameterizedTest(name = "[{0}] Map<String, Value<Point>> with user record values")
    @MethodSource("dev.zems.lib.value.marshal.RecursiveValueRoundTripTest#formats")
    @JourneyTest(speakingId = "recursive-list-map-roundtrip", acceptance = "a3")
    void mapStringPoint(RoundTripFormatHarness.Format f) {
      var descriptor = TypeDescriptor.of(Map.class, String.class, Point.class);
      Value<Map<String, Value<Point>>> v = Value.mapOf(
        Map.entry("origin", new Point(0, 0)),
        Map.entry("end", new Point(5, 5))
      );

      @SuppressWarnings({ "unchecked", "rawtypes" })
      Value<Map<String, Value<Point>>> v2 = (Value) roundTrip(f, (Value) v, (TypeDescriptor) descriptor);

      var entries = v2.asMap().get();
      assertThat(entries).hasSize(2);
      assertThat(((BoxedValue<?>) entries.get("origin")).inner()).isEqualTo(new Point(0, 0));
      assertThat(((BoxedValue<?>) entries.get("end")).inner()).isEqualTo(new Point(5, 5));
    }

    @ParameterizedTest(name = "[{0}] List<Map<String, Integer>> deeply nested")
    @MethodSource("dev.zems.lib.value.marshal.RecursiveValueRoundTripTest#formats")
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @JourneyTest(speakingId = "recursive-list-map-roundtrip", acceptance = "a4")
    void deeplyNestedListOfMap(RoundTripFormatHarness.Format f) {
      var innerMapDescriptor = TypeDescriptor.of(Map.class, String.class, Integer.class);
      var listDescriptor = TypeDescriptor.of(List.class, innerMapDescriptor);

      var inner1 = new LinkedHashMap<String, Value<Integer>>();
      inner1.put("a", Value.of(1));
      inner1.put("b", Value.of(2));
      var inner2 = new LinkedHashMap<String, Value<Integer>>();
      inner2.put("c", Value.of(3));

      var elements = List.of(Value.mapOf(inner1), (Value) Value.mapOf(inner2));
      Value v = Value.listOf((List) elements);

      Value v2 = roundTrip(f, v, (TypeDescriptor) listDescriptor);

      var outer = ((Value<?>) v2).asList().get();
      assertThat(outer).hasSize(2);
      var firstMap = (Map) outer.getFirst().asMap().get();
      assertThat(firstMap).hasSize(2);
      assertThat(firstMap.get("a")).isEqualTo(Value.of(1));
      assertThat(firstMap.get("b")).isEqualTo(Value.of(2));
      var secondMap = (Map) outer.get(1).asMap().get();
      assertThat(secondMap.get("c")).isEqualTo(Value.of(3));
    }
  }

  // -- Per-element state preservation ---------------------------------------------------

  @Nested
  @DisplayName("Per-element state preservation")
  class StatePreservation {

    @ParameterizedTest(name = "[{0}] List with mixed states preserves per-element state")
    @MethodSource("dev.zems.lib.value.marshal.RecursiveValueRoundTripTest#formats")
    @JourneyTest(speakingId = "per-element-state-preservation", acceptance = "a1")
    void listWithStateMarkers(RoundTripFormatHarness.Format f) {
      var descriptor = TypeDescriptor.of(List.class, Integer.class);
      Value<List<Value<Integer>>> v = Value.listOf(
        List.of(Value.of(1), Value.nullValue(), Value.errorMessage("bad value", Integer.class), Value.of(4))
      );

      @SuppressWarnings({ "unchecked", "rawtypes" })
      Value<List<Value<Integer>>> v2 = (Value) roundTrip(f, (Value) v, (TypeDescriptor) descriptor);

      var elements = v2.asList().get();
      assertThat(elements).hasSize(4);
      assertThat(elements.get(0)).isInstanceOf(IntegerValue.class);
      assertThat(((IntegerValue) elements.get(0)).intValue()).isEqualTo(1);
      assertThat(elements.get(1)).isInstanceOf(NullValue.class);
      assertThat(elements.get(2)).isInstanceOf(ErrorValue.class);
      assertThat(elements.get(2).error()).get().extracting(Throwable::getMessage).isEqualTo("bad value");
      assertThat(elements.get(3)).isInstanceOf(IntegerValue.class);
      assertThat(((IntegerValue) elements.get(3)).intValue()).isEqualTo(4);
    }

    @ParameterizedTest(name = "[{0}] Map with mixed-state values preserves per-entry state")
    @MethodSource("dev.zems.lib.value.marshal.RecursiveValueRoundTripTest#formats")
    @JourneyTest(speakingId = "per-element-state-preservation", acceptance = "a2")
    void mapWithStateValues(RoundTripFormatHarness.Format f) {
      var descriptor = TypeDescriptor.of(Map.class, String.class, Integer.class);
      var entries = new LinkedHashMap<String, Value<Integer>>();
      entries.put("ok", Value.of(42));
      entries.put("missing", Value.nullValue());
      entries.put("broken", Value.errorMessage("oops", Integer.class));
      Value<Map<String, Value<Integer>>> v = Value.mapOf(entries);

      @SuppressWarnings({ "unchecked", "rawtypes" })
      Value<Map<String, Value<Integer>>> v2 = (Value) roundTrip(f, (Value) v, (TypeDescriptor) descriptor);

      var read = v2.asMap().get();
      assertThat(read.get("ok")).isInstanceOf(IntegerValue.class);
      assertThat(read.get("missing")).isInstanceOf(NullValue.class);
      assertThat(read.get("broken")).isInstanceOf(ErrorValue.class);
    }
  }

  // -- BoxedValue + scalar entry points -------------------------------------------------

  @Nested
  @DisplayName("BoxedValue + Value.of dispatch")
  class BoxedAndScalarEntries {

    @ParameterizedTest(name = "[{0}] BoxedValue<Point> direct round-trip")
    @MethodSource("dev.zems.lib.value.marshal.RecursiveValueRoundTripTest#formats")
    @JourneyTest(speakingId = "boxed-value-coverage-and-npe", acceptance = "a4")
    void boxedPointRoundTrip(RoundTripFormatHarness.Format f) {
      var descriptor = TypeDescriptor.of(Point.class);
      Value<Point> v = Value.of(new Point(7, 11));

      Value<Point> v2 = roundTrip(f, v, descriptor);

      assertThat(v2).isInstanceOf(BoxedValue.class);
      assertThat(((BoxedValue<Point>) v2).inner()).isEqualTo(new Point(7, 11));
    }
  }

  // -- Container-DoS regression ---------------------------------------------------------

  @Nested
  @DisplayName("Container-DoS regression")
  class ContainerDos {

    @Test
    @JourneyTest(speakingId = "container-dos-reader-bounds", acceptance = "a1")
    void unboundedListSizeIsRejectedBeforeAllocation() {
      // Hand-craft a binary payload claiming size = 100_000_000 but containing zero element bytes.
      // The outer record body length bounds the visible bytes — but the size field itself reaches
      // the descriptor before any element is read. The maxArrayLength check fires first.
      var listDesc = TypeDescriptor.of(List.class, Integer.class);
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().withWireConstraints(WireConstraints.UNCHECKED).binaryWriter(bos)) {
        w.writeRecord(0, "value", listDesc, List.of()); // empty
      }
      // We can't easily synthesise an oversized claim through the writer (it would actually allocate
      // the elements). Instead: assert the secure-default reader's maxArrayLength is enforced when
      // the wire claims a too-large size.
      var tightConstraints = WireConstraints.builder().maxArrayLength(10).build();
      var readProtocol = ValueIo.framed().withWireConstraints(tightConstraints);

      // Build a wire payload claiming size=11 (just past the limit).
      var oversizedBos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().withWireConstraints(WireConstraints.UNCHECKED).binaryWriter(oversizedBos)) {
        Supplier<List<Value<Integer>>> mkList = () -> {
          var l = new ArrayList<Value<Integer>>(11);
          for (int i = 0; i < 11; i++) {
            l.add(Value.of(i));
          }
          return l;
        };
        w.writeRecord(0, "value", listDesc, mkList.get());
      }

      try (var r = readProtocol.binaryReader(new ByteArrayInputStream(oversizedBos.toByteArray()))) {
        assertThatThrownBy(() -> r.read(listDesc))
          .isInstanceOf(WireConstraintViolationException.class)
          .hasMessageContaining("maxArrayLength");
      } catch (RuntimeException closeError) {
        // Body throw leaves the wire positionally inconsistent — close-time checksum/terminator
        // failure is downstream of the body throw.
      }
    }

    @Test
    @JourneyTest(speakingId = "container-dos-reader-bounds", acceptance = "a2")
    void unboundedMapEntriesIsRejectedBeforeAllocation() {
      var mapDesc = TypeDescriptor.of(Map.class, String.class, Integer.class);
      var tight = WireConstraints.builder().maxMapEntries(10).build();

      var oversizedBos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().withWireConstraints(WireConstraints.UNCHECKED).binaryWriter(oversizedBos)) {
        var m = new LinkedHashMap<String, Value<Integer>>();
        for (int i = 0; i < 11; i++) {
          m.put("k" + i, Value.of(i));
        }
        w.writeRecord(0, "value", mapDesc, m);
      }

      try (
        var r = ValueIo.framed()
          .withWireConstraints(tight)
          .binaryReader(new ByteArrayInputStream(oversizedBos.toByteArray()))
      ) {
        assertThatThrownBy(() -> r.read(mapDesc))
          .isInstanceOf(WireConstraintViolationException.class)
          .hasMessageContaining("maxMapEntries");
      } catch (RuntimeException closeError) {
        // see note above
      }
    }
  }
}
