package dev.zems.lib.value;

/**
 * Opt-in marker for a type that is its own {@link Value}: a type that {@code implements CoreValue<T>} slots into
 * pattern-match cases and {@code Value}-typed APIs directly, without a {@link BoxedValue} wrapper.
 *
 * @param <T> the implementing type itself ({@code SourceNode implements CoreValue<SourceNode>}); {@link #unbox()}
 *     returns the instance, so any other {@code T} breaks on the first cast
 */
public non-sealed interface CoreValue<T> extends Value<T> {
  @Override
  @SuppressWarnings("unchecked")
  default T unbox() {
    return (T) this;
  }
}
