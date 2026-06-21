package dev.zems.lib.value;

import java.util.Optional;

/**
 * Error-accessor facet of {@link Value}: surfaces the {@link Throwable} (or its message) carried by an
 * {@link ErrorValue}. State classification — {@code isError()} / {@code isNotError()} — stays on
 * {@link ValueStateChecks}; this interface is for extracting the payload.
 *
 * <p>
 * {@code Value<T>} extends this interface so callers see these methods directly on {@code Value}; this type exists
 * purely to keep the {@link Value} source readable.
 */
public interface ValueErrorHandling {
  /**
   * Returns the error's message if this is an {@link ErrorValue}, empty otherwise. Sugar over
   * {@code error().map(Throwable::getMessage)} for the common error-reporting path.
   */
  default Optional<String> errorMessage() {
    return error().map(Throwable::getMessage);
  }

  /** Returns the error if this is an {@link ErrorValue}, empty otherwise. */
  default Optional<Throwable> error() {
    return this instanceof ErrorValue<?> e ? Optional.of(e.throwable()) : Optional.empty();
  }
}
