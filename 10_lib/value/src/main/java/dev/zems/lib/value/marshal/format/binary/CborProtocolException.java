package dev.zems.lib.value.marshal.format.binary;

/**
 * Thrown when a CBOR decode encounters bytes that don't match the expected shape (wrong major type, malformed argument,
 * truncated indefinite-length item, invalid simple value, etc.). Extends {@link IllegalStateException} so callers that
 * already catch the broader exception type pre-CBOR continue to work.
 */
final class CborProtocolException extends IllegalStateException {

  CborProtocolException(String message) {
    super(message);
  }
}
