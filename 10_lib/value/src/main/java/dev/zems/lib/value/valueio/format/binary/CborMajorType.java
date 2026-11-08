package dev.zems.lib.value.valueio.format.binary;

/** The eight CBOR major types (RFC 8949 §3.1); the ordinal is the wire major-type number. */
enum CborMajorType {
  UNSIGNED_INT,
  NEGATIVE_INT,
  BYTE_STRING,
  TEXT_STRING,
  ARRAY,
  MAP,
  TAG,
  SIMPLE_OR_FLOAT;

  // Enum.values() clones the array on every call, and of() runs once per data item on the read path.
  private static final CborMajorType[] VALUES = values();

  static CborMajorType of(int initialByte) {
    return VALUES[(initialByte >>> 5) & 0x07];
  }

  int highBits() {
    return ordinal() << 5;
  }
}
