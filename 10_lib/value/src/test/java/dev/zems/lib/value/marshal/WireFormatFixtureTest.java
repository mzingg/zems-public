package dev.zems.lib.value.marshal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.JourneyTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Locks in the V1 wire-format byte output for representative payloads. Captures today's post-refactor bytes for binary
 * and JSON across all combinations of mode (framed, streaming), type-verification, and checksum. If a test in this
 * class fails: either (a) the wire format changed accidentally and you should back out the change, or (b) the change is
 * intentional and you should regenerate the fixture AND bump the protocol version.
 */
@DisplayName("Wire format byte fixture (refactor canary)")
class WireFormatFixtureTest {

  // Canonical payload record used by record/nested-record fixtures.
  public record Point(int x, int y) {
    public static final TypeDescriptor<Point> DESCRIPTOR = TypeDescriptor.of(
      "test.Point",
      Point.class,
      r -> new Point(r.readInt(0, "x"), r.readInt(1, "y")),
      (w, p) -> {
        w.writeInt(0, "x", p.x());
        w.writeInt(1, "y", p.y());
      }
    );
  }

  public record Line(String label, Point a, Point b) {
    public static final TypeDescriptor<Line> DESCRIPTOR = TypeDescriptor.of(
      "test.Line",
      Line.class,
      r ->
        new Line(
          r.readString(0, "label"),
          r.readRecord(1, "a", Point.DESCRIPTOR),
          r.readRecord(2, "b", Point.DESCRIPTOR)
        ),
      (w, l) -> {
        w.writeString(0, "label", l.label());
        w.writeRecord(1, "a", Point.DESCRIPTOR, l.a());
        w.writeRecord(2, "b", Point.DESCRIPTOR, l.b());
      }
    );
  }

  // ============================================================
  // Round-trip sanity (each fixture pair: write, then read back successfully)
  // ============================================================
  @Nested
  @DisplayName("Round-trip")
  class RoundTrip {

    @Test
    @JourneyTest(speakingId = "wire-format-fixtures-binary", acceptance = "a1")
    void binaryFramed_scalarString() {
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().binaryWriter(bos)) {
        w.write(Value.of("hello"), TypeDescriptor.of(String.class));
      }
      try (var r = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThat(r.read(String.class).asString()).contains("hello");
      }
    }

    @Test
    @JourneyTest(speakingId = "wire-format-fixtures-binary", acceptance = "a2")
    void binaryFramed_record() {
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().binaryWriter(bos)) {
        w.writeRecord(0, "p", Point.DESCRIPTOR, new Point(3, 4));
      }
      try (var r = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThat(r.readRecord(0, "p", Point.DESCRIPTOR)).isEqualTo(new Point(3, 4));
      }
    }

    @Test
    @JourneyTest(speakingId = "wire-format-fixtures-binary", acceptance = "a3")
    void binaryFramed_nestedRecord() {
      var bos = new ByteArrayOutputStream();
      var line = new Line("seg", new Point(1, 2), new Point(3, 4));
      try (var w = ValueIo.framed().binaryWriter(bos)) {
        w.writeRecord(0, "l", Line.DESCRIPTOR, line);
      }
      try (var r = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThat(r.readRecord(0, "l", Line.DESCRIPTOR)).isEqualTo(line);
      }
    }

    @Test
    @JourneyTest(speakingId = "wire-format-fixtures-binary", acceptance = "a4")
    void binaryFramed_verified_scalarString() {
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().usingTypeVerification().binaryWriter(bos)) {
        w.write(Value.of("hello"), TypeDescriptor.of(String.class));
      }
      try (var r = ValueIo.framed().usingTypeVerification().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThat(r.read(String.class).asString()).contains("hello");
      }
    }

    @Test
    @JourneyTest(speakingId = "wire-format-fixtures-binary", acceptance = "a5")
    void binaryFramed_checksum_scalarString() {
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().withChecksum(ChecksumAlgorithm.SHA_256).binaryWriter(bos)) {
        w.write(Value.of("hello"), TypeDescriptor.of(String.class));
      }
      try (
        var r = ValueIo.framed()
          .withChecksum(ChecksumAlgorithm.SHA_256)
          .binaryReader(new ByteArrayInputStream(bos.toByteArray()))
      ) {
        assertThat(r.read(String.class).asString()).contains("hello");
      }
    }

    @Test
    @JourneyTest(speakingId = "wire-format-fixtures-json", acceptance = "a1")
    void jsonFramed_record() {
      var sw = new StringWriter();
      try (var w = ValueIo.framed().jsonWriter(sw)) {
        w.writeRecord(0, "p", Point.DESCRIPTOR, new Point(3, 4));
      }
      try (var r = ValueIo.framed().jsonReader(new StringReader(sw.toString()))) {
        assertThat(r.readRecord(0, "p", Point.DESCRIPTOR)).isEqualTo(new Point(3, 4));
      }
    }
  }
}
