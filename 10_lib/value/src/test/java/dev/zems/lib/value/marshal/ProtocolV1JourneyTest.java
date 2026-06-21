package dev.zems.lib.value.marshal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.JourneyTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Protocol.V1 — envelope round-trips")
class ProtocolV1JourneyTest {

  @Nested
  @DisplayName("Framed envelope round-trip")
  class FramedEnvelope {

    @Test
    @JourneyTest(speakingId = "protocol-v1-envelope-modes", acceptance = "a1")
    void roundTripsWithoutChecksum() {
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().binaryWriter(bos)) {
        w.write(Value.of("hello"), TypeDescriptor.of(String.class));
      }
      try (var r = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        Value<String> v = r.read(String.class);
        assertThat(v.asString()).contains("hello");
      }
    }
  }

  @Nested
  @DisplayName("Checksum")
  class WithChecksum {

    @Test
    @JourneyTest(speakingId = "protocol-v1-envelope-modes", acceptance = "a2")
    void sha256RoundTripsCleanly() {
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().withChecksum(ChecksumAlgorithm.SHA_256).binaryWriter(bos)) {
        w.write(Value.of("payload"), TypeDescriptor.of(String.class));
      }
      try (
        var r = ValueIo.framed()
          .withChecksum(ChecksumAlgorithm.SHA_256)
          .binaryReader(new ByteArrayInputStream(bos.toByteArray()))
      ) {
        Value<String> v = r.read(String.class);
        assertThat(v.asString()).contains("payload");
      }
    }
  }

  @Nested
  @DisplayName("Streaming")
  class Streaming {

    @Test
    @JourneyTest(speakingId = "protocol-v1-envelope-modes", acceptance = "a3")
    void streamingRoundTripsMultipleRecords() {
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.streaming().binaryWriter(bos)) {
        w.write(Value.of("first"), TypeDescriptor.of(String.class));
        w.write(Value.of("second"), TypeDescriptor.of(String.class));
        w.write(Value.of("third"), TypeDescriptor.of(String.class));
      }
      try (var r = ValueIo.streaming().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThat(r.read(String.class).asString()).contains("first");
        r.consumeRecordSeparator();
        assertThat(r.read(String.class).asString()).contains("second");
        r.consumeRecordSeparator();
        assertThat(r.read(String.class).asString()).contains("third");
        r.consumeRecordSeparator();
        assertThat(r.hasMoreRecords()).isFalse();
      }
    }
  }
}
