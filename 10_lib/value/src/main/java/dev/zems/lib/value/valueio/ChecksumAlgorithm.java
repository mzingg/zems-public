package dev.zems.lib.value.valueio;

/** Checksum algorithms supported by {@link Protocol#withChecksum(ChecksumAlgorithm)}. */
public enum ChecksumAlgorithm {
  NONE(""),
  SHA_256("SHA-256"),
  SHA_512("SHA-512");

  private final String javaName;

  ChecksumAlgorithm(String javaName) {
    this.javaName = javaName;
  }

  /** The JCA algorithm name ({@code "SHA-256"}); the empty string for {@link #NONE}. */
  public String javaName() {
    return javaName;
  }
}
