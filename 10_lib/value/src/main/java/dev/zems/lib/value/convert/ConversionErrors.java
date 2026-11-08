package dev.zems.lib.value.convert;

import dev.zems.lib.value.Value;

/**
 * The one refusal frame of this package: {@code "Cannot convert <Source> to <Target>: <reason>"}. A refusal about the
 * type pair answers an unsupported value, a refusal about this payload an error value; both wear the same frame.
 */
final class ConversionErrors {

  private ConversionErrors() {}

  static <T> Value<T> noConversion(Class<?> sourceType, Class<T> targetType) {
    return unsupported(sourceType, targetType, "no conversion exists");
  }

  static <T> Value<T> failure(Class<?> sourceType, Class<T> targetType, String reason) {
    return failure(sourceType, targetType, reason, null);
  }

  static <T> Value<T> failure(Class<?> sourceType, Class<T> targetType, String reason, Throwable cause) {
    return Value.errorOf(new IllegalStateException(frame(sourceType, targetType, reason), cause), targetType);
  }

  static <T> Value<T> reframe(Class<?> sourceType, Class<T> targetType, Value<T> result) {
    if (!result.isError()) {
      return result;
    }
    Throwable cause = result.error().orElseThrow();
    return failure(sourceType, targetType, reasonOf(cause), cause);
  }

  static <T> Value<T> unsupported(Class<?> sourceType, Class<T> targetType, String reason) {
    return Value.unsupported(frame(sourceType, targetType, reason), targetType);
  }

  /** The throwable's message, or its simple class name when the message is blank. */
  static String reasonOf(Throwable cause) {
    String message = cause.getMessage();
    return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
  }

  private static String frame(Class<?> sourceType, Class<?> targetType, String reason) {
    return "Cannot convert " + sourceType.getSimpleName() + " to " + targetType.getSimpleName() + ": " + reason;
  }
}
