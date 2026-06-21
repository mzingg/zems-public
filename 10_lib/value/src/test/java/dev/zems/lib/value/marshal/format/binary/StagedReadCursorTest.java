package dev.zems.lib.value.marshal.format.binary;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.ContractTest;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contract test for {@link StagedReadCursor} — channel-backed reader with a small staging buffer that refills from the
 * channel on demand. Pins refill triggering across boundaries and primitive read correctness when payload spans
 * multiple staging fills.
 */
@ContractTest
@DisplayName("StagedReadCursor")
class StagedReadCursorTest {

  // Min staging size is 64 bytes. Tests use 64 (the minimum) to force boundary
  // crossings on payloads larger than 64 bytes.
  private static final int STAGING = 64;

  /** Build a staged reader over the byte array. */
  private static SegmentReadCursor stagedOver(byte[] bytes) {
    return SegmentCursors.stagedReader(Channels.newChannel(new ByteArrayInputStream(bytes)), STAGING, true);
  }

  @Test
  @DisplayName("byte-by-byte read across multiple refills (payload > staging)")
  void byteReadAcrossRefills() {
    int n = STAGING * 3 + 5;
    var src = new byte[n];
    for (int i = 0; i < n; i++) {
      src[i] = (byte) i;
    }
    var c = stagedOver(src);
    for (int i = 0; i < n; i++) {
      assertThat(c.get()).isEqualTo((byte) i);
    }
  }

  @Test
  @DisplayName("getInt straddling staging boundary refills correctly")
  void getIntAcrossRefill() {
    // Place an Int such that 1 byte sits in the first refill, 3 bytes in the second.
    var buf = ByteBuffer.allocate(STAGING + 4).order(ByteOrder.BIG_ENDIAN);
    for (int i = 0; i < STAGING - 1; i++) {
      buf.put((byte) 0);
    }
    buf.putInt(0x01020304);
    var c = stagedOver(buf.array());
    c.skip(STAGING - 1);
    assertThat(c.getInt()).isEqualTo(0x01020304);
  }

  @Test
  @DisplayName("getLong straddling refills")
  void getLongAcrossRefill() {
    var buf = ByteBuffer.allocate(STAGING + 8).order(ByteOrder.BIG_ENDIAN);
    for (int i = 0; i < STAGING - 1; i++) {
      buf.put((byte) 0);
    }
    buf.putLong(0x0102030405060708L);
    var c = stagedOver(buf.array());
    c.skip(STAGING - 1);
    assertThat(c.getLong()).isEqualTo(0x0102030405060708L);
  }

  @Test
  @DisplayName("bulk read larger than staging refills repeatedly")
  void bulkReadAcrossRefills() {
    int n = STAGING * 4;
    var src = new byte[n];
    for (int i = 0; i < n; i++) {
      src[i] = (byte) i;
    }
    var c = stagedOver(src);
    var dst = new byte[n];
    c.get(dst, 0, n);
    assertThat(dst).isEqualTo(src);
  }

  @Test
  @DisplayName("position() reflects total bytes read across refills")
  void positionTracksTotalBytes() {
    int n = STAGING * 2;
    var src = new byte[n];
    for (int i = 0; i < n; i++) {
      src[i] = (byte) i;
    }
    var c = stagedOver(src);
    c.skip(STAGING + 7);
    assertThat(c.position()).isEqualTo(STAGING + 7);
    assertThat(c.get()).isEqualTo((byte) (STAGING + 7));
  }

  @Test
  @DisplayName("close() does not throw when ownsChannel=true")
  void closeReleasesOwnedChannel() {
    var c = stagedOver(new byte[STAGING]);
    c.close();
  }
}
