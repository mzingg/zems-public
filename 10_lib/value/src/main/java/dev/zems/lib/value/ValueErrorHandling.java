package dev.zems.lib.value;

import java.util.Optional;

/** Error-accessor facet of {@link Value}: the throwable, or its message, carried by an {@link ErrorValue}. */
public interface ValueErrorHandling {
  /** The error's message when this is an {@link ErrorValue}, empty otherwise. */
  default Optional<String> errorMessage() {
    return error().map(Throwable::getMessage);
  }

  /** The carried throwable when this is an {@link ErrorValue}, empty otherwise. */
  default Optional<Throwable> error() {
    return this instanceof ErrorValue<?> e ? Optional.of(e.throwable()) : Optional.empty();
  }
}
