package dev.zems.lib.value.convert;

import dev.zems.lib.value.Value;
import java.util.function.Function;

/**
 * One conversion from a source payload type to a target type; register it with {@link Converter#with(Conversion)}. Its
 * policy (rounding, epoch unit, ...) is fixed when the conversion is built.
 */
public interface Conversion<S, T> {
  /**
   * Builds a conversion from a source type, a target type and a body.
   *
   * @throws IllegalArgumentException if any argument is {@code null}
   */
  static <S, T> Conversion<S, T> of(Class<S> sourceType, Class<T> targetType, Function<S, Value<T>> body) {
    if (sourceType == null) {
      throw new IllegalArgumentException("Conversion source type must not be null");
    }
    if (targetType == null) {
      throw new IllegalArgumentException("Conversion target type must not be null");
    }
    if (body == null) {
      throw new IllegalArgumentException("Conversion body must not be null");
    }
    // An anonymous class, not a record: a record nested in an interface is implicitly public and would widen the
    // package's public API.
    return new Conversion<>() {
      @Override
      public Class<S> sourceType() {
        return sourceType;
      }

      @Override
      public Class<T> targetType() {
        return targetType;
      }

      @Override
      public Value<T> convert(S source) {
        return body.apply(source);
      }

      @Override
      public String toString() {
        return "Conversion[" + sourceType.getName() + " -> " + targetType.getName() + "]";
      }
    };
  }

  /**
   * The payload type this conversion accepts: the class of the unboxed payload (or one of its superclasses), never the
   * {@link Value} wrapper type. A converter dispatches on it.
   */
  Class<S> sourceType();

  Class<T> targetType();

  /**
   * Converts one payload. The argument is the unboxed payload, never a {@link Value}, {@code null} or a state marker;
   * the converter passes those through before any conversion runs. Answers an error value with the reason when this
   * particular payload is refused, instead of throwing.
   */
  Value<T> convert(S source);
}
