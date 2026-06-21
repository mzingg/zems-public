package dev.zems.lib.value.marshal;

/**
 * Checksum algorithms supported by {@link Protocol#withChecksum(ChecksumAlgorithm)}.
 */
public enum ChecksumAlgorithm {
  /** No checksum. */
  NONE(""),
  SHA_256("SHA-256"),
  SHA_512("SHA-512");

  private final String javaName;

  ChecksumAlgorithm(String javaName) {
    this.javaName = javaName;
  }

  /** The standard Java name for this algorithm (e.g. {@code "SHA-256"}). */
  public String javaName() {
    return javaName;
  }
}
