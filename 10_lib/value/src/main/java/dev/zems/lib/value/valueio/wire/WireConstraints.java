package dev.zems.lib.value.valueio.wire;

import dev.zems.lib.value.valueio.Protocol;
import java.util.Objects;

/**
 * Wire-level safety bounds, enforced by readers against untrusted input and by writers before emitting. A
 * {@link Protocol.V1} carries one instance and defaults to {@link #SECURE_DEFAULTS}.
 *
 * @param maxStringLength       chars per string in JSON; UTF-8 bytes per string or byte string in binary
 * @param allowNonFiniteNumbers accepts {@code NaN} and infinities in binary doubles only; JSON rejects them regardless
 *                              (RFC 8259 cannot encode them)
 */
public record WireConstraints(
  int maxNestingDepth,
  int maxStringLength,
  int maxNumberLength,
  int maxArrayLength,
  int maxMapEntries,
  DuplicateKeyPolicy duplicateKeyPolicy,
  boolean allowNonFiniteNumbers
) {
  /** Jackson 2.15's secure defaults plus {@link DuplicateKeyPolicy#FAIL} and rejection of non-finite numbers. */
  public static final WireConstraints SECURE_DEFAULTS = new WireConstraints(
    1_000,
    5 * 1024 * 1024,
    1_000,
    1_000_000,
    1_000_000,
    DuplicateKeyPolicy.FAIL,
    false
  );

  /** All limits relaxed to {@link Integer#MAX_VALUE}; duplicate keys allowed; non-finite numbers allowed. */
  public static final WireConstraints UNCHECKED = new WireConstraints(
    Integer.MAX_VALUE,
    Integer.MAX_VALUE,
    Integer.MAX_VALUE,
    Integer.MAX_VALUE,
    Integer.MAX_VALUE,
    DuplicateKeyPolicy.ALLOW,
    true
  );

  public WireConstraints {
    if (maxNestingDepth < 1) {
      throw new IllegalArgumentException("maxNestingDepth must be >= 1, got " + maxNestingDepth);
    }
    if (maxStringLength < 0) {
      throw new IllegalArgumentException("maxStringLength must be >= 0, got " + maxStringLength);
    }
    if (maxNumberLength < 1) {
      throw new IllegalArgumentException("maxNumberLength must be >= 1, got " + maxNumberLength);
    }
    if (maxArrayLength < 0) {
      throw new IllegalArgumentException("maxArrayLength must be >= 0, got " + maxArrayLength);
    }
    if (maxMapEntries < 0) {
      throw new IllegalArgumentException("maxMapEntries must be >= 0, got " + maxMapEntries);
    }
    Objects.requireNonNull(duplicateKeyPolicy, "duplicateKeyPolicy must not be null");
  }

  /** Returns a builder seeded with {@link #SECURE_DEFAULTS}. */
  public static Builder builder() {
    return new Builder();
  }

  public enum DuplicateKeyPolicy {
    /** Throw {@link WireConstraintViolationException}. */
    FAIL,
    /** Accept the second write; readers keep the last-seen value. */
    ALLOW,
  }

  public static final class Builder {

    private int maxNestingDepth = SECURE_DEFAULTS.maxNestingDepth;
    private int maxStringLength = SECURE_DEFAULTS.maxStringLength;
    private int maxNumberLength = SECURE_DEFAULTS.maxNumberLength;
    private int maxArrayLength = SECURE_DEFAULTS.maxArrayLength;
    private int maxMapEntries = SECURE_DEFAULTS.maxMapEntries;
    private DuplicateKeyPolicy duplicateKeyPolicy = SECURE_DEFAULTS.duplicateKeyPolicy;
    private boolean allowNonFiniteNumbers = SECURE_DEFAULTS.allowNonFiniteNumbers;

    private Builder() {}

    public Builder maxNestingDepth(int value) {
      this.maxNestingDepth = value;
      return this;
    }

    public Builder maxStringLength(int value) {
      this.maxStringLength = value;
      return this;
    }

    public Builder maxNumberLength(int value) {
      this.maxNumberLength = value;
      return this;
    }

    public Builder maxArrayLength(int value) {
      this.maxArrayLength = value;
      return this;
    }

    public Builder maxMapEntries(int value) {
      this.maxMapEntries = value;
      return this;
    }

    public Builder duplicateKeyPolicy(DuplicateKeyPolicy value) {
      this.duplicateKeyPolicy = Objects.requireNonNull(value, "duplicateKeyPolicy must not be null");
      return this;
    }

    public Builder allowNonFiniteNumbers(boolean value) {
      this.allowNonFiniteNumbers = value;
      return this;
    }

    /** Relaxes every limit to {@link Integer#MAX_VALUE} and allows duplicate keys and non-finite numbers. */
    public Builder unchecked() {
      this.maxNestingDepth = UNCHECKED.maxNestingDepth;
      this.maxStringLength = UNCHECKED.maxStringLength;
      this.maxNumberLength = UNCHECKED.maxNumberLength;
      this.maxArrayLength = UNCHECKED.maxArrayLength;
      this.maxMapEntries = UNCHECKED.maxMapEntries;
      this.duplicateKeyPolicy = UNCHECKED.duplicateKeyPolicy;
      this.allowNonFiniteNumbers = UNCHECKED.allowNonFiniteNumbers;
      return this;
    }

    public WireConstraints build() {
      return new WireConstraints(
        maxNestingDepth,
        maxStringLength,
        maxNumberLength,
        maxArrayLength,
        maxMapEntries,
        duplicateKeyPolicy,
        allowNonFiniteNumbers
      );
    }
  }
}
