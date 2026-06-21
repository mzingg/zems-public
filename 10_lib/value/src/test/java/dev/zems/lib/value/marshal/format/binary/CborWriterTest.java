package dev.zems.lib.value.marshal.format.binary;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.ContractTest;
import java.io.ByteArrayOutputStream;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Contract test for {@link CborWriter}. Pins down canonical (RFC 8949 §4.2.1) byte-level output for each major-type
 * writer. Tests focus on boundary-of-argument-encoding values because the off-by-one transition between 0–23 / 24–255 /
 * 256–65535 / 2^32 / 2^32+1 is where shortest-form encoders historically go wrong.
 */
@ContractTest
@DisplayName("CborWriter")
class CborWriterTest {

  private static final HexFormat HEX = HexFormat.of();

  private static String encode(java.util.function.Consumer<CborWriter> action) {
    var bos = new ByteArrayOutputStream();
    var sink = new TestByteOutput(bos);
    action.accept(new CborWriter(sink));
    return HEX.formatHex(bos.toByteArray());
  }

  // ============ Unsigned ints ============

  /** Minimal {@link CborByteOutput} backed by a {@link ByteArrayOutputStream} for verification. */
  private static final class TestByteOutput implements CborByteOutput {

    private final ByteArrayOutputStream out;

    TestByteOutput(ByteArrayOutputStream out) {
      this.out = out;
    }

    @Override
    public void put(byte b) {
      out.write(b & 0xFF);
    }

    @Override
    public void put(byte[] src, int off, int len) {
      out.write(src, off, len);
    }

    @Override
    public void putShort(short v) {
      out.write((v >>> 8) & 0xFF);
      out.write(v & 0xFF);
    }

    @Override
    public void putInt(int v) {
      out.write((v >>> 24) & 0xFF);
      out.write((v >>> 16) & 0xFF);
      out.write((v >>> 8) & 0xFF);
      out.write(v & 0xFF);
    }

    @Override
    public void putLong(long v) {
      for (int i = 7; i >= 0; i--) {
        out.write((int) ((v >>> (i * 8)) & 0xFF));
      }
    }

    @Override
    public void putFloat(float v) {
      putInt(Float.floatToRawIntBits(v));
    }

    @Override
    public void putDouble(double v) {
      putLong(Double.doubleToRawLongBits(v));
    }
  }

  // ============ Signed ints (mixed) ============

  @Nested
  @DisplayName("unsigned integers (major type 0)")
  class UnsignedInts {

    @Test
    void zero_inline() {
      assertThat(encode(w -> w.writeUnsignedInt(0))).isEqualTo("00");
    }

    @Test
    void twentyThree_inline() {
      assertThat(encode(w -> w.writeUnsignedInt(23))).isEqualTo("17");
    }

    @Test
    void twentyFour_oneByte() {
      assertThat(encode(w -> w.writeUnsignedInt(24))).isEqualTo("1818");
    }

    @Test
    void twoFiftyFive_oneByte() {
      assertThat(encode(w -> w.writeUnsignedInt(0xFF))).isEqualTo("18ff");
    }

    @Test
    void twoFiftySix_twoBytes() {
      assertThat(encode(w -> w.writeUnsignedInt(0x100))).isEqualTo("190100");
    }

    @Test
    void sixtyFiveKMinus1_twoBytes() {
      assertThat(encode(w -> w.writeUnsignedInt(0xFFFF))).isEqualTo("19ffff");
    }

    @Test
    void sixtyFiveK_fourBytes() {
      assertThat(encode(w -> w.writeUnsignedInt(0x10000))).isEqualTo("1a00010000");
    }

    @Test
    void fourGMinus1_fourBytes() {
      assertThat(encode(w -> w.writeUnsignedInt(0xFFFFFFFFL))).isEqualTo("1affffffff");
    }

    @Test
    void fourG_eightBytes() {
      assertThat(encode(w -> w.writeUnsignedInt(0x100000000L))).isEqualTo("1b0000000100000000");
    }
  }

  // ============ Bool / null / undefined ============

  @Nested
  @DisplayName("int64 (mixed sign via writeInt64)")
  class SignedInts {

    @Test
    void negativeOne_inline() {
      // -1 encodes as MT 1 + arg 0 → 0x20
      assertThat(encode(w -> w.writeInt64(-1))).isEqualTo("20");
    }

    @Test
    void negativeTwentyFour_inline() {
      // -24 encodes as MT 1 + arg 23 → 0x37
      assertThat(encode(w -> w.writeInt64(-24))).isEqualTo("37");
    }

    @Test
    void negativeTwentyFive_oneByte() {
      // -25 encodes as MT 1 + arg 24 + 0x18 → 0x38 0x18
      assertThat(encode(w -> w.writeInt64(-25))).isEqualTo("3818");
    }

    @Test
    void longMin_eightBytes() {
      // Long.MIN_VALUE encodes as MT 1 + arg (Long.MAX_VALUE) → 0x3b + 7fff_ffff_ffff_ffff
      assertThat(encode(w -> w.writeInt64(Long.MIN_VALUE))).isEqualTo("3b7fffffffffffffff");
    }
  }

  // ============ Floats ============

  @Nested
  @DisplayName("major type 7 simple values")
  class SimpleValues {

    @Test
    void writeBool_true() {
      assertThat(encode(w -> w.writeBool(true))).isEqualTo("f5");
    }

    @Test
    void writeBool_false() {
      assertThat(encode(w -> w.writeBool(false))).isEqualTo("f4");
    }

    @Test
    void writeNull_byte() {
      assertThat(encode(CborWriter::writeNull)).isEqualTo("f6");
    }

    @Test
    void writeUndefined_byte() {
      assertThat(encode(CborWriter::writeUndefined)).isEqualTo("f7");
    }
  }

  // ============ Strings ============

  @Nested
  @DisplayName("floats (major type 7)")
  class Floats {

    @Test
    void writeFloat32_pi() {
      // 3.14f → 0xfa + 0x4048f5c3
      assertThat(encode(w -> w.writeFloat32(3.14f))).isEqualTo("fa4048f5c3");
    }

    @Test
    void writeFloat64_pi() {
      // 3.14 (double) → 0xfb + 0x40091eb851eb851f
      assertThat(encode(w -> w.writeFloat64(3.14))).isEqualTo("fb40091eb851eb851f");
    }

    @Test
    void writeFloat64Canonical_downShiftsFloat32Roundtrip() {
      // 1.0 round-trips as float32 → 0xfa3f800000
      assertThat(encode(w -> w.writeFloat64Canonical(1.0))).isEqualTo("fa3f800000");
    }

    @Test
    void writeFloat64Canonical_keepsFloat64WhenLossy() {
      // 3.14 cannot be exactly represented in float32, so it stays as float64
      assertThat(encode(w -> w.writeFloat64Canonical(3.14))).isEqualTo("fb40091eb851eb851f");
    }
  }

  // ============ Byte strings ============

  @Nested
  @DisplayName("text strings (major type 3)")
  class Strings {

    @Test
    void emptyText() {
      assertThat(encode(w -> w.writeText(""))).isEqualTo("60");
    }

    @Test
    void asciiText() {
      // "hello" → 0x65 + UTF-8 bytes
      assertThat(encode(w -> w.writeText("hello"))).isEqualTo("6568656c6c6f");
    }

    @Test
    void multiByteUtf8Text() {
      // "héllo" → 0x66 + UTF-8 bytes (é is two bytes)
      assertThat(encode(w -> w.writeText("héllo"))).isEqualTo("6668c3a96c6c6f");
    }
  }

  @Nested
  @DisplayName("scratch buffer retention")
  class ScratchRetention {

    @Test
    @DisplayName("a one-off oversized write does not inflate the retained scratch buffer")
    void oversizedWriteDoesNotRetainBuffer() {
      // charLen 8192 → worst-case 24576 bytes, well over the 8 KiB retention cap.
      encode(w -> w.writeText("a".repeat(CborWriter.MAX_RETAINED_SCRATCH)));
      encode(w -> w.writeText("a")); // a small follow-up write on the same thread

      assertThat(CborWriter.retainedScratchCapacity()).isLessThanOrEqualTo(CborWriter.MAX_RETAINED_SCRATCH);
    }
  }

  // ============ Tags ============

  @Nested
  @DisplayName("byte strings (major type 2)")
  class ByteStrings {

    @Test
    void emptyBytes() {
      assertThat(encode(w -> w.writeBytes(new byte[0]))).isEqualTo("40");
    }

    @Test
    void fourBytes() {
      assertThat(encode(w -> w.writeBytes(new byte[] { 1, 2, 3, 4 }))).isEqualTo("4401020304");
    }
  }

  // ============ Array / Map headers ============

  @Nested
  @DisplayName("tags (major type 6)")
  class Tags {

    @Test
    void selfDescribeTag_isThreeBytes() {
      // tag 55799 → 0xd9 0xd9 0xf7
      assertThat(encode(w -> w.writeTag(CborTag.SELF_DESCRIBE))).isEqualTo("d9d9f7");
    }

    @Test
    void privateUseStateNullTag() {
      // tag 49152 → MT 6 + arg 25 + 0xc0 0x00 → 0xd9 0xc0 0x00
      assertThat(encode(w -> w.writeTag(CborTag.STATE_NULL))).isEqualTo("d9c000");
    }
  }

  // ============ Test fixture ============

  @Nested
  @DisplayName("array / map headers")
  class Containers {

    @Test
    void emptyArray() {
      assertThat(encode(w -> w.writeArrayHeader(0))).isEqualTo("80");
    }

    @Test
    void threeElementArray() {
      assertThat(encode(w -> w.writeArrayHeader(3))).isEqualTo("83");
    }

    @Test
    void emptyMap() {
      assertThat(encode(w -> w.writeMapHeader(0))).isEqualTo("a0");
    }

    @Test
    void fourEntryMap() {
      assertThat(encode(w -> w.writeMapHeader(4))).isEqualTo("a4");
    }
  }
}
