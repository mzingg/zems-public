package dev.zems.lib.value.valueio.wire;

import java.util.Objects;

/** Thrown when a value being read or written violates a {@link WireConstraints} bound. */
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

  /** The violated bound's name, e.g. {@code "maxStringLength"} or {@code "duplicateKey"}. */
  public String constraint() {
    return constraint;
  }

  /** Configured limit, or {@code -1} for a policy-shaped constraint such as duplicate keys. */
  public long limit() {
    return limit;
  }

  /** Observed value, or {@code -1} for a policy-shaped constraint. */
  public long actual() {
    return actual;
  }
}
