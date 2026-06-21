package dev.zems.lib.value.marshal.format.binary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.marshal.wire.WireConstraintViolationException;
import java.io.ByteArrayInputStream;
import java.lang.foreign.MemorySegment;
import java.nio.channels.Channels;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Contract test for {@link CborReader}. Round-trips boundary integer encodings, validates float / simple / tag
 * dispatch, and exercises the indefinite-length tolerance path so Jackson/borer/foreign producers can be decoded
 * without surprises.
 */
@ContractTest
@DisplayName("CborReader")
class CborReaderTest {

  private static CborReader readerOf(String hex) {
    return readerOf(hex, Integer.MAX_VALUE);
  }

  private static CborReader readerOf(String hex, long maxStringLength) {
    byte[] bytes = HexFormat.of().parseHex(hex);
    MemorySegment seg = MemorySegment.ofArray(bytes);
    return new CborReader(SegmentCursors.bounded(seg), maxStringLength);
  }

  /** Channel-backed (staged) reader — no segment-size backstop on the declared length. */
  private static CborReader stagedReaderOf(String hex, long maxStringLength) {
    byte[] bytes = HexFormat.of().parseHex(hex);
    var cursor = SegmentCursors.stagedReader(Channels.newChannel(new ByteArrayInputStream(bytes)), 64, true);
    return new CborReader(cursor, maxStringLength);
  }

  /** A definite-length CBOR text item (major type 3, 4-byte length) of {@code length} 'a' bytes. */
  private static byte[] cborText(int length) {
    byte[] out = new byte[5 + length];
    out[0] = (byte) 0x7a; // major type 3 + arg 26 (4-byte length follows)
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

    // MT3 text + arg 26 (4-byte length) declaring 0x40000000 (~1 GiB); only the 5-byte header is present.
    private static final String HUGE_TEXT_HEADER = "7a40000000";
    // MT2 byte string + arg 26 declaring ~1 GiB; only the 5-byte header is present.
    private static final String HUGE_BYTES_HEADER = "5a40000000";

    @Test
    @DisplayName("readText rejects an oversized declared length without allocating the buffer")
    void readTextRejectsOversizedLength() {
      assertThatThrownBy(() -> readerOf(HUGE_TEXT_HEADER, 1024).readText())
        .isInstanceOf(WireConstraintViolationException.class)
        .hasMessageContaining("maxStringLength");
    }

    @Test
    @DisplayName("readBytes rejects an oversized declared length without allocating the buffer")
    void readBytesRejectsOversizedLength() {
      assertThatThrownBy(() -> readerOf(HUGE_BYTES_HEADER, 1024).readBytes())
        .isInstanceOf(WireConstraintViolationException.class)
        .hasMessageContaining("maxStringLength");
    }

    @Test
    @DisplayName("a channel-backed reader rejects an oversized declared length without the large allocation")
    void stagedReaderRejectsOversizedLengthWithoutAllocating() {
      // Were the bound checked only after allocation, this would attempt a ~1 GiB array before failing.
      assertThatThrownBy(() -> stagedReaderOf(HUGE_TEXT_HEADER, 1024).readText())
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
      int big = CborReader.MAX_RETAINED_SCRATCH + 1024; // exceeds the retention cap

      String huge = readerOf(cborText(big)).readText();
      readerOf(cborText(10)).readText(); // a small follow-up read on the same thread

      assertThat(huge).hasSize(big);
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
      assertThat(readerOf("20").readInt64()).isEqualTo(-1L);
    }

    @Test
    void negativeOneHundred() {
      // -100 → MT 1 + arg 99 → 0x38 0x63
      assertThat(readerOf("3863").readInt64()).isEqualTo(-100L);
    }

    @Test
    void longMin() {
      assertThat(readerOf("3b7fffffffffffffff").readInt64()).isEqualTo(Long.MIN_VALUE);
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
      readerOf("f6").readNull(); // no throw
    }

    @Test
    void undefinedValue() {
      readerOf("f7").readUndefined(); // no throw
    }

    @Test
    void float32_strict() {
      assertThat(readerOf("fa4048f5c3").readFloat32()).isEqualTo(3.14f);
    }

    @Test
    void float64_strict() {
      assertThat(readerOf("fb40091eb851eb851f").readFloat64()).isEqualTo(3.14);
    }

    @Test
    void floatAny_acceptsBoth() {
      assertThat(readerOf("fa3f800000").readFloatAny()).isEqualTo(1.0);
      assertThat(readerOf("fb3ff0000000000000").readFloatAny()).isEqualTo(1.0);
    }
  }

  @Nested
  @DisplayName("text / byte strings")
  class Strings {

    @Test
    void asciiText() {
      assertThat(readerOf("6568656c6c6f").readText()).isEqualTo("hello");
    }

    @Test
    void multiByteUtf8() {
      assertThat(readerOf("6668c3a96c6c6f").readText()).isEqualTo("héllo");
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
      // 0x7f indefinite-length text + chunk "he" (0x62 0x68 0x65) + chunk "llo" (0x63 0x6c 0x6c 0x6f) + break
      assertThat(readerOf("7f626865636c6c6fff").readText()).isEqualTo("hello");
    }
  }

  @Nested
  @DisplayName("tags / arrays / maps")
  class TagsAndContainers {

    @Test
    void selfDescribeTag() {
      assertThat(readerOf("d9d9f7").readTag()).isEqualTo(55799L);
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
      // 0x20 = MT 1 (negative int) — reader expects MT 0
      assertThatThrownBy(() -> readerOf("20").readUnsignedInt())
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
      assertThatThrownBy(() -> readerOf("fb40091eb851eb851f").readFloat32())
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
      // map(1) { 0: "x" } then bool true
      var r = readerOf("a1" + "00" + "6178" + "f5");
      r.skipItem();
      assertThat(r.readBool()).isTrue();
    }

    @Test
    void skipItem_advancesOverTaggedItem() {
      // tag(0) "x" then bool true
      var r = readerOf("c0" + "6178" + "f5");
      r.skipItem();
      assertThat(r.readBool()).isTrue();
    }

    @Test
    void skipItem_advancesOverHalfFloat() {
      // half-float 1.0 (0xF9 0x3C 0x00) — two payload bytes — then bool true; an interop peer may emit
      // half-floats this reader must structurally skip when draining an unknown slot.
      var r = readerOf("f93c00" + "f5");
      r.skipItem();
      assertThat(r.readBool()).isTrue();
    }
  }
}
