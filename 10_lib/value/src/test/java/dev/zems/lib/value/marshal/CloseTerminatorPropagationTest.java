package dev.zems.lib.value.marshal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.JourneyTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AbstractStateReader.close() — terminator-error propagation")
class CloseTerminatorPropagationTest {

  private static final String FRAME = "ct";

  @Test
  @DisplayName("clean read + clean close: no exception")
  @JourneyTest(speakingId = "close-terminator-propagation", acceptance = "a1")
  void cleanCloseSucceeds() {
    var bos = new ByteArrayOutputStream();
    try (var w = ValueIo.framed().withChecksum(ChecksumAlgorithm.SHA_256).binaryWriter(bos)) {
      w.write(Value.of("hello"), TypeDescriptor.of(String.class));
    }

    assertThatCode(() -> {
      try (
        var r = ValueIo.framed()
          .withChecksum(ChecksumAlgorithm.SHA_256)
          .binaryReader(new ByteArrayInputStream(bos.toByteArray()))
      ) {
        r.read(String.class);
      }
    }).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("corrupted terminator surfaces from close()")
  @JourneyTest(speakingId = "close-terminator-propagation", acceptance = "a2")
  void corruptedTerminatorPropagates() {
    var bos = new ByteArrayOutputStream();
    try (var w = ValueIo.framed().withChecksum(ChecksumAlgorithm.SHA_256).binaryWriter(bos)) {
      w.write(Value.of("hello"), TypeDescriptor.of(String.class));
    }
    var bytes = bos.toByteArray();
    // Flip the last byte (inside the terminator's checksum field) to force a mismatch.
    bytes[bytes.length - 1] ^= 0x01;

    assertThatThrownBy(() -> {
      try (
        var r = ValueIo.framed().withChecksum(ChecksumAlgorithm.SHA_256).binaryReader(new ByteArrayInputStream(bytes))
      ) {
        r.read(String.class);
      }
    })
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Checksum mismatch");
  }

  @Test
  @DisplayName("body-throw + close-throw: try-with-resources preserves body via getSuppressed")
  @JourneyTest(speakingId = "close-terminator-propagation", acceptance = "a3")
  void bodyThrowAndCloseThrow_addSuppressed() {
    var bos = new ByteArrayOutputStream();
    try (var w = ValueIo.framed().withChecksum(ChecksumAlgorithm.SHA_256).binaryWriter(bos)) {
      w.write(Value.of("hello"), TypeDescriptor.of(String.class));
    }
    var bytes = bos.toByteArray();
    bytes[bytes.length - 1] ^= 0x01; // corrupt terminator

    var planted = new RuntimeException("body planted");

    Throwable thrown;
    try (
      var r = ValueIo.framed().withChecksum(ChecksumAlgorithm.SHA_256).binaryReader(new ByteArrayInputStream(bytes))
    ) {
      // We did not consume any wire payload — close() will hit the corrupted terminator.
      throw planted;
    } catch (Throwable t) {
      thrown = t;
    }

    // The body's planted exception is the primary; the terminator failure is added as suppressed.
    assertThat(thrown).isSameAs(planted);
    assertThat(thrown.getSuppressed()).hasSize(1);
    assertThat(thrown.getSuppressed()[0]).isInstanceOf(IllegalStateException.class);
  }
}
