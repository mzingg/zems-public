package dev.zems.lib.value;

/**
 * State-query facet of {@link Value}: default methods that classify a value by its sealed state-marker subtype (null /
 * undefined / unresolved / error / tombstone). Error-payload accessors live on {@link ValueErrorHandling}.
 *
 * <p>
 * Named with a {@code Checks} suffix to avoid colliding with the wire-marker enum {@link ValueState} used by the
 * marshal layer; semantically this is the value-side mirror.
 *
 * <p>
 * {@code Value<T>} extends this interface so callers see these methods directly on {@code Value}; this type exists
 * purely to keep the {@link Value} source readable.
 */
public interface ValueStateChecks {
  /**
   * Returns true if this is any state marker — one of {@link NullValue}, {@link UndefinedValue},
   * {@link UnresolvedValue}, {@link ErrorValue}, or {@link TombstoneValue}. The complement is a "real" value (a
   * {@code BuiltInValue}, {@code BoxedValue}, or {@code CoreValue}). Equivalent to {@code !isPresent()}; pick whichever
   * name reads more naturally at the call site.
   */
  default boolean isStateMarker() {
    return (isNull() || isUndefined() || isUnresolved() || isError() || isTombstone());
  }

  /** Returns true if this is a {@link NullValue}. */
  default boolean isNull() {
    return this instanceof NullValue<?>;
  }

  /** Returns true if this is an {@link UndefinedValue}. */
  default boolean isUndefined() {
    return this instanceof UndefinedValue<?>;
  }

  /** Returns true if this is an {@link UnresolvedValue}. */
  default boolean isUnresolved() {
    return this instanceof UnresolvedValue<?>;
  }

  /** Returns true if this is an {@link ErrorValue}. */
  default boolean isError() {
    return this instanceof ErrorValue<?>;
  }

  /**
   * Returns true if this slot was explicitly removed (has a {@link TombstoneValue} marker). Distinct from
   * {@link #isNotNull()} (which is true for tombstones — they're not null specifically) and {@link #isDefined()} (which
   * is also true — tombstones are defined as removed).
   */
  default boolean isTombstone() {
    return this instanceof TombstoneValue<?>;
  }

  /** Returns true if this is not {@link #isPresent() present} — i.e. any state marker. */
  default boolean isAbsent() {
    return !isPresent();
  }

  /**
   * Returns true if this value is safe to read: defined, not null, resolved, not an error, and not a tombstone. The
   * everyday gate before extracting a payload. Equivalent to {@code !isStateMarker()}.
   */
  default boolean isPresent() {
    return (isDefined() && isNotNull() && isResolved() && isNotError() && isNotTombstone());
  }

  /** Returns true if this is not an {@link UndefinedValue}. */
  default boolean isDefined() {
    return !(this instanceof UndefinedValue<?>);
  }

  /** Returns true if this is not a {@link NullValue}. */
  default boolean isNotNull() {
    return !(this instanceof NullValue<?>);
  }

  /** Returns true if this is not an {@link UnresolvedValue}. */
  default boolean isResolved() {
    return !(this instanceof UnresolvedValue<?>);
  }

  /** Returns true if this is not an {@link ErrorValue}. */
  default boolean isNotError() {
    return !(this instanceof ErrorValue<?>);
  }

  /** Returns true if this is not a {@link TombstoneValue}. */
  default boolean isNotTombstone() {
    return !(this instanceof TombstoneValue<?>);
  }
}
