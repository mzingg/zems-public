package dev.zems.lib.value.marshal.wire.v1;

import dev.zems.lib.value.marshal.ChecksumAlgorithm;
import dev.zems.lib.value.marshal.Protocol;
import java.util.Objects;

/**
 * Envelope header for {@link Protocol.V1}: protocol version, envelope {@link Protocol.Mode mode}, type-verification
 * flag, and checksum algorithm. Read once on writer/reader open.
 *
 * <p>
 * <b>Direct invocation of the canonical constructor is unsupported.</b> Records require a public
 * canonical constructor, but only the protocol envelope is meant to produce {@code Header} instances. Calling
 * {@code new Header(...)} directly may produce headers that violate library invariants — this is not detected at
 * runtime.
 */
public record Header(int version, Protocol.Mode mode, boolean typeVerification, ChecksumAlgorithm checksum) {
  public Header {
    Objects.requireNonNull(mode, "mode must not be null");
    Objects.requireNonNull(checksum, "checksum must not be null");
  }
}
