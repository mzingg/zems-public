package dev.zems.lib.value;

import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;

/**
 * State marker for a slot whose value was present and has been explicitly removed, as opposed to {@link NullValue}
 * and {@link UndefinedValue}. Singleton, obtained through {@link Value#tombstone()}; every {@code TombstoneValue<?>}
 * equals every other.
 */
public final class TombstoneValue<T> implements Value<T> {

  private static final TombstoneValue<?> INSTANCE = new TombstoneValue<>();
  private static final int SINGLETON_HASH = 4;

  private TombstoneValue() {}

  @SuppressWarnings("unchecked")
  static <T> TombstoneValue<T> instance() {
    return (TombstoneValue<T>) INSTANCE;
  }

  @Override
  public TypeDescriptor<T> valueType() {
    return null;
  }

  @Override
  public T unbox() {
    throw new IllegalStateException("Cannot unbox state-marker value TombstoneValue");
  }

  @Override
  public int hashCode() {
    return SINGLETON_HASH;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof TombstoneValue<?>;
  }

  @Override
  public String toString() {
    return "TombstoneValue[]";
  }
}
