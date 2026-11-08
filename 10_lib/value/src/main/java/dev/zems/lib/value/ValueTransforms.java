package dev.zems.lib.value;

import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Transform facet of {@link Value}: {@code map}, {@code filter}, the {@code orElse} extractors, the {@code recover}
 * family, {@code fold} and {@code stream}.
 *
 * <p>
 * Family rules: a state marker passes through unchanged, re-typed where the result type changes, and an error keeps
 * its expected-type descriptor. An unsupported marker is recoverable like an error: {@code recover} and
 * {@code recoverWith} replace it, while {@code mapError} and {@code onError} leave it alone. A {@code null} answered
 * by a caller's function becomes {@link Value#nullValue()}. A {@code null} function argument throws
 * {@link IllegalArgumentException} with the message {@link #FUNCTION_NULL}; a {@code null} data argument is
 * legitimate.
 */
public sealed interface ValueTransforms<T> permits Value {
  /** Message of the {@link IllegalArgumentException} thrown for a {@code null} function argument. */
  String FUNCTION_NULL = "Combinator function must not be null";

  /**
   * Message prefix of the {@link NoSuchElementException} thrown by {@link #orElseThrow()} on a marker that carries
   * neither a throwable nor a reason.
   */
  String NOT_PRESENT = "Value is not present: ";

  /**
   * Applies {@code mapper} to a present payload and wraps the result. A {@link RuntimeException} from the mapper becomes
   * an error value with {@code Object} as the expected type; an {@link Error} propagates. A mapper answering a raw
   * {@code List}, {@code Set} or {@code Map} throws {@link IllegalArgumentException}; one answering a {@code Value}
   * collapses instead of nesting.
   */
  default <R> Value<R> map(Function<? super T, ? extends R> mapper) {
    requireFunction(mapper);
    Value<T> self = self();
    if (!self.isPresent()) {
      return retyped(self);
    }
    R mapped;
    try {
      mapped = mapper.apply(self.unbox());
    } catch (RuntimeException thrown) {
      return retyped(Value.errorOf(thrown, Object.class));
    }
    return Value.ofNullable(mapped);
  }

  /**
   * Applies {@code mapper} to a present payload and answers its {@code Value} as it stands. Unlike {@link #map} an
   * exception from the mapper is not caught.
   */
  default <R> Value<R> flatMap(Function<? super T, ? extends Value<R>> mapper) {
    requireFunction(mapper);
    Value<T> self = self();
    if (!self.isPresent()) {
      return retyped(self);
    }
    return Value.ofNullable(mapper.apply(self.unbox()), inner -> inner);
  }

  /** Keeps a present payload that satisfies {@code predicate} and answers {@link Value#undefined()} when it does not. */
  default Value<T> filter(Predicate<? super T> predicate) {
    requireFunction(predicate);
    Value<T> self = self();
    if (!self.isPresent()) {
      return self;
    }
    return predicate.test(self.unbox()) ? self : Value.undefined();
  }

  /** Unwraps a present payload, or answers {@code other} (which may be {@code null}) on any state marker. */
  default T orElse(T other) {
    Value<T> self = self();
    return self.isPresent() ? self.unbox() : other;
  }

  /** Unwraps a present payload, or answers the supplier's result on any state marker, errors included. */
  default T orElseGet(Supplier<? extends T> supplier) {
    requireFunction(supplier);
    Value<T> self = self();
    return self.isPresent() ? self.unbox() : supplier.get();
  }

  /**
   * Unwraps a present payload. An {@link ErrorValue} rethrows its throwable, a checked one wrapped in
   * {@link IllegalStateException}; an {@link UnsupportedValue} throws an {@link UnsupportedOperationException}
   * carrying its reason; every other marker throws {@link NoSuchElementException}.
   */
  default T orElseThrow() {
    Value<T> self = self();
    if (self.isPresent()) {
      return self.unbox();
    }
    if (self instanceof UnsupportedValue<?> unsupported) {
      throw new UnsupportedOperationException(unsupported.reason());
    }
    Throwable carried = self.error().orElse(null);
    switch (carried) {
      case null -> throw new NoSuchElementException(NOT_PRESENT + self);
      case RuntimeException runtime -> throw runtime;
      case Error error -> throw error;
      default -> throw new IllegalStateException(carried);
    }
  }

  /**
   * Unwraps a present payload, or throws the supplied exception on any state marker; an {@link ErrorValue}'s own
   * throwable is ignored.
   */
  default <X extends Throwable> T orElseThrow(Supplier<? extends X> exception) throws X {
    requireFunction(exception);
    Value<T> self = self();
    if (self.isPresent()) {
      return self.unbox();
    }
    throw exception.get();
  }

  /** Keeps this value when present, otherwise answers the supplier's {@code Value}; fires on errors too. */
  default Value<T> or(Supplier<? extends Value<T>> supplier) {
    requireFunction(supplier);
    Value<T> self = self();
    if (self.isPresent()) {
      return self;
    }
    Value<T> other = supplier.get();
    return other == null ? Value.nullValue() : other;
  }

  default void ifPresent(Consumer<? super T> action) {
    requireFunction(action);
    Value<T> self = self();
    if (self.isPresent()) {
      action.accept(self.unbox());
    }
  }

  default void ifPresentOrElse(Consumer<? super T> action, Runnable absentAction) {
    requireFunction(action);
    requireFunction(absentAction);
    Value<T> self = self();
    if (self.isPresent()) {
      action.accept(self.unbox());
    } else {
      absentAction.run();
    }
  }

  /**
   * Runs {@code action} on the throwable when this is an {@link ErrorValue}, then answers this value unchanged. An
   * {@link UnsupportedValue} is not an error and never fires it.
   */
  default Value<T> onError(Consumer<? super Throwable> action) {
    requireFunction(action);
    Value<T> self = self();
    if (self instanceof ErrorValue<?> error) {
      action.accept(error.throwable());
    }
    return self;
  }

  /** Runs {@code action} on any state marker, then answers this value unchanged. */
  default Value<T> ifAbsent(Runnable action) {
    requireFunction(action);
    Value<T> self = self();
    if (self.isAbsent()) {
      action.run();
    }
    return self;
  }

  /**
   * Replaces an {@link ErrorValue} or an {@link UnsupportedValue} with the wrapped {@code fallback}; every other
   * state passes through. A {@code null} fallback becomes {@link Value#nullValue()}.
   */
  default Value<T> recover(T fallback) {
    Value<T> self = self();
    return self.isError() || self.isUnsupported() ? Value.ofNullable(fallback) : self;
  }

  /**
   * Replaces an {@link ErrorValue} with the {@code Value} that {@code recovery} derives from its throwable, and an
   * {@link UnsupportedValue} with the one it derives from a fresh {@link UnsupportedOperationException} carrying the
   * reason; every other state passes through.
   */
  default Value<T> recoverWith(Function<? super Throwable, ? extends Value<T>> recovery) {
    requireFunction(recovery);
    Value<T> self = self();
    Throwable carried = switch (self) {
      case ErrorValue<?> error -> error.throwable();
      case UnsupportedValue<?> unsupported -> new UnsupportedOperationException(unsupported.reason());
      default -> null;
    };
    if (carried == null) {
      return self;
    }
    return Value.ofNullable(recovery.apply(carried), inner -> inner);
  }

  /**
   * Replaces an {@link ErrorValue}'s throwable with the one {@code mapper} derives from it, keeping the expected-type
   * descriptor; every other state passes through, an {@link UnsupportedValue} included.
   */
  default Value<T> mapError(Function<? super Throwable, ? extends Throwable> mapper) {
    requireFunction(mapper);
    Value<T> self = self();
    if (!(self instanceof ErrorValue<?> error)) {
      return self;
    }
    // Inlining typedError(error).expectedType() gives javac two inference variables in one constraint and it fails
    // with "inference variable T has incompatible equality constraints".
    ErrorValue<T> typed = typedError(error);
    return Value.errorOf(mapper.apply(error.throwable()), typed.expectedType());
  }

  /**
   * Routes a present payload to {@code onPresent} and any state marker, as the {@code Value} itself, to
   * {@code onAbsent}, answering whichever handler ran.
   */
  default <R> R fold(Function<? super T, ? extends R> onPresent, Function<? super Value<T>, ? extends R> onAbsent) {
    requireFunction(onPresent);
    requireFunction(onAbsent);
    Value<T> self = self();
    return self.isPresent() ? onPresent.apply(self.unbox()) : onAbsent.apply(self);
  }

  /**
   * Streams a present payload as one element and any state marker as an empty stream. A collection wrapper yields one
   * element, the whole collection; use {@code unboxElements()} on a {@code ListValue} or {@code SetValue} to stream the
   * elements.
   */
  default Stream<T> stream() {
    Value<T> self = self();
    return self.isPresent() ? Stream.of(self.unbox()) : Stream.empty();
  }

  private Value<T> self() {
    return (Value<T>) this;
  }

  @SuppressWarnings("unchecked")
  private static <A, B> Value<B> retyped(Value<A> marker) {
    return (Value<B>) marker;
  }

  @SuppressWarnings("unchecked")
  private static <T> ErrorValue<T> typedError(ErrorValue<?> error) {
    return (ErrorValue<T>) error;
  }

  private static void requireFunction(Object argument) {
    if (argument == null) {
      throw new IllegalArgumentException(FUNCTION_NULL);
    }
  }
}
