package dev.zems.lib.value.valueio.format.binary;

import static dev.zems.lib.value.valueio.format.binary.CborConstants.ARG_1BYTE;
import static dev.zems.lib.value.valueio.format.binary.CborConstants.ARG_2BYTE;
import static dev.zems.lib.value.valueio.format.binary.CborConstants.ARG_4BYTE;
import static dev.zems.lib.value.valueio.format.binary.CborConstants.ARG_8BYTE;
import static dev.zems.lib.value.valueio.format.binary.CborConstants.ARG_INLINE_MAX;
import static dev.zems.lib.value.valueio.format.binary.CborConstants.FLOAT_32_ARG;
import static dev.zems.lib.value.valueio.format.binary.CborConstants.FLOAT_64_ARG;
import static dev.zems.lib.value.valueio.format.binary.CborConstants.SIMPLE_FALSE;
import static dev.zems.lib.value.valueio.format.binary.CborConstants.SIMPLE_NULL;
import static dev.zems.lib.value.valueio.format.binary.CborConstants.SIMPLE_TRUE;
import static dev.zems.lib.value.valueio.format.binary.CborConstants.SIMPLE_UNDEFINED;

/** Canonical CBOR encoder (RFC 8949 §4.2.1) over a {@link CborByteOutput}; writes definite-length items only. */
final class CborWriter {

  // U+FFFD as UTF-8, emitted for unpaired surrogates so the bytes match String.getBytes(UTF_8), which replaces rather
  // than throws.
  private static final byte REPLACEMENT_B0 = (byte) 0xEF;
  private static final byte REPLACEMENT_B1 = (byte) 0xBF;
  private static final byte REPLACEMENT_B2 = (byte) 0xBD;

  /** RFC 3629: a UTF-16 code unit encodes to at most 3 UTF-8 bytes; a surrogate pair is 4 bytes for 2 units. */
  private static final int MAX_UTF8_BYTES_PER_CHAR = 3;

  // ThreadLocal, not an instance field: a CborWriter is built per serialise call, so an instance scratch would never
  // amortise (measured at tens of gigabytes of scratch allocation over one benchmark preparation run).
  private static final ThreadLocal<byte[]> TEXT_SCRATCH = ThreadLocal.withInitial(() -> new byte[256]);

  static final int MAX_RETAINED_SCRATCH = 8 * 1024;

  private final CborByteOutput cursor;

  CborWriter(CborByteOutput cursor) {
    this.cursor = cursor;
  }

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
        char low = s.charAt(i + 1);
        if (low >= 0xDC00 && low <= 0xDFFF) {
          int cp = 0x10000 + ((c - 0xD800) << 10) + (low - 0xDC00);
          dst[o++] = (byte) (0xF0 | (cp >> 18));
          dst[o++] = (byte) (0x80 | ((cp >> 12) & 0x3F));
          dst[o++] = (byte) (0x80 | ((cp >> 6) & 0x3F));
          dst[o++] = (byte) (0x80 | (cp & 0x3F));
          i += 2;
        } else {
          dst[o++] = REPLACEMENT_B0;
          dst[o++] = REPLACEMENT_B1;
          dst[o++] = REPLACEMENT_B2;
          i++;
        }
      } else {
        dst[o++] = REPLACEMENT_B0;
        dst[o++] = REPLACEMENT_B1;
        dst[o++] = REPLACEMENT_B2;
        i++;
      }
    }
    return o;
  }

  void writeUnsignedInt(long value) {
    if (value < 0) {
      throw new IllegalArgumentException("writeUnsignedInt requires a non-negative value, got " + value);
    }
    writeInitialByte(CborMajorType.UNSIGNED_INT, value);
  }

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

  void writeNegativeInt(long value) {
    if (value >= 0) {
      throw new IllegalArgumentException("writeNegativeInt requires a negative value, got " + value);
    }
    writeInitialByte(CborMajorType.NEGATIVE_INT, -1L - value);
  }

  void writeInt64(long value) {
    if (value >= 0) {
      writeInitialByte(CborMajorType.UNSIGNED_INT, value);
    } else {
      writeInitialByte(CborMajorType.NEGATIVE_INT, -1L - value);
    }
  }

  void writeBytes(byte[] data) {
    writeBytes(data, 0, data.length);
  }

  void writeBytes(byte[] data, int offset, int length) {
    writeInitialByte(CborMajorType.BYTE_STRING, length);
    cursor.put(data, offset, length);
  }

  void writeText(String s) {
    int charLen = s.length();
    if (charLen == 0) {
      writeInitialByte(CborMajorType.TEXT_STRING, 0);
      return;
    }
    int maxBytes = charLen * MAX_UTF8_BYTES_PER_CHAR;
    byte[] buf = TEXT_SCRATCH.get();
    if (maxBytes > buf.length) {
      if (maxBytes > MAX_RETAINED_SCRATCH) {
        // Not stored back: a single jumbo string would otherwise pin a multi-megabyte scratch on a pooled thread for
        // the thread's life.
        buf = new byte[maxBytes];
      } else {
        buf = new byte[Math.min(MAX_RETAINED_SCRATCH, Math.max(maxBytes, buf.length * 2))];
        TEXT_SCRATCH.set(buf);
      }
    }
    int byteLen = encodeUtf8(s, charLen, buf);
    writeInitialByte(CborMajorType.TEXT_STRING, byteLen);
    cursor.put(buf, 0, byteLen);
  }

  static int retainedScratchCapacity() {
    return TEXT_SCRATCH.get().length;
  }

  void writeArrayHeader(long elements) {
    if (elements < 0) {
      throw new IllegalArgumentException("array length must be non-negative, got " + elements);
    }
    writeInitialByte(CborMajorType.ARRAY, elements);
  }

  void writeMapHeader(long entries) {
    if (entries < 0) {
      throw new IllegalArgumentException("map entries must be non-negative, got " + entries);
    }
    writeInitialByte(CborMajorType.MAP, entries);
  }

  void writeTag(long tag) {
    if (tag < 0) {
      throw new IllegalArgumentException("tag number must be non-negative, got " + tag);
    }
    writeInitialByte(CborMajorType.TAG, tag);
  }

  void writeBool(boolean value) {
    cursor.put((byte) (CborMajorType.SIMPLE_OR_FLOAT.highBits() | (value ? SIMPLE_TRUE : SIMPLE_FALSE)));
  }

  void writeNull() {
    cursor.put((byte) (CborMajorType.SIMPLE_OR_FLOAT.highBits() | SIMPLE_NULL));
  }

  void writeUndefined() {
    cursor.put((byte) (CborMajorType.SIMPLE_OR_FLOAT.highBits() | SIMPLE_UNDEFINED));
  }

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

  void writeFloat64(double value) {
    cursor.put((byte) (CborMajorType.SIMPLE_OR_FLOAT.highBits() | FLOAT_64_ARG));
    cursor.putDouble(value);
  }

  void writeFloat32(float value) {
    cursor.put((byte) (CborMajorType.SIMPLE_OR_FLOAT.highBits() | FLOAT_32_ARG));
    cursor.putFloat(value);
  }
}
