package dev.zems.lib.value.valueio.format.binary;

/** Hex-encoded CBOR data items shared by the codec tests. */
final class CborHex {

  /** RFC 8949 §3.1: major type 1, argument 0 encodes -1. */
  static final String NEGATIVE_ONE = "20";

  /** RFC 8949 §3.1: major type 1, argument 23 encodes -24, the largest inline negative. */
  static final String NEGATIVE_TWENTY_FOUR = "37";

  /** RFC 8949 §3.1: major type 1, one-byte argument 24 encodes -25. */
  static final String NEGATIVE_TWENTY_FIVE = "3818";

  /** RFC 8949 §3.1: major type 1, one-byte argument 99 encodes -100. */
  static final String NEGATIVE_ONE_HUNDRED = "3863";

  /** RFC 8949 §3.1: major type 1, eight-byte argument Long.MAX_VALUE encodes Long.MIN_VALUE. */
  static final String LONG_MIN = "3b7fffffffffffffff";

  /** RFC 8949 §3.3: single-precision 3.14f. */
  static final String FLOAT32_PI = "fa4048f5c3";

  /** RFC 8949 §3.3: double-precision 3.14. */
  static final String FLOAT64_PI = "fb40091eb851eb851f";

  /** RFC 8949 §3.3: single-precision 1.0. */
  static final String FLOAT32_ONE = "fa3f800000";

  /** RFC 8949 §3.3: half-precision 1.0. */
  static final String FLOAT16_ONE = "f93c00";

  /** RFC 8949 §3.3: simple value 21, {@code true}. */
  static final String TRUE = "f5";

  /** RFC 8949 §3.3: simple value 22, {@code null}. */
  static final String NULL = "f6";

  /** RFC 8949 §3.1: unsigned integer 0. */
  static final String ZERO = "00";

  /** RFC 8949 §3.1: text string of 5 bytes, "hello". */
  static final String TEXT_HELLO = "6568656c6c6f";

  /** RFC 8949 §3.1: text string of 6 bytes, "héllo" (é is two UTF-8 bytes). */
  static final String TEXT_HELLO_ACCENTED = "6668c3a96c6c6f";

  /** RFC 8949 §3.1: text string of 1 byte, "x". */
  static final String TEXT_X = "6178";

  /** RFC 8949 §3.2.3: indefinite-length text of the chunks "he" and "llo", closed by the break byte. */
  static final String TEXT_HELLO_CHUNKED = "7f626865636c6c6fff";

  /** RFC 8949 §3.1: map header with one entry. */
  static final String MAP_ONE_ENTRY = "a1";

  /** RFC 8949 §3.4: tag 0. */
  static final String TAG_ZERO = "c0";

  /** RFC 8949 §3.4.6: self-describe tag 55799, encoded with a two-byte argument. */
  static final String SELF_DESCRIBE_TAG = "d9d9f7";

  /** RFC 8949 §3.4: tag 49152 ({@code CborTag.STATE_NULL}), encoded with a two-byte argument. */
  static final String STATE_NULL_TAG = "d9c000";

  /** RFC 8949 §3.1: text-string header with a four-byte argument declaring 1 GiB, and no payload behind it. */
  static final String HUGE_TEXT_HEADER = "7a40000000";

  /** RFC 8949 §3.1: byte-string header with a four-byte argument declaring 1 GiB, and no payload behind it. */
  static final String HUGE_BYTES_HEADER = "5a40000000";

  private CborHex() {}
}
