package dev.zems.lib.value.marshal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.JourneyTest;
import dev.zems.lib.value.ErrorValue;
import dev.zems.lib.value.NullValue;
import dev.zems.lib.value.TombstoneValue;
import dev.zems.lib.value.UndefinedValue;
import dev.zems.lib.value.UnresolvedValue;
import dev.zems.lib.value.Value;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("ValueIo streaming records API")
class StreamingRecordsTest {

  @Nested
  @DisplayName("Binary streaming")
  class BinaryStreaming {

    @Test
    @JourneyTest(speakingId = "stream-api-binary", acceptance = "a1")
    void roundTripsThreeRecords() {
      var bos = new ByteArrayOutputStream();
      ValueIo.streaming().binaryWriteAll(
        bos,
        String.class,
        Stream.of(Value.of("first"), Value.of("second"), Value.of("third"))
      );
      try (var s = ValueIo.streaming().binaryRecords(new ByteArrayInputStream(bos.toByteArray()), String.class)) {
        var collected = s.map(v -> v.asString().orElseThrow()).toList();
        assertThat(collected).containsExactly("first", "second", "third");
      }
    }

    @Test
    @JourneyTest(speakingId = "record-spliterator-trysplit-safety", acceptance = "confined")
    @DisplayName("the records stream refuses to split, keeping wire decode single-threaded under .parallel()")
    void recordsStreamRefusesToSplit() {
      var bos = new ByteArrayOutputStream();
      ValueIo.streaming().binaryWriteAll(bos, String.class, Stream.of(Value.of("a"), Value.of("b"), Value.of("c")));
      try (var s = ValueIo.streaming().binaryRecords(new ByteArrayInputStream(bos.toByteArray()), String.class)) {
        Spliterator<Value<String>> spliterator = s.spliterator();
        assertThat(spliterator.trySplit()).isNull();
      }
    }

    @Test
    @JourneyTest(speakingId = "stream-api-binary", acceptance = "a2")
    void emptyStreamProducesNoRecords() {
      var bos = new ByteArrayOutputStream();
      ValueIo.streaming().binaryWriteAll(bos, String.class, Stream.empty());
      try (var s = ValueIo.streaming().binaryRecords(new ByteArrayInputStream(bos.toByteArray()), String.class)) {
        assertThat(s.count()).isEqualTo(0L);
      }
    }

    @Test
    @JourneyTest(speakingId = "stream-api-binary", acceptance = "a3")
    void singleRecordWithSeparator() {
      var bos = new ByteArrayOutputStream();
      ValueIo.streaming().binaryWriteAll(bos, String.class, Stream.of(Value.of("only")));
      try (var s = ValueIo.streaming().binaryRecords(new ByteArrayInputStream(bos.toByteArray()), String.class)) {
        var collected = s.map(v -> v.asString().orElseThrow()).toList();
        assertThat(collected).containsExactly("only");
      }
    }

    @Test
    @JourneyTest(speakingId = "stream-api-binary", acceptance = "a4")
    void mixedStatesRoundTrip() {
      var bos = new ByteArrayOutputStream();
      ValueIo.streaming().binaryWriteAll(
        bos,
        String.class,
        Stream.of(Value.of("x"), Value.nullValue(), Value.undefined(), Value.unresolved())
      );
      try (var s = ValueIo.streaming().binaryRecords(new ByteArrayInputStream(bos.toByteArray()), String.class)) {
        var collected = s.toList();
        assertThat(collected).hasSize(4);
        assertThat(collected.get(0).asString()).contains("x");
        assertThat(collected.get(1).isNotNull()).isFalse();
        assertThat(collected.get(2).isDefined()).isFalse();
        assertThat(collected.get(3).isResolved()).isFalse();
      }
    }

    @Test
    @JourneyTest(speakingId = "framed-streaming-mode-validation", acceptance = "a4")
    void parallelDoesNotBreakOrdering() {
      var bos = new ByteArrayOutputStream();
      ValueIo.streaming().binaryWriteAll(bos, String.class, Stream.of(Value.of("a"), Value.of("b"), Value.of("c")));
      try (
        var s = ValueIo.streaming().binaryRecords(new ByteArrayInputStream(bos.toByteArray()), String.class).parallel()
      ) {
        var collected = s.map(v -> v.asString().orElseThrow()).toList();
        assertThat(collected).containsExactly("a", "b", "c");
      }
    }

    @Test
    @JourneyTest(speakingId = "stream-api-binary", acceptance = "a5")
    void usingTypeVerificationRoundTrips() {
      var bos = new ByteArrayOutputStream();
      ValueIo.streaming()
        .usingTypeVerification()
        .binaryWriteAll(bos, String.class, Stream.of(Value.of("a"), Value.of("b")));
      try (
        var s = ValueIo.streaming()
          .usingTypeVerification()
          .binaryRecords(new ByteArrayInputStream(bos.toByteArray()), String.class)
      ) {
        assertThat(s.map(v -> v.asString().orElseThrow()).toList()).containsExactly("a", "b");
      }
    }
  }

  @Nested
  @DisplayName("JSON streaming (JSONL)")
  class JsonStreaming {

    @Test
    @JourneyTest(speakingId = "stream-api-json", acceptance = "a1")
    void roundTripsThreeRecords() {
      var sw = new StringWriter();
      ValueIo.streaming().jsonWriteAll(
        sw,
        String.class,
        Stream.of(Value.of("first"), Value.of("second"), Value.of("third"))
      );
      try (var s = ValueIo.streaming().jsonRecords(new StringReader(sw.toString()), String.class)) {
        assertThat(s.map(v -> v.asString().orElseThrow()).toList()).containsExactly("first", "second", "third");
      }
    }

    @Test
    @JourneyTest(speakingId = "stream-api-json", acceptance = "a2")
    void wireShapeIsNDJSON() {
      var sw = new StringWriter();
      ValueIo.streaming().jsonWriteAll(sw, String.class, Stream.of(Value.of("a"), Value.of("b")));
      var produced = sw.toString();
      // Two complete JSON objects separated by '\n', each terminated by '\n'. No surrounding array.
      // Outer "$payload" is the envelope-level reserved slot; inner "__slot0" is the String
      // descriptor's body slot under the id-only wire encoding (user slot names never reach the wire).
      assertThat(produced).isEqualTo("{\"$payload\":{\"__slot0\":\"a\"}}\n{\"$payload\":{\"__slot0\":\"b\"}}\n");
    }

    @Test
    @JourneyTest(speakingId = "stream-api-json", acceptance = "a3")
    void emptyStream() {
      var sw = new StringWriter();
      ValueIo.streaming().jsonWriteAll(sw, String.class, Stream.empty());
      assertThat(sw.toString()).isEmpty();
      try (var s = ValueIo.streaming().jsonRecords(new StringReader(sw.toString()), String.class)) {
        assertThat(s.count()).isEqualTo(0L);
      }
    }

    @Test
    @JourneyTest(speakingId = "stream-api-json", acceptance = "a4")
    void mixedStatesRoundTrip() {
      var sw = new StringWriter();
      ValueIo.streaming().jsonWriteAll(
        sw,
        String.class,
        Stream.of(Value.of("x"), Value.nullValue(), Value.undefined())
      );
      try (var s = ValueIo.streaming().jsonRecords(new StringReader(sw.toString()), String.class)) {
        var collected = s.toList();
        assertThat(collected).hasSize(3);
        assertThat(collected.get(0).asString()).contains("x");
        assertThat(collected.get(1).isNotNull()).isFalse();
        assertThat(collected.get(2).isDefined()).isFalse();
      }
    }
  }

  @Nested
  @DisplayName("Mode and configuration validation")
  class Validation {

    @Test
    @JourneyTest(speakingId = "framed-streaming-mode-validation", acceptance = "a1")
    void framedRequiresExactlyOneRecord_zero() {
      var bos = new ByteArrayOutputStream();
      assertThatThrownBy(() -> ValueIo.framed().binaryWriteAll(bos, String.class, Stream.empty()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("got 0");
    }

    @Test
    @JourneyTest(speakingId = "framed-streaming-mode-validation", acceptance = "a2")
    void framedRequiresExactlyOneRecord_two() {
      var bos = new ByteArrayOutputStream();
      assertThatThrownBy(() ->
        ValueIo.framed().binaryWriteAll(bos, String.class, Stream.of(Value.of("a"), Value.of("b")))
      )
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("got 2");
    }

    @Test
    @JourneyTest(speakingId = "framed-streaming-mode-validation", acceptance = "a3")
    void checksumOnStreamingThrows() {
      assertThatThrownBy(() -> ValueIo.streaming().withChecksum(ChecksumAlgorithm.SHA_256))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Checksum is not supported in STREAMING");
    }
  }

  @Nested
  @DisplayName("Framed Stream API (size-1 stream)")
  class FramedStreamApi {

    @Test
    @JourneyTest(speakingId = "framed-stream-api-roundtrips", acceptance = "a1")
    void framedBinaryWriteAllProducesSizeOneStream() {
      var bos = new ByteArrayOutputStream();
      ValueIo.framed().binaryWriteAll(bos, String.class, Stream.of(Value.of("only")));
      try (var s = ValueIo.framed().binaryRecords(new ByteArrayInputStream(bos.toByteArray()), String.class)) {
        var collected = s.toList();
        assertThat(collected).hasSize(1);
        assertThat(collected.getFirst().asString()).contains("only");
      }
    }

    @Test
    @JourneyTest(speakingId = "framed-stream-api-roundtrips", acceptance = "a2")
    void framedJsonRoundTripViaStreamApi() {
      var sw = new StringWriter();
      ValueIo.framed().jsonWriteAll(sw, String.class, Stream.of(Value.of("hello")));
      try (var s = ValueIo.framed().jsonRecords(new StringReader(sw.toString()), String.class)) {
        assertThat(s.map(v -> v.asString().orElseThrow()).toList()).containsExactly("hello");
      }
    }

    @Test
    @JourneyTest(speakingId = "framed-stream-api-roundtrips", acceptance = "a3")
    void pipelineInteropFramedAndStreaming() {
      // Same Stream<Value<String>> pipeline works for both modes.
      var framedBos = new ByteArrayOutputStream();
      ValueIo.framed().binaryWriteAll(framedBos, String.class, Stream.of(Value.of("framed")));

      var streamingBos = new ByteArrayOutputStream();
      ValueIo.streaming().binaryWriteAll(
        streamingBos,
        String.class,
        Stream.of(Value.of("a"), Value.of("b"), Value.of("c"))
      );

      List<String> framedResult;
      try (var s = ValueIo.framed().binaryRecords(new ByteArrayInputStream(framedBos.toByteArray()), String.class)) {
        framedResult = s.map(v -> v.asString().orElseThrow()).toList();
      }
      List<String> streamingResult;
      try (
        var s = ValueIo.streaming().binaryRecords(new ByteArrayInputStream(streamingBos.toByteArray()), String.class)
      ) {
        streamingResult = s.map(v -> v.asString().orElseThrow()).toList();
      }
      assertThat(framedResult).containsExactly("framed");
      assertThat(streamingResult).containsExactly("a", "b", "c");
    }

    @Test
    @JourneyTest(speakingId = "framed-stream-api-roundtrips", acceptance = "a4")
    void framedChecksumStillWorksThroughStreamApi() {
      var bos = new ByteArrayOutputStream();
      ValueIo.framed()
        .withChecksum(ChecksumAlgorithm.SHA_256)
        .binaryWriteAll(bos, String.class, Stream.of(Value.of("payload")));
      try (
        var s = ValueIo.framed()
          .withChecksum(ChecksumAlgorithm.SHA_256)
          .binaryRecords(new ByteArrayInputStream(bos.toByteArray()), String.class)
      ) {
        assertThat(s.map(v -> v.asString().orElseThrow()).toList()).containsExactly("payload");
      }
    }
  }

  /**
   * Pins the wire shape and round-trip semantics for top-level state markers in both FRAMED and STREAMING modes across
   * both formats. Specifically covers the asymmetry called out in {@code 99_doc/what-next-with-value.md} issues #3 and
   * #4 — STREAMING wraps each top-level state marker in its own self-contained line via the {@code topLevelStreaming}
   * branch of {@code JsonStateWriter.writeStateMarker}, while FRAMED writes inside the already-open envelope.
   */
  @Nested
  @DisplayName("Top-level state markers")
  class TopLevelStateMarkers {

    static Stream<Arguments> nonErrorStates() {
      return Stream.of(
        Arguments.of(Value.nullValue(), "NULL"),
        Arguments.of(Value.undefined(), "UNDEFINED"),
        Arguments.of(Value.unresolved(), "UNRESOLVED"),
        Arguments.of(Value.tombstone(), "TOMBSTONE")
      );
    }

    private record MatrixEntry(String name, Value<String> value, Class<?> expected) {}

    static Stream<Arguments> roundTripMatrix() {
      var entries = List.of(
        new MatrixEntry("NULL", Value.nullValue(), NullValue.class),
        new MatrixEntry("UNDEFINED", Value.undefined(), UndefinedValue.class),
        new MatrixEntry("UNRESOLVED", Value.unresolved(), UnresolvedValue.class),
        new MatrixEntry("ERROR", Value.errorMessage("oops", String.class), ErrorValue.class),
        new MatrixEntry("TOMBSTONE", Value.tombstone(), TombstoneValue.class)
      );
      var result = new ArrayList<Arguments>();
      for (var e : entries) {
        for (var mode : List.of("FRAMED", "STREAMING")) {
          for (var format : List.of("binary", "JSON")) {
            result.add(Arguments.of(e.name(), mode, format, e.value(), e.expected()));
          }
        }
      }
      return result.stream();
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("nonErrorStates")
    @JourneyTest(speakingId = "json-streaming-state-marker-top-level", acceptance = "a2")
    void jsonStreamingWireShape(Value<String> marker, String stateName) {
      var sw = new StringWriter();
      ValueIo.streaming().jsonWriteAll(sw, String.class, Stream.of(marker));
      assertThat(sw.toString()).isEqualTo("{\"$payload\":{\"__state\":\"" + stateName + "\"}}\n");
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("nonErrorStates")
    @JourneyTest(speakingId = "json-streaming-state-marker-top-level", acceptance = "a2")
    void jsonFramedWireShape(Value<String> marker, String stateName) {
      var sw = new StringWriter();
      ValueIo.framed().jsonWriteAll(sw, String.class, Stream.of(marker));
      var wire = sw.toString();
      // FRAMED keeps the state marker at the payload slot inside the open envelope — no
      // per-record wrapper, no JSONL separator.
      assertThat(wire)
        .contains("\"$payload\":{\"__state\":\"" + stateName + "\"}")
        .doesNotContain("\n");
    }

    @ParameterizedTest(name = "{0} via {1} {2}")
    @MethodSource("roundTripMatrix")
    @JourneyTest(speakingId = "json-streaming-state-marker-top-level", acceptance = "a1")
    void topLevelStateMarkerRoundTrip(
      String stateName,
      String mode,
      String format,
      Value<String> input,
      Class<?> expectedType
    ) {
      byte[] bytes = writeOne(input, mode, format);
      Value<String> recovered = readOne(bytes, mode, format);
      assertThat(recovered).isInstanceOf(expectedType);
    }

    private byte[] writeOne(Value<String> v, String mode, String format) {
      if ("binary".equals(format)) {
        var bos = new ByteArrayOutputStream();
        if ("FRAMED".equals(mode)) {
          ValueIo.framed().binaryWriteAll(bos, String.class, Stream.of(v));
        } else {
          ValueIo.streaming().binaryWriteAll(bos, String.class, Stream.of(v));
        }
        return bos.toByteArray();
      }
      var sw = new StringWriter();
      if ("FRAMED".equals(mode)) {
        ValueIo.framed().jsonWriteAll(sw, String.class, Stream.of(v));
      } else {
        ValueIo.streaming().jsonWriteAll(sw, String.class, Stream.of(v));
      }
      return sw.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Value<String> readOne(byte[] bytes, String mode, String format) {
      if ("binary".equals(format)) {
        var stream = "FRAMED".equals(mode)
          ? ValueIo.framed().binaryRecords(new ByteArrayInputStream(bytes), String.class)
          : ValueIo.streaming().binaryRecords(new ByteArrayInputStream(bytes), String.class);
        try (stream) {
          return stream.findFirst().orElseThrow();
        }
      }
      var reader = new StringReader(new String(bytes, StandardCharsets.UTF_8));
      var stream = "FRAMED".equals(mode)
        ? ValueIo.framed().jsonRecords(reader, String.class)
        : ValueIo.streaming().jsonRecords(reader, String.class);
      try (stream) {
        return stream.findFirst().orElseThrow();
      }
    }

    @Test
    @JourneyTest(speakingId = "json-streaming-state-marker-top-level", acceptance = "a1")
    void errorStateRoundTripPreservesMessageStreamingJson() {
      var sw = new StringWriter();
      ValueIo.streaming().jsonWriteAll(sw, String.class, Stream.of(Value.errorMessage("boom", String.class)));
      try (var s = ValueIo.streaming().jsonRecords(new StringReader(sw.toString()), String.class)) {
        var collected = s.toList();
        assertThat(collected).hasSize(1);
        var recovered = collected.getFirst();
        assertThat(recovered)
          .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(ErrorValue.class))
          .satisfies(err -> {
            assertThat(err.throwable().getMessage()).contains("boom");
            assertThat(err.expectedType().qualifiedName()).isEqualTo("java.lang.String");
          });
      }
    }
  }
}
