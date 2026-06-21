package dev.zems.lib.value.marshal.format.binary;

/**
 * Constants for the CBOR initial-byte encoding (RFC 8949 §3).
 *
 * <p>
 * Every CBOR data item starts with one initial byte that packs the major type (high 3 bits) and an
 * additional-information argument (low 5 bits). When the additional information is 0–23 the argument value is the low 5
 * bits directly; values 24–27 indicate that 1/2/4/8 unsigned big-endian bytes follow as the argument; 28–30 are
 * reserved; 31 signals an indefinite-length item (for major types 2–5 and 7) or — when paired with major type 7 — the
 * {@code break} stop code (0xFF) that terminates the indefinite item.
 */
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

  // Major-type 7 simple-value assignments (RFC 8949 §3.3 / §3.4).
  static final int SIMPLE_FALSE = 20;
  static final int SIMPLE_TRUE = 21;
  static final int SIMPLE_NULL = 22;
  static final int SIMPLE_UNDEFINED = 23;

  // Major-type 7 float marker arguments. (16-bit shares additional-info 25 with ARG_2BYTE.)
  static final int FLOAT_16_ARG = 25;
  static final int FLOAT_32_ARG = 26;
  static final int FLOAT_64_ARG = 27;

  private CborConstants() {}
}
