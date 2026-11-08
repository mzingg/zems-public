package dev.zems.lib.value.valueio.format.binary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.valueio.wire.WireConstraintViolationException;
import java.io.ByteArrayInputStream;
import java.lang.foreign.MemorySegment;
import java.nio.channels.Channels;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("CborReader")
class CborReaderTest {

  /** RFC 8949 §3: major type 3 with additional information 26, so a four-byte length follows. */
  private static final byte TEXT_HEADER_WITH_FOUR_BYTE_LENGTH = (byte) 0x7a;

  private static CborReader readerOf(String hex) {
    return readerOf(hex, Integer.MAX_VALUE);
  }

  private static CborReader readerOf(String hex, long maxStringLength) {
    byte[] bytes = HexFormat.of().parseHex(hex);
    MemorySegment seg = MemorySegment.ofArray(bytes);
    return new CborReader(SegmentCursors.bounded(seg), maxStringLength);
  }

  private static CborReader stagedReaderOf(String hex, long maxStringLength) {
    byte[] bytes = HexFormat.of().parseHex(hex);
    var cursor = SegmentCursors.stagedReader(Channels.newChannel(new ByteArrayInputStream(bytes)), 64, true);
    return new CborReader(cursor, maxStringLength);
  }

  private static byte[] cborText(int length) {
    byte[] out = new byte[5 + length];
    out[0] = TEXT_HEADER_WITH_FOUR_BYTE_LENGTH;
    out[1] = (byte) (length >>> 24);
    out[2] = (byte) (length >>> 16);
    out[3] = (byte) (length >>> 8);
    out[4] = (byte) length;
    Arrays.fill(out, 5, out.length, (byte) 0x61);
    return out;
  }

  private static CborReader readerOf(byte[] bytes) {
    return new CborReader(SegmentCursors.bounded(MemorySegment.ofArray(bytes)), Integer.MAX_VALUE);
  }

  @Nested
  @DisplayName("string/byte length bounds (checked before allocating)")
  class LengthBounds {

    @Test
    @DisplayName("readText rejects an oversized declared length without allocating the buffer")
    void readTextRejectsOversizedLength() {
      assertThatThrownBy(() -> readerOf(CborHex.HUGE_TEXT_HEADER, 1024).readText())
        .isInstanceOf(WireConstraintViolationException.class)
        .hasMessageContaining("maxStringLength");
    }

    @Test
    @DisplayName("readBytes rejects an oversized declared length without allocating the buffer")
    void readBytesRejectsOversizedLength() {
      assertThatThrownBy(() -> readerOf(CborHex.HUGE_BYTES_HEADER, 1024).readBytes())
        .isInstanceOf(WireConstraintViolationException.class)
        .hasMessageContaining("maxStringLength");
    }

    @Test
    @DisplayName("a channel-backed reader rejects an oversized declared length without the large allocation")
    void stagedReaderRejectsOversizedLengthWithoutAllocating() {
      assertThatThrownBy(() -> stagedReaderOf(CborHex.HUGE_TEXT_HEADER, 1024).readText())
        .isInstanceOf(WireConstraintViolationException.class)
        .hasMessageContaining("maxStringLength");
    }
  }

  @Nested
  @DisplayName("scratch buffer retention")
  class ScratchRetention {

    @Test
    @DisplayName("a one-off oversized read serves the text but does not inflate the retained scratch buffer")
    void oversizedReadDoesNotRetainBuffer() {
      int oversized = CborReader.MAX_RETAINED_SCRATCH + 1024;

      String huge = readerOf(cborText(oversized)).readText();
      readerOf(cborText(10)).readText();

      assertThat(huge).hasSize(oversized);
      assertThat(CborReader.retainedScratchCapacity()).isLessThanOrEqualTo(CborReader.MAX_RETAINED_SCRATCH);
    }
  }

  @Nested
  @DisplayName("unsigned integers")
  class UnsignedInts {

    @Test
    void inline() {
      assertThat(readerOf("17").readUnsignedInt()).isEqualTo(23L);
    }

    @Test
    void oneByte() {
      assertThat(readerOf("18ff").readUnsignedInt()).isEqualTo(255L);
    }

    @Test
    void twoBytes() {
      assertThat(readerOf("19ffff").readUnsignedInt()).isEqualTo(65535L);
    }

    @Test
    void fourBytes() {
      assertThat(readerOf("1affffffff").readUnsignedInt()).isEqualTo(0xFFFFFFFFL);
    }

    @Test
    void eightBytes() {
      assertThat(readerOf("1b0000000100000000").readUnsignedInt()).isEqualTo(0x100000000L);
    }
  }

  @Nested
  @DisplayName("signed integers via readInt64")
  class SignedInts {

    @Test
    void negativeOne() {
      assertThat(readerOf(CborHex.NEGATIVE_ONE).readInt64()).isEqualTo(-1L);
    }

    @Test
    void negativeOneHundred() {
      assertThat(readerOf(CborHex.NEGATIVE_ONE_HUNDRED).readInt64()).isEqualTo(-100L);
    }

    @Test
    void longMin() {
      assertThat(readerOf(CborHex.LONG_MIN).readInt64()).isEqualTo(Long.MIN_VALUE);
    }
  }

  @Nested
  @DisplayName("simple / float / bool")
  class SimpleAndFloat {

    @Test
    void boolTrue() {
      assertThat(readerOf("f5").readBool()).isTrue();
    }

    @Test
    void boolFalse() {
      assertThat(readerOf("f4").readBool()).isFalse();
    }

    @Test
    void nullValue() {
      readerOf(CborHex.NULL).readNull();
    }

    @Test
    void undefinedValue() {
      readerOf("f7").readUndefined();
    }

    @Test
    void float32_strict() {
      assertThat(readerOf(CborHex.FLOAT32_PI).readFloat32()).isEqualTo(3.14f);
    }

    @Test
    void float64_strict() {
      assertThat(readerOf(CborHex.FLOAT64_PI).readFloat64()).isEqualTo(3.14);
    }

    @Test
    void floatAny_acceptsBoth() {
      assertThat(readerOf(CborHex.FLOAT32_ONE).readFloatAny()).isEqualTo(1.0);
      assertThat(readerOf("fb3ff0000000000000").readFloatAny()).isEqualTo(1.0);
    }
  }

  @Nested
  @DisplayName("text / byte strings")
  class Strings {

    @Test
    void asciiText() {
      assertThat(readerOf(CborHex.TEXT_HELLO).readText()).isEqualTo("hello");
    }

    @Test
    void multiByteUtf8() {
      assertThat(readerOf(CborHex.TEXT_HELLO_ACCENTED).readText()).isEqualTo("héllo");
    }

    @Test
    void emptyText() {
      assertThat(readerOf("60").readText()).isEmpty();
    }

    @Test
    void fourBytesString() {
      assertThat(readerOf("4401020304").readBytes()).containsExactly(1, 2, 3, 4);
    }

    @Test
    void indefiniteLengthText_concatenatedChunks() {
      assertThat(readerOf(CborHex.TEXT_HELLO_CHUNKED).readText()).isEqualTo("hello");
    }
  }

  @Nested
  @DisplayName("tags / arrays / maps")
  class TagsAndContainers {

    @Test
    void selfDescribeTag() {
      assertThat(readerOf(CborHex.SELF_DESCRIBE_TAG).readTag()).isEqualTo(55799L);
    }

    @Test
    void arrayHeader_threeElements() {
      assertThat(readerOf("83").readArrayHeader()).isEqualTo(3L);
    }

    @Test
    void mapHeader_fourEntries() {
      assertThat(readerOf("a4").readMapHeader()).isEqualTo(4L);
    }

    @Test
    void indefiniteArrayHeader_returnsSentinel() {
      assertThat(readerOf("9fff").readArrayHeader()).isEqualTo(CborReader.INDEFINITE_LENGTH);
    }

    @Test
    void indefiniteMapHeader_returnsSentinel() {
      assertThat(readerOf("bfff").readMapHeader()).isEqualTo(CborReader.INDEFINITE_LENGTH);
    }
  }

  @Nested
  @DisplayName("protocol errors")
  class ProtocolErrors {

    @Test
    void readUnsignedInt_onNegative_throws() {
      assertThatThrownBy(() -> readerOf(CborHex.NEGATIVE_ONE).readUnsignedInt())
        .isInstanceOf(CborProtocolException.class)
        .hasMessageContaining("UNSIGNED_INT");
    }

    @Test
    void readBool_onNonSimple_throws() {
      assertThatThrownBy(() -> readerOf("00").readBool())
        .isInstanceOf(CborProtocolException.class)
        .hasMessageContaining("SIMPLE_OR_FLOAT");
    }

    @Test
    void readFloat32_onFloat64_throws() {
      assertThatThrownBy(() -> readerOf(CborHex.FLOAT64_PI).readFloat32())
        .isInstanceOf(CborProtocolException.class)
        .hasMessageContaining("float32");
    }
  }

  @Nested
  @DisplayName("peek + skip")
  class PeekAndSkip {

    @Test
    void peekMajorType_doesNotConsume() {
      var r = readerOf("17");
      assertThat(r.peekMajorType()).isEqualTo(CborMajorType.UNSIGNED_INT);
      assertThat(r.readUnsignedInt()).isEqualTo(23L);
    }

    @Test
    void skipItem_advancesOverInteger() {
      var r = readerOf("1818" + "f5");
      r.skipItem();
      assertThat(r.readBool()).isTrue();
    }

    @Test
    void skipItem_advancesOverMap() {
      var r = readerOf(CborHex.MAP_ONE_ENTRY + CborHex.ZERO + CborHex.TEXT_X + CborHex.TRUE);
      r.skipItem();
      assertThat(r.readBool()).isTrue();
    }

    @Test
    void skipItem_advancesOverTaggedItem() {
      var r = readerOf(CborHex.TAG_ZERO + CborHex.TEXT_X + CborHex.TRUE);
      r.skipItem();
      assertThat(r.readBool()).isTrue();
    }

    @Test
    @DisplayName("skipItem steps over a half-float, which an interop peer may emit in an unknown slot")
    void skipItem_advancesOverHalfFloat() {
      var r = readerOf(CborHex.FLOAT16_ONE + CborHex.TRUE);
      r.skipItem();
      assertThat(r.readBool()).isTrue();
    }
  }
}
