package dev.zems.lib.value.marshal.wire;

import dev.zems.lib.value.marshal.Protocol;
import java.util.Objects;

/**
 * Wire-level safety bounds applied symmetrically by readers (against untrusted input) and writers (against pathological
 * in-memory values). A {@link Protocol.V1} carries one {@code WireConstraints} and the format readers/writers query it
 * through {@link Protocol.V1#wireConstraints()}.
 *
 * <p>
 * Defaults match Jackson 2.15+ secure-by-default values plus {@link DuplicateKeyPolicy#FAIL} and strict
 * non-finite-number rejection. A user that never calls {@link Protocol.V1#withWireConstraints(WireConstraints)} still
 * gets {@link #SECURE_DEFAULTS}.
 *
 * <p>
 * Three opt-out shapes:
 * <ul>
 * <li>{@code WireConstraints.UNCHECKED} — direct constant
 * <li>{@code WireConstraints.builder().unchecked().build()} — fluent
 * <li>{@code WireConstraints.builder().<override>...build()} — keep most defaults, relax a few
 * </ul>
 *
 * @param maxNestingDepth       max open records / arrays / objects on the active stack
 * @param maxStringLength       max chars (JSON) or bytes (binary) per scalar payload
 * @param maxNumberLength       max chars in a JSON number token
 * @param maxArrayLength        max elements in a list-shaped slot
 * @param maxMapEntries         max entries in a map-shaped slot
 * @param duplicateKeyPolicy    reaction when a record / object writes the same name twice
 * @param allowNonFiniteNumbers if {@code true}, accepts {@code NaN} / {@code +/-Infinity} in binary doubles. JSON stays
 *                              strict regardless — RFC 8259 disallows them on the wire and the parser rejects them at
 *                              every configuration.
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
  /** Jackson 2.15+ defaults plus FAIL on duplicate keys and strict non-finite rejection. */
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

  /** How readers and writers react when the same name is written twice in a single record / object. */
  public enum DuplicateKeyPolicy {
    /** Throw {@link WireConstraintViolationException}. */
    FAIL,
    /** Accept the second write; readers keep the last-seen value. */
    ALLOW,
  }

  /** Mutable builder seeded from {@link WireConstraints#SECURE_DEFAULTS}. */
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

    /** Bulk opt-out: relax every limit to {@link Integer#MAX_VALUE}, allow duplicates and non-finite numbers. */
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
