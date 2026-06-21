package dev.zems.lib.value.marshal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Contract test for {@code Protocol.V1} as the envelope contract — specifically the custom-format extension SPI
 * ({@link Protocol#writer(Protocol.WriterFactory)}) and the {@link AbstractStateWriter} mode guard. The public I/O
 * surface is exercised through {@link ValueIo} in {@code ValueIoTest}.
 */
@ContractTest
@DisplayName("Protocol.V1")
class ProtocolV1Test {

  /** Custom format declaring STREAMING-only. Used by SupportedModesGuard tests. */
  private static final class StreamingOnlyWriter extends AbstractStateWriter {

    private static final Set<Protocol.Mode> SUPPORTED_MODES = Set.of(Protocol.Mode.STREAMING);

    StreamingOnlyWriter(Protocol protocol) {
      super(protocol, SUPPORTED_MODES);
    }

    @Override
    protected void doWriteRecordOpen(int id, String name, TypeDescriptor<?> descriptor) {}

    @Override
    protected void doWriteRecordClose(int id, String name, TypeDescriptor<?> descriptor) {}

    @Override
    protected void doWriteRecordSeparator() {}

    @Override
    protected void doWriteTombstone(int id, String name) {}

    @Override
    protected void doWriteError(int id, String name, Throwable throwable) {}

    @Override
    protected void doWriteUnresolved(int id, String name) {}

    @Override
    protected void doWriteUndefined(int id, String name) {}

    @Override
    protected void doWriteNull(int id, String name) {}

    @Override
    protected void doEndNested(int id, String name) {}

    @Override
    protected void doBeginNested(int id, String name) {}

    @Override
    protected void doWriteBytes(int id, String name, byte[] value) {}

    @Override
    protected void doWriteString(int id, String name, String value) {}

    @Override
    protected void doWriteDouble(int id, String name, double value) {}

    @Override
    protected void doWriteFloat(int id, String name, float value) {}

    @Override
    protected void doWriteLong(int id, String name, long value) {}

    @Override
    protected void doWriteInt(int id, String name, int value) {}

    @Override
    protected void doWriteShort(int id, String name, short value) {}

    @Override
    protected void doWriteChar(int id, String name, char value) {}

    @Override
    protected void doWriteBoolean(int id, String name, boolean value) {}
  }

  @Nested
  @DisplayName("Format-level supportedModes guard")
  class SupportedModesGuard {

    @Test
    void customStreamingOnlyWriterRejectsFramedProtocol() {
      // No try-with-resources: writer() throws before returning, so there's no Closeable to track.
      assertThatThrownBy(() -> Protocol.V1.framed().writer(StreamingOnlyWriter::new))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("StreamingOnlyWriter")
        .hasMessageContaining("FRAMED");
    }

    @Test
    void customStreamingOnlyWriterAcceptsStreamingProtocol() {
      try (var w = Protocol.V1.streaming().writer(StreamingOnlyWriter::new)) {
        assertThat(w.supportedModes()).containsExactly(Protocol.Mode.STREAMING);
      }
    }
  }
}
