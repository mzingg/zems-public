package dev.zems.lib.value;

import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;

/**
 * State marker for a slot that has no value at all, as opposed to {@link NullValue} for an explicit null. Singleton,
 * obtained through {@link Value#undefined()}; every {@code UndefinedValue<?>} equals every other.
 */
public final class UndefinedValue<T> implements Value<T> {

  private static final UndefinedValue<?> INSTANCE = new UndefinedValue<>();
  private static final int SINGLETON_HASH = 1;

  private UndefinedValue() {}

  @SuppressWarnings("unchecked")
  static <T> UndefinedValue<T> instance() {
    return (UndefinedValue<T>) INSTANCE;
  }

  @Override
  public TypeDescriptor<T> valueType() {
    return null;
  }

  @Override
  public T unbox() {
    throw new IllegalStateException("Cannot unbox state-marker value UndefinedValue");
  }

  @Override
  public int hashCode() {
    return SINGLETON_HASH;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof UndefinedValue<?>;
  }

  @Override
  public String toString() {
    return "UndefinedValue[]";
  }
}
