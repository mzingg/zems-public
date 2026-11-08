package dev.zems.lib.value;

import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.util.function.Function;

/** Factory bodies behind the state-marker, error and {@code ofNullable} entries on {@link Value}. */
final class ValueStates {

  private static final String ERROR_NULL = "Error cannot be null";
  private static final String ERROR_BLANK = "Error cannot be null or empty";
  private static final String REASON_BLANK = "Reason cannot be null or empty";
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

  static <T> Value<T> unsupported(String reason, Class<T> expectedClass) {
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException(REASON_BLANK);
    }
    if (expectedClass == null) {
      throw new IllegalArgumentException(EXPECTED_TYPE_NULL);
    }
    return unsupported(reason, TypeDescriptor.of(expectedClass.getName(), expectedClass));
  }

  static <T> Value<T> unsupported(String reason, TypeDescriptor<T> expectedType) {
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException(REASON_BLANK);
    }
    if (expectedType == null) {
      throw new IllegalArgumentException(EXPECTED_TYPE_NULL);
    }
    return new UnsupportedValue<>(reason, expectedType);
  }

  // Overload resolution on the type variable T picks the generic Value.of(T), which maps null to nullValue(); a typed
  // overload would answer an ErrorValue for the same null.
  static <T> Value<T> ofNullable(T input) {
    return Value.of(input);
  }

  static <T, R> Value<R> ofNullable(T input, Function<T, Value<R>> ifNotNull) {
    if (ifNotNull == null) {
      throw new IllegalArgumentException("ifNotNull must not be null");
    }
    if (input == null) {
      return nullValue();
    }
    Value<R> mapped = ifNotNull.apply(input);
    return mapped == null ? nullValue() : mapped;
  }

  static <T> Value<T> nullValue() {
    return NullValue.instance();
  }
}
