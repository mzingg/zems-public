package dev.zems.lib.value.valueio.wire.v1;

import dev.zems.lib.value.valueio.Protocol;
import java.util.Objects;

/**
 * Envelope terminator of {@link Protocol.V1}: byte count and hex-encoded checksum digest. Only the protocol envelope
 * constructs instances; calling the constructor directly is unsupported.
 */
public record Terminator(long byteCount, String checksumHex) {
  public Terminator {
    Objects.requireNonNull(checksumHex, "checksumHex must not be null");
  }
}
