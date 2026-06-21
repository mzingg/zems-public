package dev.zems.lib.value.marshal.wire;

import java.util.Objects;

/**
 * Thrown when a value being read or written violates a {@link WireConstraints} bound. Extends
 * {@link IllegalStateException} so existing call sites that catch {@code IllegalStateException} keep working; callers
 * that want to single out wire-level DoS specifically can catch this type.
 */
public final class WireConstraintViolationException extends IllegalStateException {

  private final String constraint;
  private final long limit;
  private final long actual;

  public WireConstraintViolationException(String constraint, long limit, long actual) {
    super(constraint + " exceeded: " + actual + " > " + limit);
    this.constraint = Objects.requireNonNull(constraint, "constraint must not be null");
    this.limit = limit;
    this.actual = actual;
  }

  public WireConstraintViolationException(String constraint, String detail) {
    super(constraint + " violated: " + detail);
    this.constraint = Objects.requireNonNull(constraint, "constraint must not be null");
    this.limit = -1;
    this.actual = -1;
  }

  /** Name of the violated constraint (e.g. {@code "maxStringLength"}, {@code "duplicateKey"}). */
  public String constraint() {
    return constraint;
  }

  /** Configured limit, or {@code -1} when the constraint is policy-shaped (e.g. duplicate-key). */
  public long limit() {
    return limit;
  }

  /** Observed value that triggered the violation, or {@code -1} for policy-shaped constraints. */
  public long actual() {
    return actual;
  }
}
