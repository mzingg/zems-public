package dev.zems.lib.value;

import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;

/**
 * State marker for a slot whose value is explicitly {@code null}, so a {@link Value} reference itself is never
 * {@code null}. Singleton, obtained through {@link Value#nullValue()}; every {@code NullValue<?>} equals every other.
 */
public final class NullValue<T> implements Value<T> {

  private static final NullValue<?> INSTANCE = new NullValue<>();
  private static final int SINGLETON_HASH = 0;

  private NullValue() {}

  @SuppressWarnings("unchecked")
  static <T> NullValue<T> instance() {
    return (NullValue<T>) INSTANCE;
  }

  @Override
  public TypeDescriptor<T> valueType() {
    return null;
  }

  @Override
  public T unbox() {
    throw new IllegalStateException("Cannot unbox state-marker value NullValue");
  }

  @Override
  public int hashCode() {
    return SINGLETON_HASH;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof NullValue<?>;
  }

  @Override
  public String toString() {
    return "NullValue[]";
  }
}
