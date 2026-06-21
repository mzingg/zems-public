package dev.zems.lib.value.marshal.format.binary;

/**
 * CBOR tag numbers used by this format. State-marker tags live in the IANA private-use range (49152–65535 per RFC 8949
 * §9.1); the self-describe tag (55799) is the registered "this is CBOR" magic.
 */
final class CborTag {

  /** Registered self-describe magic (RFC 8949 §3.4.6). Three-byte sequence {@code D9 D9 F7}. */
  static final long SELF_DESCRIBE = 55799L;

  /** Wraps {@code null}: explicit-null state marker. */
  static final long STATE_NULL = 49152L;

  /** Wraps {@code null}: undefined-value state marker. */
  static final long STATE_UNDEFINED = 49153L;

  /** Wraps {@code null}: tombstone state marker. */
  static final long STATE_TOMBSTONE = 49154L;

  /** Wraps a 2-entry map {@code {0: class, 1: message}}: error state marker. */
  static final long STATE_ERROR = 49155L;

  /** Wraps {@code null}: unresolved-value state marker. */
  static final long STATE_UNRESOLVED = 49156L;

  /**
   * Wraps a record when {@code ValueIo.usingTypeVerification()} is enabled. The tag's payload is a 2-element array
   * {@code [verifyString, recordMap]} where {@code verifyString} is {@code "descriptorName@signature"} (see
   * {@code BinaryStateWriter}).
   */
  static final long TYPE_VERIFY = 49157L;

  private CborTag() {}
}
