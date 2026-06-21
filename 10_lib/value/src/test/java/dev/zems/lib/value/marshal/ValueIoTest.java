package dev.zems.lib.value.marshal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("ValueIo")
class ValueIoTest {

  @Nested
  @DisplayName("Reader assertions")
  class ReaderAssertion {

    @Test
    void readerWithoutChecksumThrows_writerHadChecksum() {
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().withChecksum(ChecksumAlgorithm.SHA_256).binaryWriter(bos)) {
        w.write(Value.of("payload"), TypeDescriptor.of(String.class));
      }
      assertThatThrownBy(() -> ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("checksum");
    }

    @Test
    void typeVerificationMismatchThrows() {
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().binaryWriter(bos)) {
        w.write(Value.of("hello"), TypeDescriptor.of(String.class));
      }
      // Writer omitted typeVerification (default off); reader requires on → mismatch.
      assertThatThrownBy(() ->
        ValueIo.framed().usingTypeVerification().binaryReader(new ByteArrayInputStream(bos.toByteArray()))
      )
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("typeVerification");
    }

    @Test
    void framedReaderOnStreamingWriterFailsToDecodeHeader() {
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.streaming().binaryWriter(bos)) {
        w.write(Value.of("hello"), TypeDescriptor.of(String.class));
      }
      assertThatThrownBy(() -> ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))).isInstanceOf(
        Exception.class
      );
    }
  }

  @Nested
  @DisplayName("Streaming + checksum mismatch rejection")
  class StreamingMismatch {

    @Test
    void streamingWithChecksumIsRejected() {
      assertThatThrownBy(() -> ValueIo.streaming().withChecksum(ChecksumAlgorithm.SHA_256))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Checksum is not supported in STREAMING");
    }
  }

  @Nested
  @DisplayName("WireConstraints validation")
  class WireConstraintsGuard {

    @Test
    void framedWithWireConstraintsNullIsRejected() {
      assertThatThrownBy(() -> ValueIo.framed().withWireConstraints(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void streamingWithWireConstraintsNullIsRejected() {
      assertThatThrownBy(() -> ValueIo.streaming().withWireConstraints(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("typeVerification chain returns a configured stage")
  class TypeVerification {

    @Test
    void framedUsingTypeVerificationProducesWorkingWriter() {
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().usingTypeVerification().binaryWriter(bos)) {
        w.write(Value.of("x"), TypeDescriptor.of(String.class));
      }
      assertThat(bos.toByteArray()).isNotEmpty();
    }

    @Test
    void streamingUsingTypeVerificationProducesWorkingWriter() {
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.streaming().usingTypeVerification().binaryWriter(bos)) {
        w.write(Value.of("x"), TypeDescriptor.of(String.class));
      }
      assertThat(bos.toByteArray()).isNotEmpty();
    }
  }
}
