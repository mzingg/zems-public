package dev.zems.lib.value.valueio.format.binary;

/** CBOR tag numbers used by the binary format; state markers sit in the IANA private-use range (RFC 8949 §9.1). */
final class CborTag {

  /** RFC 8949 §3.4.6: self-describe tag, encoded as the three bytes {@code D9 D9 F7}. */
  static final long SELF_DESCRIBE = 55799L;

  static final long STATE_NULL = 49152L;

  static final long STATE_UNDEFINED = 49153L;

  static final long STATE_TOMBSTONE = 49154L;

  static final long STATE_ERROR = 49155L;

  static final long STATE_UNRESOLVED = 49156L;

  static final long TYPE_VERIFY = 49157L;

  static final long STATE_UNSUPPORTED = 49158L;

  private CborTag() {}
}
