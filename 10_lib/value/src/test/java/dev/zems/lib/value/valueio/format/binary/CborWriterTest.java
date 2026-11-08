package dev.zems.lib.value.valueio.format.binary;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common.junit.ContractTest;
import java.io.ByteArrayOutputStream;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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

  private static String textWhoseWorstCaseExceedsRetentionCap() {
    return "a".repeat(CborWriter.MAX_RETAINED_SCRATCH);
  }

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

  @Nested
  @DisplayName("int64 (mixed sign via writeInt64)")
  class SignedInts {

    @Test
    void negativeOne_inline() {
      assertThat(encode(w -> w.writeInt64(-1))).isEqualTo(CborHex.NEGATIVE_ONE);
    }

    @Test
    void negativeTwentyFour_inline() {
      assertThat(encode(w -> w.writeInt64(-24))).isEqualTo(CborHex.NEGATIVE_TWENTY_FOUR);
    }

    @Test
    void negativeTwentyFive_oneByte() {
      assertThat(encode(w -> w.writeInt64(-25))).isEqualTo(CborHex.NEGATIVE_TWENTY_FIVE);
    }

    @Test
    void longMin_eightBytes() {
      assertThat(encode(w -> w.writeInt64(Long.MIN_VALUE))).isEqualTo(CborHex.LONG_MIN);
    }
  }

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

  @Nested
  @DisplayName("floats (major type 7)")
  class Floats {

    @Test
    void writeFloat32_pi() {
      assertThat(encode(w -> w.writeFloat32(3.14f))).isEqualTo(CborHex.FLOAT32_PI);
    }

    @Test
    void writeFloat64_pi() {
      assertThat(encode(w -> w.writeFloat64(3.14))).isEqualTo(CborHex.FLOAT64_PI);
    }

    @Test
    void writeFloat64Canonical_downShiftsFloat32Roundtrip() {
      assertThat(encode(w -> w.writeFloat64Canonical(1.0))).isEqualTo(CborHex.FLOAT32_ONE);
    }

    @Test
    void writeFloat64Canonical_keepsFloat64WhenLossy() {
      assertThat(encode(w -> w.writeFloat64Canonical(3.14))).isEqualTo(CborHex.FLOAT64_PI);
    }
  }

  @Nested
  @DisplayName("text strings (major type 3)")
  class Strings {

    @Test
    void emptyText() {
      assertThat(encode(w -> w.writeText(""))).isEqualTo("60");
    }

    @Test
    void asciiText() {
      assertThat(encode(w -> w.writeText("hello"))).isEqualTo(CborHex.TEXT_HELLO);
    }

    @Test
    void multiByteUtf8Text() {
      assertThat(encode(w -> w.writeText("héllo"))).isEqualTo(CborHex.TEXT_HELLO_ACCENTED);
    }
  }

  @Nested
  @DisplayName("scratch buffer retention")
  class ScratchRetention {

    @Test
    @DisplayName("a one-off oversized write does not inflate the retained scratch buffer")
    void oversizedWriteDoesNotRetainBuffer() {
      encode(w -> w.writeText(textWhoseWorstCaseExceedsRetentionCap()));
      encode(w -> w.writeText("a"));

      assertThat(CborWriter.retainedScratchCapacity()).isLessThanOrEqualTo(CborWriter.MAX_RETAINED_SCRATCH);
    }
  }

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

  @Nested
  @DisplayName("tags (major type 6)")
  class Tags {

    @Test
    void selfDescribeTag_isThreeBytes() {
      assertThat(encode(w -> w.writeTag(CborTag.SELF_DESCRIBE))).isEqualTo(CborHex.SELF_DESCRIBE_TAG);
    }

    @Test
    void privateUseStateNullTag() {
      assertThat(encode(w -> w.writeTag(CborTag.STATE_NULL))).isEqualTo(CborHex.STATE_NULL_TAG);
    }
  }

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
