package dev.zems.lib.value.marshal.format.binary;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.ContractTest;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contract test for {@link StagedWriteCursor} — channel-backed writer with a small staging buffer that flushes on
 * demand. Pins flush triggering, primitive write correctness, and channel ownership on close.
 */
@ContractTest
@DisplayName("StagedWriteCursor")
class StagedWriteCursorTest {

  // Min staging size is 64 bytes. Use STAGING=64 to force flushes on payloads > 64 bytes.
  private static final int STAGING = 64;

  private static Sink sink() {
    var bos = new ByteArrayOutputStream();
    return new Sink(bos, Channels.newChannel(bos));
  }

  @Test
  @DisplayName("byte-by-byte write across flushes lands in the underlying channel")
  void byteWriteAcrossFlushes() {
    int n = STAGING * 3 + 5;
    var s = sink();
    var c = SegmentCursors.stagedWriter(s.channel(), STAGING, true);
    for (int i = 0; i < n; i++) {
      c.put((byte) i);
    }
    c.close();
    var written = s.bos().toByteArray();
    assertThat(written).hasSize(n);
    for (int i = 0; i < n; i++) {
      assertThat(written[i]).isEqualTo((byte) i);
    }
  }

  @Test
  @DisplayName("putInt straddling staging boundary flushes correctly")
  void putIntAcrossFlush() {
    var s = sink();
    var c = SegmentCursors.stagedWriter(s.channel(), STAGING, true);
    // Write STAGING-1 zero bytes, then putInt — int spans two staging fills.
    for (int i = 0; i < STAGING - 1; i++) {
      c.put((byte) 0);
    }
    c.putInt(0x01020304);
    c.close();

    var read = ByteBuffer.wrap(s.bos().toByteArray()).order(ByteOrder.BIG_ENDIAN);
    read.position(STAGING - 1);
    assertThat(read.getInt()).isEqualTo(0x01020304);
  }

  @Test
  @DisplayName("bulk put larger than staging flushes repeatedly")
  void bulkPutAcrossFlushes() {
    int n = STAGING * 4;
    var src = new byte[n];
    for (int i = 0; i < n; i++) {
      src[i] = (byte) i;
    }
    var s = sink();
    var c = SegmentCursors.stagedWriter(s.channel(), STAGING, true);
    c.put(src, 0, n);
    c.close();
    assertThat(s.bos().toByteArray()).isEqualTo(src);
  }

  @Test
  @DisplayName("flush() commits buffered bytes to the channel without closing")
  void flushCommitsWithoutClose() {
    var s = sink();
    var c = SegmentCursors.stagedWriter(s.channel(), STAGING, true);
    c.put((byte) 1);
    c.put((byte) 2);
    c.flush();
    assertThat(s.bos().toByteArray()).containsExactly((byte) 1, (byte) 2);
    c.close();
  }

  @Test
  @DisplayName("primitive accessors land BIG_ENDIAN")
  void primitiveAccessorsBigEndian() {
    var s = sink();
    var c = SegmentCursors.stagedWriter(s.channel(), STAGING, true);
    c.putLong(0x0102030405060708L);
    c.close();
    var read = ByteBuffer.wrap(s.bos().toByteArray()).order(ByteOrder.BIG_ENDIAN);
    assertThat(read.getLong()).isEqualTo(0x0102030405060708L);
  }

  @Test
  @DisplayName("position() reflects bytes committed so far")
  void positionTracksTotalBytes() {
    var s = sink();
    var c = SegmentCursors.stagedWriter(s.channel(), STAGING, true);
    c.put(new byte[STAGING + 7], 0, STAGING + 7);
    assertThat(c.position()).isEqualTo(STAGING + 7);
    c.close();
  }

  private record Sink(ByteArrayOutputStream bos, WritableByteChannel channel) {}
}
