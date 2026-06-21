package dev.zems.lib.value.marshal.wire.v1;

import dev.zems.lib.value.marshal.Protocol;
import java.util.Objects;

/**
 * Envelope terminator for {@link Protocol.V1}: byte count and hex-encoded checksum digest. Written/read once on close.
 *
 * <p>
 * <b>Direct invocation of the canonical constructor is unsupported.</b> Records require a public
 * canonical constructor, but only the protocol envelope is meant to produce {@code Terminator} instances. Calling
 * {@code new Terminator(...)} directly may produce terminators that violate library invariants — this is not detected
 * at runtime.
 */
public record Terminator(long byteCount, String checksumHex) {
  public Terminator {
    Objects.requireNonNull(checksumHex, "checksumHex must not be null");
  }
}
