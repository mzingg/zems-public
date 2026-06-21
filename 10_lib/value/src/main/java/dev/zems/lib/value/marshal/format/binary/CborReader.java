package dev.zems.lib.value.marshal.format.binary;

import static dev.zems.lib.value.marshal.format.binary.CborConstants.ARG_1BYTE;
import static dev.zems.lib.value.marshal.format.binary.CborConstants.ARG_2BYTE;
import static dev.zems.lib.value.marshal.format.binary.CborConstants.ARG_4BYTE;
import static dev.zems.lib.value.marshal.format.binary.CborConstants.ARG_8BYTE;
import static dev.zems.lib.value.marshal.format.binary.CborConstants.ARG_INDEFINITE;
import static dev.zems.lib.value.marshal.format.binary.CborConstants.ARG_INLINE_MAX;
import static dev.zems.lib.value.marshal.format.binary.CborConstants.BREAK_BYTE;
import static dev.zems.lib.value.marshal.format.binary.CborConstants.FLOAT_16_ARG;
import static dev.zems.lib.value.marshal.format.binary.CborConstants.FLOAT_32_ARG;
import static dev.zems.lib.value.marshal.format.binary.CborConstants.FLOAT_64_ARG;
import static dev.zems.lib.value.marshal.format.binary.CborConstants.SIMPLE_FALSE;
import static dev.zems.lib.value.marshal.format.binary.CborConstants.SIMPLE_NULL;
import static dev.zems.lib.value.marshal.format.binary.CborConstants.SIMPLE_TRUE;
import static dev.zems.lib.value.marshal.format.binary.CborConstants.SIMPLE_UNDEFINED;

import dev.zems.lib.value.marshal.wire.WireConstraintViolationException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * CBOR decoder (RFC 8949) on top of a {@link SegmentReadCursor}. Tolerates indefinite-length items on read (peers like
 * Jackson CBOR may emit them) and translates them through the same surface — callers see {@link #INDEFINITE_LENGTH}
 * from {@code readArrayHeader} / {@code readMapHeader} and consume elements until {@link #peekIsBreak()} returns true.
 *
 * <p>
 * Throws {@link CborProtocolException} on any structural mismatch: wrong major type, malformed argument, invalid simple
 * value.
 */
final class CborReader {

  /** Sentinel returned by {@link #readArrayHeader()} / {@link #readMapHeader()} for indefinite-length items. */
  static final long INDEFINITE_LENGTH = -1L;

  /**
   * Thread-scoped reusable buffer for definite-length {@link #readText()} payloads. ThreadLocal rather than
   * per-instance because {@code CborReader} is constructed per-deserialize call — an instance scratch can't amortise
   * across records. Starts at 256 B and grows on demand to the largest text seen on the thread.
   */
  private static final ThreadLocal<byte[]> TEXT_SCRATCH = ThreadLocal.withInitial(() -> new byte[256]);

  /**
   * Largest scratch buffer retained across reads on a thread. A text larger than this reads through a one-off buffer
   * that is not stored back, so a single jumbo string can't pin scratch on a pooled thread for the thread's life.
   */
  static final int MAX_RETAINED_SCRATCH = 8 * 1024;

  private final SegmentReadCursor cursor;
  private final long maxStringLength;

  CborReader(SegmentReadCursor cursor, long maxStringLength) {
    this.cursor = cursor;
    this.maxStringLength = maxStringLength;
  }

  /**
   * Validates a wire-declared byte/text length against {@code maxStringLength} <b>before</b> any buffer is allocated, so
   * a hostile small payload cannot drive a huge allocation. The bound never exceeds {@code Integer.MAX_VALUE}, so a
   * value that passes is safe to use as an {@code int} — a length beyond the addressable range is rejected here as a
   * structured violation instead of surfacing a raw {@code ArithmeticException} from {@code Math.toIntExact}.
   */
  private int checkedLength(long declared) {
    if (declared > maxStringLength) {
      throw new WireConstraintViolationException("maxStringLength", maxStringLength, declared);
    }
    return (int) declared;
  }

  // ============ Inspection ============

  /** Returns the major type of the next data item without consuming any bytes. */
  CborMajorType peekMajorType() {
    return CborMajorType.of(Byte.toUnsignedInt(cursor.peek()));
  }

  /** Returns the raw next initial byte as an unsigned int without consuming it. */
  int peekInitialByte() {
    return Byte.toUnsignedInt(cursor.peek());
  }

  /** True iff the underlying cursor has more bytes available. */
  boolean hasRemaining() {
    return cursor.hasRemaining();
  }

  /** Bytes consumed since construction (passes through to the cursor). */
  long position() {
    return cursor.position();
  }

  // ============ Major-type 0 / 1 — integers ============

  /** Reads a CBOR unsigned int (major type 0). */
  long readUnsignedInt() {
    int initial = readInitialByte(CborMajorType.UNSIGNED_INT);
    return readArgument(initial);
  }

  /** Reads the initial byte and verifies its major type. */
  private int readInitialByte(CborMajorType expected) {
    int initial = Byte.toUnsignedInt(cursor.get());
    CborMajorType actual = CborMajorType.of(initial);
    if (actual != expected) {
      throw new CborProtocolException(
        "expected major type " +
          expected +
          " but got " +
          actual +
          " (initial byte 0x" +
          Integer.toHexString(initial) +
          ")"
      );
    }
    return initial;
  }

  /** Reads the argument bytes (0/1/2/4/8) following the initial byte. Returns the unsigned value as {@code long}. */
  private long readArgument(int initial) {
    int low = initial & 0x1F;
    if (low <= ARG_INLINE_MAX) {
      return low;
    }
    return switch (low) {
      case ARG_1BYTE -> Byte.toUnsignedLong(cursor.get());
      case ARG_2BYTE -> Short.toUnsignedLong(cursor.getShort());
      case ARG_4BYTE -> Integer.toUnsignedLong(cursor.getInt());
      case ARG_8BYTE -> {
        long v = cursor.getLong();
        if (v < 0) {
          // CBOR allows up to 2^64-1; our internal limits never produce values > Long.MAX_VALUE.
          throw new CborProtocolException(
            "8-byte CBOR argument exceeds Long.MAX_VALUE — not supported (raw=" + Long.toUnsignedString(v) + ")"
          );
        }
        yield v;
      }
      default -> throw new CborProtocolException("invalid argument encoding (low bits " + low + ")");
    };
  }

  /** Reads a CBOR negative int (major type 1) and returns the actual signed value. */
  long readNegativeInt() {
    int initial = readInitialByte(CborMajorType.NEGATIVE_INT);
    return -1L - readArgument(initial);
  }

  /** Reads any CBOR integer (major type 0 or 1) as a Java {@code long}. */
  long readInt64() {
    int initial = Byte.toUnsignedInt(cursor.get());
    CborMajorType mt = CborMajorType.of(initial);
    return switch (mt) {
      case UNSIGNED_INT -> readArgument(initial);
      case NEGATIVE_INT -> -1L - readArgument(initial);
      default -> throw new CborProtocolException("expected integer (major type 0 or 1) but got " + mt);
    };
  }

  // ============ Major-type 2 / 3 — strings ============

  /**
   * Reads a byte string (major type 2). Does not support indefinite-length on the writer side; reader tolerates it via
   * concatenation.
   */
  byte[] readBytes() {
    int initial = readInitialByte(CborMajorType.BYTE_STRING);
    if ((initial & 0x1F) == ARG_INDEFINITE) {
      return readIndefiniteByteString();
    }
    int length = checkedLength(readArgument(initial));
    byte[] out = new byte[length];
    cursor.get(out, 0, out.length);
    return out;
  }

  /** Reads a UTF-8 text string (major type 3). */
  String readText() {
    int initial = readInitialByte(CborMajorType.TEXT_STRING);
    if ((initial & 0x1F) == ARG_INDEFINITE) {
      return new String(readIndefiniteTextChunks(), StandardCharsets.UTF_8);
    }
    int length = checkedLength(readArgument(initial));
    if (length == 0) {
      return "";
    }
    byte[] buf = TEXT_SCRATCH.get();
    if (length > buf.length) {
      if (length > MAX_RETAINED_SCRATCH) {
        // One-off oversized text: read through a transient buffer and do NOT store it back, so a single
        // jumbo string can't pin multi-megabyte scratch on a pooled thread for the thread's life.
        buf = new byte[length];
      } else {
        // Grow by doubling so subsequent reads of similar size are amortised, but never retain more than
        // MAX_RETAINED_SCRATCH so a near-cap read can't inflate a pooled thread permanently.
        buf = new byte[Math.min(MAX_RETAINED_SCRATCH, Math.max(length, buf.length * 2))];
        TEXT_SCRATCH.set(buf);
      }
    }
    cursor.get(buf, 0, length);
    return new String(buf, 0, length, StandardCharsets.UTF_8);
  }

  /** Test-only: current retained scratch capacity on this thread. */
  static int retainedScratchCapacity() {
    return TEXT_SCRATCH.get().length;
  }

  /** Concatenates the chunks of an indefinite-length text string. */
  private byte[] readIndefiniteTextChunks() {
    var out = new ByteArrayOutputStream();
    while (!peekIsBreak()) {
      int initial = readInitialByte(CborMajorType.TEXT_STRING);
      int length = checkedLength(readArgument(initial));
      byte[] chunk = new byte[length];
      cursor.get(chunk, 0, chunk.length);
      out.write(chunk, 0, chunk.length);
    }
    readBreak();
    return out.toByteArray();
  }

  /** True iff the next byte is the break stop-code (0xFF). */
  boolean peekIsBreak() {
    return (cursor.hasRemaining() && Byte.toUnsignedInt(cursor.peek()) == BREAK_BYTE);
  }

  /** Consumes the break stop-code. Caller must have verified {@link #peekIsBreak()}. */
  void readBreak() {
    int b = Byte.toUnsignedInt(cursor.get());
    if (b != BREAK_BYTE) {
      throw new CborProtocolException("expected break (0xFF) but got 0x" + Integer.toHexString(b));
    }
  }

  // ============ Major-type 4 / 5 — arrays and maps ============

  /** Reads an array header. Returns the element count, or {@link #INDEFINITE_LENGTH} for an indefinite-length array. */
  long readArrayHeader() {
    int initial = readInitialByte(CborMajorType.ARRAY);
    if ((initial & 0x1F) == ARG_INDEFINITE) {
      return INDEFINITE_LENGTH;
    }
    return readArgument(initial);
  }

  /** Reads a map header. Returns the entry count, or {@link #INDEFINITE_LENGTH} for an indefinite-length map. */
  long readMapHeader() {
    int initial = readInitialByte(CborMajorType.MAP);
    if ((initial & 0x1F) == ARG_INDEFINITE) {
      return INDEFINITE_LENGTH;
    }
    return readArgument(initial);
  }

  // ============ Major-type 6 — tags ============

  /** Reads a tag number (major type 6). The next read should consume the tagged data item. */
  long readTag() {
    int initial = readInitialByte(CborMajorType.TAG);
    return readArgument(initial);
  }

  // ============ Major-type 7 — simple values, floats ============

  boolean readBool() {
    int initial = readInitialByte(CborMajorType.SIMPLE_OR_FLOAT);
    int simple = initial & 0x1F;
    return switch (simple) {
      case SIMPLE_TRUE -> true;
      case SIMPLE_FALSE -> false;
      default -> throw new CborProtocolException("expected bool (simple 20/21) but got simple " + simple);
    };
  }

  void readNull() {
    int initial = readInitialByte(CborMajorType.SIMPLE_OR_FLOAT);
    if ((initial & 0x1F) != SIMPLE_NULL) {
      throw new CborProtocolException(
        "expected null (simple 22) but got initial byte 0x" + Integer.toHexString(initial)
      );
    }
  }

  void readUndefined() {
    int initial = readInitialByte(CborMajorType.SIMPLE_OR_FLOAT);
    if ((initial & 0x1F) != SIMPLE_UNDEFINED) {
      throw new CborProtocolException(
        "expected undefined (simple 23) but got initial byte 0x" + Integer.toHexString(initial)
      );
    }
  }

  /** Reads a 32-bit float (major type 7, arg=26). Throws if the wire carries a 16- or 64-bit float instead. */
  float readFloat32() {
    int initial = readInitialByte(CborMajorType.SIMPLE_OR_FLOAT);
    if ((initial & 0x1F) != FLOAT_32_ARG) {
      throw new CborProtocolException(
        "expected float32 (arg 26) but got initial byte 0x" + Integer.toHexString(initial)
      );
    }
    return cursor.getFloat();
  }

  /** Reads a 64-bit float (major type 7, arg=27). Throws on any other arg. */
  double readFloat64() {
    int initial = readInitialByte(CborMajorType.SIMPLE_OR_FLOAT);
    if ((initial & 0x1F) != FLOAT_64_ARG) {
      throw new CborProtocolException(
        "expected float64 (arg 27) but got initial byte 0x" + Integer.toHexString(initial)
      );
    }
    return cursor.getDouble();
  }

  /**
   * Reads either a 32-bit or 64-bit float and returns the value as a {@code double}. A canonical writer down-shifts
   * float64s that round-trip through float32 — readers must accept both forms. Half-precision (arg=25) is not
   * supported.
   */
  double readFloatAny() {
    int initial = readInitialByte(CborMajorType.SIMPLE_OR_FLOAT);
    return switch (initial & 0x1F) {
      case FLOAT_32_ARG -> cursor.getFloat();
      case FLOAT_64_ARG -> cursor.getDouble();
      default -> throw new CborProtocolException(
        "expected float (arg 26 or 27) but got initial byte 0x" + Integer.toHexString(initial)
      );
    };
  }

  // ============ Skip / passthrough ============

  /** Skips one complete CBOR data item (including any nested children and tags). */
  void skipItem() {
    int initial = Byte.toUnsignedInt(cursor.get());
    CborMajorType mt = CborMajorType.of(initial);
    int low = initial & 0x1F;
    switch (mt) {
      case UNSIGNED_INT, NEGATIVE_INT -> readArgument(initial);
      case BYTE_STRING, TEXT_STRING -> {
        if (low == ARG_INDEFINITE) {
          while (!peekIsBreak()) {
            skipItem();
          }
          readBreak();
        } else {
          long len = readArgument(initial);
          cursor.skip(Math.toIntExact(len));
        }
      }
      case ARRAY -> {
        if (low == ARG_INDEFINITE) {
          while (!peekIsBreak()) {
            skipItem();
          }
          readBreak();
        } else {
          long count = readArgument(initial);
          for (long i = 0; i < count; i++) {
            skipItem();
          }
        }
      }
      case MAP -> {
        if (low == ARG_INDEFINITE) {
          while (!peekIsBreak()) {
            skipItem(); // key
            skipItem(); // value
          }
          readBreak();
        } else {
          long entries = readArgument(initial);
          for (long i = 0; i < entries; i++) {
            skipItem(); // key
            skipItem(); // value
          }
        }
      }
      case TAG -> {
        readArgument(initial);
        skipItem();
      }
      case SIMPLE_OR_FLOAT -> {
        if (low <= ARG_INLINE_MAX) {
          // inline simple value already consumed in initial byte
        } else if (low == ARG_1BYTE) {
          cursor.get();
        } else if (low == FLOAT_16_ARG) {
          cursor.skip(2);
        } else if (low == FLOAT_32_ARG) {
          cursor.skip(4);
        } else if (low == FLOAT_64_ARG) {
          cursor.skip(8);
        } else if (low == ARG_INDEFINITE) {
          // break encountered outside an indefinite item — invalid
          throw new CborProtocolException("orphan break (0xFF) outside an indefinite-length item");
        } else {
          throw new CborProtocolException("unsupported simple/float arg " + low);
        }
      }
    }
  }

  // ============ Internal helpers ============

  /** Concatenates the chunks of an indefinite-length byte string. */
  private byte[] readIndefiniteByteString() {
    var out = new ByteArrayOutputStream();
    while (!peekIsBreak()) {
      byte[] chunk = readBytes();
      out.write(chunk, 0, chunk.length);
    }
    readBreak();
    return out.toByteArray();
  }
}
