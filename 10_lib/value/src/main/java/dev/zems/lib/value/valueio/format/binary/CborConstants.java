package dev.zems.lib.value.valueio.format.binary;

/** Constants of the CBOR initial-byte encoding (RFC 8949 §3). */
final class CborConstants {

  /** Maximum value the additional-information field carries inline (no following bytes). */
  static final int ARG_INLINE_MAX = 23;

  /** Argument: one unsigned byte follows. */
  static final int ARG_1BYTE = 24;

  /** Argument: two unsigned big-endian bytes follow. */
  static final int ARG_2BYTE = 25;

  /** Argument: four unsigned big-endian bytes follow. */
  static final int ARG_4BYTE = 26;

  /** Argument: eight unsigned big-endian bytes follow. */
  static final int ARG_8BYTE = 27;

  /** Indefinite-length marker; valid for byte/text strings, arrays, maps; and the break code. */
  static final int ARG_INDEFINITE = 31;

  /** Break stop-code byte (major type 7 + arg=31). Terminates indefinite-length items. */
  static final int BREAK_BYTE = 0xFF;

  /** RFC 8949 §3.3: simple value {@code false}. */
  static final int SIMPLE_FALSE = 20;

  /** RFC 8949 §3.3: simple value {@code true}. */
  static final int SIMPLE_TRUE = 21;

  /** RFC 8949 §3.3: simple value {@code null}. */
  static final int SIMPLE_NULL = 22;

  /** RFC 8949 §3.3: simple value {@code undefined}. */
  static final int SIMPLE_UNDEFINED = 23;

  /** RFC 8949 §3.3: IEEE 754 half-precision float follows (same additional information as {@link #ARG_2BYTE}). */
  static final int FLOAT_16_ARG = 25;

  /** RFC 8949 §3.3: IEEE 754 single-precision float follows. */
  static final int FLOAT_32_ARG = 26;

  /** RFC 8949 §3.3: IEEE 754 double-precision float follows. */
  static final int FLOAT_64_ARG = 27;

  private CborConstants() {}
}
