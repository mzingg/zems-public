package dev.zems.lib.value.valueio.format.binary;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common.junit.ContractTest;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("StagedReadCursor")
class StagedReadCursorTest {

  private static final int MIN_STAGING_BYTES = 64;

  private static SegmentReadCursor stagedOver(byte[] bytes) {
    return SegmentCursors.stagedReader(Channels.newChannel(new ByteArrayInputStream(bytes)), MIN_STAGING_BYTES, true);
  }

  @Test
  @DisplayName("byte-by-byte read across multiple refills (payload > staging)")
  void byteReadAcrossRefills() {
    int n = MIN_STAGING_BYTES * 3 + 5;
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
    var buf = ByteBuffer.allocate(MIN_STAGING_BYTES + 4).order(ByteOrder.BIG_ENDIAN);
    for (int i = 0; i < MIN_STAGING_BYTES - 1; i++) {
      buf.put((byte) 0);
    }
    buf.putInt(0x01020304);
    var c = stagedOver(buf.array());
    c.skip(MIN_STAGING_BYTES - 1);
    assertThat(c.getInt()).isEqualTo(0x01020304);
  }

  @Test
  @DisplayName("getLong straddling refills")
  void getLongAcrossRefill() {
    var buf = ByteBuffer.allocate(MIN_STAGING_BYTES + 8).order(ByteOrder.BIG_ENDIAN);
    for (int i = 0; i < MIN_STAGING_BYTES - 1; i++) {
      buf.put((byte) 0);
    }
    buf.putLong(0x0102030405060708L);
    var c = stagedOver(buf.array());
    c.skip(MIN_STAGING_BYTES - 1);
    assertThat(c.getLong()).isEqualTo(0x0102030405060708L);
  }

  @Test
  @DisplayName("bulk read larger than staging refills repeatedly")
  void bulkReadAcrossRefills() {
    int n = MIN_STAGING_BYTES * 4;
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
    int n = MIN_STAGING_BYTES * 2;
    var src = new byte[n];
    for (int i = 0; i < n; i++) {
      src[i] = (byte) i;
    }
    var c = stagedOver(src);
    c.skip(MIN_STAGING_BYTES + 7);
    assertThat(c.position()).isEqualTo(MIN_STAGING_BYTES + 7);
    assertThat(c.get()).isEqualTo((byte) (MIN_STAGING_BYTES + 7));
  }

  @Test
  @DisplayName("close() does not throw when ownsChannel=true")
  void closeReleasesOwnedChannel() {
    var c = stagedOver(new byte[MIN_STAGING_BYTES]);
    c.close();
  }
}
