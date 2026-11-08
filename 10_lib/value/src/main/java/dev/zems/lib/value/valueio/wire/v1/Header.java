package dev.zems.lib.value.valueio.wire.v1;

import dev.zems.lib.value.valueio.ChecksumAlgorithm;
import dev.zems.lib.value.valueio.Protocol;
import java.util.Objects;

/**
 * Envelope header of {@link Protocol.V1}: protocol version, {@link Protocol.Mode mode}, type-verification flag and
 * checksum algorithm. Only the protocol envelope constructs instances; calling the constructor directly is unsupported.
 */
public record Header(int version, Protocol.Mode mode, boolean typeVerification, ChecksumAlgorithm checksum) {
  public Header {
    Objects.requireNonNull(mode, "mode must not be null");
    Objects.requireNonNull(checksum, "checksum must not be null");
  }
}
