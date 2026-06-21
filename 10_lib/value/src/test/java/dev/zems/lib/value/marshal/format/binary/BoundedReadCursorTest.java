package dev.zems.lib.value.marshal.format.binary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contract test for {@link BoundedReadCursor} — fixed-segment reader that backs binary decode for in-memory and mmap'd
 * inputs. Pins boundary conditions and primitive accessors.
 */
@ContractTest
@DisplayName("BoundedReadCursor")
class BoundedReadCursorTest {

  /** Build a heap segment from a buffer using BIG_ENDIAN (the protocol's wire byte order). */
  private static SegmentReadCursor cursorFromBytes(byte[] bytes) {
    var segment = MemorySegment.ofArray(bytes);
    return SegmentCursors.bounded(segment);
  }

  @Test
  @DisplayName("empty segment has no remaining bytes")
  void emptySegment() {
    var c = cursorFromBytes(new byte[0]);
    assertThat(c.hasRemaining()).isFalse();
    assertThat(c.position()).isZero();
  }

  @Test
  @DisplayName("get() reads bytes sequentially and advances position")
  void sequentialByteRead() {
    var c = cursorFromBytes(new byte[] { 1, 2, 3 });
    assertThat(c.get()).isEqualTo((byte) 1);
    assertThat(c.position()).isEqualTo(1);
    assertThat(c.get()).isEqualTo((byte) 2);
    assertThat(c.get()).isEqualTo((byte) 3);
    assertThat(c.hasRemaining()).isFalse();
  }

  @Test
  @DisplayName("get() past the end throws")
  void getPastEndThrows() {
    var c = cursorFromBytes(new byte[] { 1 });
    c.get();
    assertThatThrownBy(c::get).isInstanceOfAny(IndexOutOfBoundsException.class, IllegalStateException.class);
  }

  @Test
  @DisplayName("getInt reads BIG_ENDIAN 4 bytes")
  void getIntBigEndian() {
    var buf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(0x01020304).array();
    var c = cursorFromBytes(buf);
    assertThat(c.getInt()).isEqualTo(0x01020304);
    assertThat(c.position()).isEqualTo(4);
  }

  @Test
  @DisplayName("getLong / getDouble / getFloat / getShort all advance position")
  void primitiveAccessorsAdvancePosition() {
    var buf = ByteBuffer.allocate(8 + 8 + 4 + 2).order(ByteOrder.BIG_ENDIAN);
    buf
      .putLong(123L)
      .putDouble(3.14)
      .putFloat(2.5f)
      .putShort((short) 7);

    var c = cursorFromBytes(buf.array());
    assertThat(c.getLong()).isEqualTo(123L);
    assertThat(c.position()).isEqualTo(8);
    assertThat(c.getDouble()).isEqualTo(3.14);
    assertThat(c.position()).isEqualTo(16);
    assertThat(c.getFloat()).isEqualTo(2.5f);
    assertThat(c.getShort()).isEqualTo((short) 7);
  }

  @Test
  @DisplayName("get(byte[], off, len) bulk-copies into destination")
  void bulkRead() {
    var c = cursorFromBytes(new byte[] { 1, 2, 3, 4, 5 });
    var dst = new byte[5];
    c.get(dst, 0, 3);
    assertThat(dst).startsWith((byte) 1, (byte) 2, (byte) 3);
    assertThat(c.position()).isEqualTo(3);
  }

  @Test
  @DisplayName("peek() reads next byte without advancing")
  void peekDoesNotAdvance() {
    var c = cursorFromBytes(new byte[] { 42, 7 });
    assertThat(c.peek()).isEqualTo((byte) 42);
    assertThat(c.position()).isZero();
    assertThat(c.get()).isEqualTo((byte) 42);
  }

  @Test
  @DisplayName("skip(n) advances position by n")
  void skipAdvancesPosition() {
    var c = cursorFromBytes(new byte[] { 1, 2, 3, 4, 5 });
    c.skip(3);
    assertThat(c.position()).isEqualTo(3);
    assertThat(c.get()).isEqualTo((byte) 4);
  }

  @Test
  @DisplayName("close() does not throw")
  void closeIsSafe() {
    var c = cursorFromBytes(new byte[] { 1, 2, 3 });
    c.close();
  }

  @Test
  @DisplayName("works on shared-arena native segment")
  void nativeSegment() {
    try (var arena = Arena.ofShared()) {
      var seg = arena.allocate(4);
      seg.set(java.lang.foreign.ValueLayout.JAVA_BYTE, 0, (byte) 1);
      seg.set(java.lang.foreign.ValueLayout.JAVA_BYTE, 1, (byte) 2);
      seg.set(java.lang.foreign.ValueLayout.JAVA_BYTE, 2, (byte) 3);
      seg.set(java.lang.foreign.ValueLayout.JAVA_BYTE, 3, (byte) 4);
      var c = SegmentCursors.bounded(seg);
      assertThat(c.get()).isEqualTo((byte) 1);
      assertThat(c.get()).isEqualTo((byte) 2);
      assertThat(c.position()).isEqualTo(2);
    }
  }
}
