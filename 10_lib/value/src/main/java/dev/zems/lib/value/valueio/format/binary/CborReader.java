package dev.zems.lib.value.valueio.format.binary;

import static dev.zems.lib.value.valueio.format.binary.CborConstants.ARG_1BYTE;
import static dev.zems.lib.value.valueio.format.binary.CborConstants.ARG_2BYTE;
import static dev.zems.lib.value.valueio.format.binary.CborConstants.ARG_4BYTE;
import static dev.zems.lib.value.valueio.format.binary.CborConstants.ARG_8BYTE;
import static dev.zems.lib.value.valueio.format.binary.CborConstants.ARG_INDEFINITE;
import static dev.zems.lib.value.valueio.format.binary.CborConstants.ARG_INLINE_MAX;
import static dev.zems.lib.value.valueio.format.binary.CborConstants.BREAK_BYTE;
import static dev.zems.lib.value.valueio.format.binary.CborConstants.FLOAT_16_ARG;
import static dev.zems.lib.value.valueio.format.binary.CborConstants.FLOAT_32_ARG;
import static dev.zems.lib.value.valueio.format.binary.CborConstants.FLOAT_64_ARG;
import static dev.zems.lib.value.valueio.format.binary.CborConstants.SIMPLE_FALSE;
import static dev.zems.lib.value.valueio.format.binary.CborConstants.SIMPLE_NULL;
import static dev.zems.lib.value.valueio.format.binary.CborConstants.SIMPLE_TRUE;
import static dev.zems.lib.value.valueio.format.binary.CborConstants.SIMPLE_UNDEFINED;

import dev.zems.lib.value.valueio.wire.WireConstraintViolationException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** CBOR (RFC 8949) decoder over a {@link SegmentReadCursor}; tolerates indefinite-length items for interop. */
final class CborReader {

  static final long INDEFINITE_LENGTH = -1L;

  // ThreadLocal, not an instance field: a CborReader is built per deserialise call, so an instance scratch would never
  // amortise across records.
  private static final ThreadLocal<byte[]> TEXT_SCRATCH = ThreadLocal.withInitial(() -> new byte[256]);

  static final int MAX_RETAINED_SCRATCH = 8 * 1024;

  private final SegmentReadCursor cursor;
  private final long maxStringLength;

  CborReader(SegmentReadCursor cursor, long maxStringLength) {
    this.cursor = cursor;
    this.maxStringLength = maxStringLength;
  }

  private int checkedLength(long declared) {
    if (declared > maxStringLength) {
      throw new WireConstraintViolationException("maxStringLength", maxStringLength, declared);
    }
    // maxStringLength is an int on WireConstraints, so a length that passes the check fits the cast.
    return (int) declared;
  }

  CborMajorType peekMajorType() {
    return CborMajorType.of(Byte.toUnsignedInt(cursor.peek()));
  }

  int peekInitialByte() {
    return Byte.toUnsignedInt(cursor.peek());
  }

  boolean hasRemaining() {
    return cursor.hasRemaining();
  }

  long position() {
    return cursor.position();
  }

  long readUnsignedInt() {
    int initial = readInitialByte(CborMajorType.UNSIGNED_INT);
    return readArgument(initial);
  }

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
          // CBOR arguments go up to 2^64-1 but Java has no unsigned long; a value above Long.MAX_VALUE is rejected
          // rather than wrapped negative.
          throw new CborProtocolException(
            "8-byte CBOR argument exceeds Long.MAX_VALUE — not supported (raw=" + Long.toUnsignedString(v) + ")"
          );
        }
        yield v;
      }
      default -> throw new CborProtocolException("invalid argument encoding (low bits " + low + ")");
    };
  }

  long readNegativeInt() {
    int initial = readInitialByte(CborMajorType.NEGATIVE_INT);
    return -1L - readArgument(initial);
  }

  long readInt64() {
    int initial = Byte.toUnsignedInt(cursor.get());
    CborMajorType mt = CborMajorType.of(initial);
    return switch (mt) {
      case UNSIGNED_INT -> readArgument(initial);
      case NEGATIVE_INT -> -1L - readArgument(initial);
      default -> throw new CborProtocolException("expected integer (major type 0 or 1) but got " + mt);
    };
  }

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
        // Not stored back: a single jumbo string would otherwise pin a multi-megabyte scratch on a pooled thread for
        // the thread's life.
        buf = new byte[length];
      } else {
        buf = new byte[Math.min(MAX_RETAINED_SCRATCH, Math.max(length, buf.length * 2))];
        TEXT_SCRATCH.set(buf);
      }
    }
    cursor.get(buf, 0, length);
    return new String(buf, 0, length, StandardCharsets.UTF_8);
  }

  static int retainedScratchCapacity() {
    return TEXT_SCRATCH.get().length;
  }

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

  boolean peekIsBreak() {
    return cursor.hasRemaining() && Byte.toUnsignedInt(cursor.peek()) == BREAK_BYTE;
  }

  void readBreak() {
    int b = Byte.toUnsignedInt(cursor.get());
    if (b != BREAK_BYTE) {
      throw new CborProtocolException("expected break (0xFF) but got 0x" + Integer.toHexString(b));
    }
  }

  long readArrayHeader() {
    int initial = readInitialByte(CborMajorType.ARRAY);
    if ((initial & 0x1F) == ARG_INDEFINITE) {
      return INDEFINITE_LENGTH;
    }
    return readArgument(initial);
  }

  long readMapHeader() {
    int initial = readInitialByte(CborMajorType.MAP);
    if ((initial & 0x1F) == ARG_INDEFINITE) {
      return INDEFINITE_LENGTH;
    }
    return readArgument(initial);
  }

  long readTag() {
    int initial = readInitialByte(CborMajorType.TAG);
    return readArgument(initial);
  }

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

  float readFloat32() {
    int initial = readInitialByte(CborMajorType.SIMPLE_OR_FLOAT);
    if ((initial & 0x1F) != FLOAT_32_ARG) {
      throw new CborProtocolException(
        "expected float32 (arg 26) but got initial byte 0x" + Integer.toHexString(initial)
      );
    }
    return cursor.getFloat();
  }

  double readFloat64() {
    int initial = readInitialByte(CborMajorType.SIMPLE_OR_FLOAT);
    if ((initial & 0x1F) != FLOAT_64_ARG) {
      throw new CborProtocolException(
        "expected float64 (arg 27) but got initial byte 0x" + Integer.toHexString(initial)
      );
    }
    return cursor.getDouble();
  }

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
            skipItem();
            skipItem();
          }
          readBreak();
        } else {
          long entries = readArgument(initial);
          for (long i = 0; i < entries; i++) {
            skipItem();
            skipItem();
          }
        }
      }
      case TAG -> {
        readArgument(initial);
        skipItem();
      }
      case SIMPLE_OR_FLOAT -> {
        if (low <= ARG_INLINE_MAX) {
          // RFC 8949 §3.3: simple values 0–23 have no payload bytes after the initial byte.
        } else if (low == ARG_1BYTE) {
          cursor.get();
        } else if (low == FLOAT_16_ARG) {
          cursor.skip(2);
        } else if (low == FLOAT_32_ARG) {
          cursor.skip(4);
        } else if (low == FLOAT_64_ARG) {
          cursor.skip(8);
        } else if (low == ARG_INDEFINITE) {
          throw new CborProtocolException("orphan break (0xFF) outside an indefinite-length item");
        } else {
          throw new CborProtocolException("unsupported simple/float arg " + low);
        }
      }
    }
  }

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
