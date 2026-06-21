package dev.zems.lib.value.marshal.format.binary;

/**
 * The eight CBOR major types (RFC 8949 §3.1). The enum ordinal matches the wire major-type number — keep the
 * declaration order in sync.
 */
enum CborMajorType {
  /** 0 — unsigned integer (argument is the value). */
  UNSIGNED_INT,
  /** 1 — negative integer (value = −1 − argument). */
  NEGATIVE_INT,
  /** 2 — byte string (argument is the byte count; payload follows). */
  BYTE_STRING,
  /** 3 — UTF-8 text string (argument is the byte count). */
  TEXT_STRING,
  /** 4 — array of data items (argument is the element count). */
  ARRAY,
  /** 5 — map of key/value pairs (argument is the pair count). */
  MAP,
  /** 6 — tagged data item (argument is the tag number; one data item follows). */
  TAG,
  /** 7 — simple values, floats, and the break stop-code. */
  SIMPLE_OR_FLOAT;

  // Enum.values() returns a fresh clone per call; cache it for the read hot path.
  private static final CborMajorType[] VALUES = values();

  /** Extracts the major type from a CBOR initial byte. */
  static CborMajorType of(int initialByte) {
    return VALUES[(initialByte >>> 5) & 0x07];
  }

  /** Returns the high 3 bits of the initial byte for this major type. */
  int highBits() {
    return ordinal() << 5;
  }
}
