package dev.zems.lib.value;

/** State-query facet of {@link Value}: classifies a value by its state-marker subtype. */
public interface ValueStateChecks {
  /** True for any of the six state markers; the complement of {@link #isPresent()}. */
  default boolean isStateMarker() {
    return isNull() || isUndefined() || isUnresolved() || isError() || isTombstone() || isUnsupported();
  }

  default boolean isNull() {
    return this instanceof NullValue<?>;
  }

  default boolean isUndefined() {
    return this instanceof UndefinedValue<?>;
  }

  default boolean isUnresolved() {
    return this instanceof UnresolvedValue<?>;
  }

  default boolean isError() {
    return this instanceof ErrorValue<?>;
  }

  /** True for a {@link TombstoneValue}; a tombstone is also {@link #isNotNull()} and {@link #isDefined()}. */
  default boolean isTombstone() {
    return this instanceof TombstoneValue<?>;
  }

  /**
   * True for an {@link UnsupportedValue}: the type pair or the operation cannot be served, so retrying with other
   * data is pointless.
   */
  default boolean isUnsupported() {
    return this instanceof UnsupportedValue<?>;
  }

  default boolean isAbsent() {
    return !isPresent();
  }

  /**
   * True when the payload is safe to read: defined, not null, resolved, not an error, not a tombstone and not
   * unsupported.
   */
  default boolean isPresent() {
    return isDefined() && isNotNull() && isResolved() && isNotError() && isNotTombstone() && isSupported();
  }

  default boolean isDefined() {
    return !(this instanceof UndefinedValue<?>);
  }

  default boolean isNotNull() {
    return !(this instanceof NullValue<?>);
  }

  default boolean isResolved() {
    return !(this instanceof UnresolvedValue<?>);
  }

  default boolean isNotError() {
    return !(this instanceof ErrorValue<?>);
  }

  default boolean isNotTombstone() {
    return !(this instanceof TombstoneValue<?>);
  }

  /** True unless this is an {@link UnsupportedValue}; every other marker, and a present value, is "supported". */
  default boolean isSupported() {
    return !(this instanceof UnsupportedValue<?>);
  }
}
