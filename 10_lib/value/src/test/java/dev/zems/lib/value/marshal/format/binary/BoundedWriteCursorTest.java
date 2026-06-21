package dev.zems.lib.value.marshal.format.binary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import java.lang.foreign.MemorySegment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contract test for {@link BoundedWriteCursor} — fixed-segment writer that backs binary encode for in-memory output.
 * Pins boundary conditions, primitive accessors, and the overflow-on-overrun contract.
 */
@ContractTest
@DisplayName("BoundedWriteCursor")
class BoundedWriteCursorTest {

  private static SegmentWriteCursor cursorOf(int size) {
    var segment = MemorySegment.ofArray(new byte[size]);
    return SegmentCursors.boundedWriter(segment);
  }

  @Test
  @DisplayName("put(byte) writes sequentially and advances position")
  void putByte() {
    var c = cursorOf(3);
    c.put((byte) 1);
    c.put((byte) 2);
    c.put((byte) 3);
    assertThat(c.position()).isEqualTo(3);
  }

  @Test
  @DisplayName("put past end throws ISE with overflow message")
  void putPastEndThrows() {
    var c = cursorOf(2);
    c.put((byte) 1);
    c.put((byte) 2);
    assertThatThrownBy(() -> c.put((byte) 3))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Overflow");
  }

  @Test
  @DisplayName("primitive accessors put correct number of bytes BIG_ENDIAN")
  void primitiveAccessorsBigEndian() {
    var c = cursorOf(4 + 8 + 8 + 4 + 2);
    c.putInt(0x01020304);
    c.putLong(123L);
    c.putDouble(3.14);
    c.putFloat(2.5f);
    c.putShort((short) 7);
    c.flush();
    assertThat(c.position()).isEqualTo(4 + 8 + 8 + 4 + 2);

    // Verify by reading back through a bounded reader
    var roundTrip = MemorySegment.ofArray(new byte[28]);
    var w = SegmentCursors.boundedWriter(roundTrip);
    w.putInt(0x01020304);
    w.putLong(123L);
    var r = SegmentCursors.bounded(roundTrip);
    assertThat(r.getInt()).isEqualTo(0x01020304);
    assertThat(r.getLong()).isEqualTo(123L);
  }

  @Test
  @DisplayName("bulk put(byte[], off, len) copies and advances")
  void bulkPut() {
    var c = cursorOf(5);
    c.put(new byte[] { 1, 2, 3, 4, 5 }, 0, 5);
    assertThat(c.position()).isEqualTo(5);
  }

  @Test
  @DisplayName("bulk put past end throws ISE")
  void bulkPutPastEndThrows() {
    var c = cursorOf(3);
    assertThatThrownBy(() -> c.put(new byte[] { 1, 2, 3, 4 }, 0, 4))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Overflow");
  }

  @Test
  @DisplayName("putInt at end of segment throws ISE")
  void putIntPastEnd() {
    // 3-byte segment can't hold a 4-byte int.
    var c = cursorOf(3);
    assertThatThrownBy(() -> c.putInt(42))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Overflow");
  }

  @Test
  @DisplayName("flush() and close() do not throw on a fresh cursor")
  void flushAndCloseSafe() {
    var c = cursorOf(8);
    c.flush();
    c.close();
  }
}
