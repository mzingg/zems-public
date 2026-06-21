package dev.zems.lib.value.marshal.format.binary;

import static dev.zems.lib.value.marshal.format.binary.CborConstants.ARG_1BYTE;
import static dev.zems.lib.value.marshal.format.binary.CborConstants.ARG_2BYTE;
import static dev.zems.lib.value.marshal.format.binary.CborConstants.ARG_4BYTE;
import static dev.zems.lib.value.marshal.format.binary.CborConstants.ARG_8BYTE;
import static dev.zems.lib.value.marshal.format.binary.CborConstants.ARG_INLINE_MAX;
import static dev.zems.lib.value.marshal.format.binary.CborConstants.FLOAT_32_ARG;
import static dev.zems.lib.value.marshal.format.binary.CborConstants.FLOAT_64_ARG;
import static dev.zems.lib.value.marshal.format.binary.CborConstants.SIMPLE_FALSE;
import static dev.zems.lib.value.marshal.format.binary.CborConstants.SIMPLE_NULL;
import static dev.zems.lib.value.marshal.format.binary.CborConstants.SIMPLE_TRUE;
import static dev.zems.lib.value.marshal.format.binary.CborConstants.SIMPLE_UNDEFINED;

/**
 * Canonical CBOR encoder (RFC 8949 §4.2.1, Core Deterministic Encoding) on top of a {@link SegmentWriteCursor}. Always
 * emits the shortest valid argument byte sequence so the resulting bytes are stable under refactoring — required
 * because {@code ChecksumComputation} digests logical values rather than wire bytes, but a non-canonical encode would
 * still shift wire-level fixtures.
 *
 * <p>
 * The encoder writes definite-length items only.
 */
final class CborWriter {

  // Replacement character (U+FFFD) UTF-8 bytes, used for unpaired surrogates — matches the
  // behaviour of String.getBytes(UTF_8) which defaults to CodingErrorAction.REPLACE.
  private static final byte REPLACEMENT_B0 = (byte) 0xEF;
  private static final byte REPLACEMENT_B1 = (byte) 0xBF;
  private static final byte REPLACEMENT_B2 = (byte) 0xBD;

  /**
   * Thread-scoped reusable buffer for {@link #writeText(String)} UTF-8 encoding. ThreadLocal rather than per-instance
   * because {@code CborWriter} is constructed per-serialize call (~10⁷ writers/prep run) so a per-instance scratch can
   * never amortise — the first iteration of N3 paid 41.7 GB of scratch allocation in prep for that reason. ThreadLocal
   * scope keeps one buffer per thread for the life of the thread, growing once to the largest text written. Starting
   * size 256 B covers typical attribute keys/values; growth is exponential.
   */
  private static final ThreadLocal<byte[]> TEXT_SCRATCH = ThreadLocal.withInitial(() -> new byte[256]);

  /**
   * Largest scratch buffer retained across writes on a thread. A text whose worst-case encoding is larger than this
   * writes through a one-off buffer that is not stored back, so a single jumbo string can't pin scratch on a pooled
   * thread for the thread's life.
   */
  static final int MAX_RETAINED_SCRATCH = 8 * 1024;

  private final CborByteOutput cursor;

  CborWriter(CborByteOutput cursor) {
    this.cursor = cursor;
  }

  /**
   * Encodes {@code s} as UTF-8 into {@code dst} starting at offset 0. Returns the number of bytes written. Matches
   * {@code String.getBytes(UTF_8)} byte-for-byte, including the replacement character (U+FFFD, {@code EF BF BD}) for
   * unpaired surrogates.
   */
  private static int encodeUtf8(String s, int charLen, byte[] dst) {
    int o = 0;
    int i = 0;
    while (i < charLen) {
      int c = s.charAt(i);
      if (c < 0x80) {
        dst[o++] = (byte) c;
        i++;
      } else if (c < 0x800) {
        dst[o++] = (byte) (0xC0 | (c >> 6));
        dst[o++] = (byte) (0x80 | (c & 0x3F));
        i++;
      } else if (c < 0xD800 || c > 0xDFFF) {
        dst[o++] = (byte) (0xE0 | (c >> 12));
        dst[o++] = (byte) (0x80 | ((c >> 6) & 0x3F));
        dst[o++] = (byte) (0x80 | (c & 0x3F));
        i++;
      } else if (c <= 0xDBFF && i + 1 < charLen) {
        // High surrogate — check for valid low surrogate.
        char low = s.charAt(i + 1);
        if (low >= 0xDC00 && low <= 0xDFFF) {
          int cp = 0x10000 + ((c - 0xD800) << 10) + (low - 0xDC00);
          dst[o++] = (byte) (0xF0 | (cp >> 18));
          dst[o++] = (byte) (0x80 | ((cp >> 12) & 0x3F));
          dst[o++] = (byte) (0x80 | ((cp >> 6) & 0x3F));
          dst[o++] = (byte) (0x80 | (cp & 0x3F));
          i += 2;
        } else {
          // High surrogate not followed by low → replacement, consume just the high.
          dst[o++] = REPLACEMENT_B0;
          dst[o++] = REPLACEMENT_B1;
          dst[o++] = REPLACEMENT_B2;
          i++;
        }
      } else {
        // Lone low surrogate or high surrogate at end → replacement, consume one char.
        dst[o++] = REPLACEMENT_B0;
        dst[o++] = REPLACEMENT_B1;
        dst[o++] = REPLACEMENT_B2;
        i++;
      }
    }
    return o;
  }

  // ============ Major-type 0 / 1 — integers ============

  /** Writes a non-negative integer as a CBOR unsigned int (major type 0). */
  void writeUnsignedInt(long value) {
    if (value < 0) {
      throw new IllegalArgumentException("writeUnsignedInt requires a non-negative value, got " + value);
    }
    writeInitialByte(CborMajorType.UNSIGNED_INT, value);
  }

  /**
   * Emits the initial byte plus argument bytes for a non-major-type-7 item, choosing the shortest encoding per RFC 8949
   * §4.2.1 rule 1. {@code argument} is treated as unsigned 64-bit — values 0..Long.MAX_VALUE are encoded directly;
   * negative {@code long}s are not valid here (use the type-specific writer).
   */
  private void writeInitialByte(CborMajorType majorType, long argument) {
    if (argument < 0) {
      throw new IllegalArgumentException("argument must be non-negative when treated as unsigned, got " + argument);
    }
    int high = majorType.highBits();
    if (argument <= ARG_INLINE_MAX) {
      cursor.put((byte) (high | (int) argument));
    } else if (argument <= 0xFFL) {
      cursor.put((byte) (high | ARG_1BYTE));
      cursor.put((byte) argument);
    } else if (argument <= 0xFFFFL) {
      cursor.put((byte) (high | ARG_2BYTE));
      cursor.putShort((short) argument);
    } else if (argument <= 0xFFFFFFFFL) {
      cursor.put((byte) (high | ARG_4BYTE));
      cursor.putInt((int) argument);
    } else {
      cursor.put((byte) (high | ARG_8BYTE));
      cursor.putLong(argument);
    }
  }

  /** Writes a negative integer as a CBOR negative int (major type 1). The argument carries {@code -1 - value}. */
  void writeNegativeInt(long value) {
    if (value >= 0) {
      throw new IllegalArgumentException("writeNegativeInt requires a negative value, got " + value);
    }
    writeInitialByte(CborMajorType.NEGATIVE_INT, -1L - value);
  }

  /** Writes a Java {@code long} as CBOR using the correct sign-aware major type. */
  void writeInt64(long value) {
    if (value >= 0) {
      writeInitialByte(CborMajorType.UNSIGNED_INT, value);
    } else {
      writeInitialByte(CborMajorType.NEGATIVE_INT, -1L - value);
    }
  }

  // ============ Major-type 2 / 3 — strings ============

  /** Writes a byte string (major type 2). */
  void writeBytes(byte[] data) {
    writeBytes(data, 0, data.length);
  }

  void writeBytes(byte[] data, int offset, int length) {
    writeInitialByte(CborMajorType.BYTE_STRING, length);
    cursor.put(data, offset, length);
  }

  /** Writes a UTF-8 text string (major type 3). */
  void writeText(String s) {
    int charLen = s.length();
    if (charLen == 0) {
      writeInitialByte(CborMajorType.TEXT_STRING, 0);
      return;
    }
    // Worst-case UTF-8 is 3 bytes per BMP char; surrogate pairs encode as 4 bytes for 2 chars
    // (2 B/char effective), so * 3 is always a safe upper bound.
    int maxBytes = charLen * 3;
    byte[] buf = TEXT_SCRATCH.get();
    if (maxBytes > buf.length) {
      if (maxBytes > MAX_RETAINED_SCRATCH) {
        // One-off oversized text: encode through a transient buffer and do NOT store it back, so a single
        // jumbo string can't pin multi-megabyte scratch on a pooled thread for the thread's life.
        buf = new byte[maxBytes];
      } else {
        // Grow by doubling (amortised), but never retain more than MAX_RETAINED_SCRATCH.
        buf = new byte[Math.min(MAX_RETAINED_SCRATCH, Math.max(maxBytes, buf.length * 2))];
        TEXT_SCRATCH.set(buf);
      }
    }
    int byteLen = encodeUtf8(s, charLen, buf);
    writeInitialByte(CborMajorType.TEXT_STRING, byteLen);
    cursor.put(buf, 0, byteLen);
  }

  /** Test-only: current retained scratch capacity on this thread. */
  static int retainedScratchCapacity() {
    return TEXT_SCRATCH.get().length;
  }

  // ============ Major-type 4 / 5 — arrays and maps ============

  /** Writes a definite-length array header (major type 4). */
  void writeArrayHeader(long elements) {
    if (elements < 0) {
      throw new IllegalArgumentException("array length must be non-negative, got " + elements);
    }
    writeInitialByte(CborMajorType.ARRAY, elements);
  }

  /** Writes a definite-length map header (major type 5). */
  void writeMapHeader(long entries) {
    if (entries < 0) {
      throw new IllegalArgumentException("map entries must be non-negative, got " + entries);
    }
    writeInitialByte(CborMajorType.MAP, entries);
  }

  // ============ Major-type 6 — tags ============

  /** Writes a tag number (major type 6). The next call must write the tagged data item. */
  void writeTag(long tag) {
    if (tag < 0) {
      throw new IllegalArgumentException("tag number must be non-negative, got " + tag);
    }
    writeInitialByte(CborMajorType.TAG, tag);
  }

  // ============ Major-type 7 — simple values, floats, booleans ============

  void writeBool(boolean value) {
    cursor.put((byte) (CborMajorType.SIMPLE_OR_FLOAT.highBits() | (value ? SIMPLE_TRUE : SIMPLE_FALSE)));
  }

  void writeNull() {
    cursor.put((byte) (CborMajorType.SIMPLE_OR_FLOAT.highBits() | SIMPLE_NULL));
  }

  void writeUndefined() {
    cursor.put((byte) (CborMajorType.SIMPLE_OR_FLOAT.highBits() | SIMPLE_UNDEFINED));
  }

  /**
   * Canonical-encoding float64 writer (RFC 8949 §4.2.2): if {@code value} round-trips losslessly through {@code float},
   * emit the 32-bit form; otherwise emit the 64-bit form. NaN is left untouched in whichever representation the caller
   * supplied.
   */
  void writeFloat64Canonical(double value) {
    if (Double.isNaN(value)) {
      writeFloat64(value);
      return;
    }
    float asFloat = (float) value;
    if ((double) asFloat == value) {
      writeFloat32(asFloat);
    } else {
      writeFloat64(value);
    }
  }

  /** Writes a 64-bit float (major type 7, arg=27). */
  void writeFloat64(double value) {
    cursor.put((byte) (CborMajorType.SIMPLE_OR_FLOAT.highBits() | FLOAT_64_ARG));
    cursor.putDouble(value);
  }

  /** Writes a 32-bit float (major type 7, arg=26). */
  void writeFloat32(float value) {
    cursor.put((byte) (CborMajorType.SIMPLE_OR_FLOAT.highBits() | FLOAT_32_ARG));
    cursor.putFloat(value);
  }
}
