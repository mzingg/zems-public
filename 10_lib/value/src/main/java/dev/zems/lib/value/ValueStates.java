package dev.zems.lib.value;

import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.util.function.Function;

/**
 * Package-private factory implementations for state-marker {@link Value Value}s ({@link NullValue},
 * {@link UndefinedValue}, {@link UnresolvedValue}, {@link TombstoneValue}), {@link ErrorValue}, and the null-tolerant
 * {@link #ofNullable} helper.
 *
 * <p>
 * External callers go through {@code Value.*} — the static methods here are package-private and reachable only via
 * Value's one-line delegates.
 */
final class ValueStates {

  private static final String ERROR_NULL = "Error cannot be null";
  private static final String ERROR_BLANK = "Error cannot be null or empty";
  private static final String EXPECTED_TYPE_NULL = "Expected type cannot be null";

  private ValueStates() {}

  static <T> Value<T> undefined() {
    return UndefinedValue.instance();
  }

  static <T> Value<T> unresolved() {
    return UnresolvedValue.instance();
  }

  static <T> Value<T> tombstone() {
    return TombstoneValue.instance();
  }

  static <T> Value<T> errorOf(Throwable cause, Class<T> expectedClass) {
    if (cause == null) {
      throw new IllegalArgumentException(ERROR_NULL);
    }
    if (expectedClass == null) {
      throw new IllegalArgumentException(EXPECTED_TYPE_NULL);
    }
    return errorOf(cause, TypeDescriptor.of(expectedClass.getName(), expectedClass));
  }

  static <T> Value<T> errorOf(Throwable cause, TypeDescriptor<T> expectedType) {
    if (cause == null) {
      throw new IllegalArgumentException(ERROR_NULL);
    }
    if (expectedType == null) {
      throw new IllegalArgumentException(EXPECTED_TYPE_NULL);
    }
    return new ErrorValue<>(cause, expectedType);
  }

  static <T> Value<T> errorMessage(String message, Class<T> expectedClass) {
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException(ERROR_BLANK);
    }
    if (expectedClass == null) {
      throw new IllegalArgumentException(EXPECTED_TYPE_NULL);
    }
    return errorMessage(message, TypeDescriptor.of(expectedClass.getName(), expectedClass));
  }

  static <T> Value<T> errorMessage(String message, TypeDescriptor<T> expectedType) {
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException(ERROR_BLANK);
    }
    if (expectedType == null) {
      throw new IllegalArgumentException(EXPECTED_TYPE_NULL);
    }
    return new ErrorValue<>(new IllegalStateException(message), expectedType);
  }

  static <T, R> Value<R> ofNullable(T input, Function<T, Value<R>> ifNotNull) {
    if (ifNotNull == null) {
      throw new IllegalArgumentException("ifNotNull must not be null");
    }
    if (input == null) {
      return nullValue();
    }
    Value<R> mapped = ifNotNull.apply(input);
    // A mapper that returns a bare null would otherwise leak a literal null Value, breaking the
    // "a Value reference is never null" invariant — coerce it to the explicit null state instead.
    return mapped == null ? nullValue() : mapped;
  }

  static <T> Value<T> nullValue() {
    return NullValue.instance();
  }
}
