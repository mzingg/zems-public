package dev.zems.lib.value.valueio.format.binary;

/** Thrown when CBOR bytes do not match the expected shape: wrong major type, malformed argument or simple value. */
final class CborProtocolException extends IllegalStateException {

  CborProtocolException(String message) {
    super(message);
  }
}
