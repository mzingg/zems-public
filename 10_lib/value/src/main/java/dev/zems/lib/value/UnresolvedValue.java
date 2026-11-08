package dev.zems.lib.value;

import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;

/**
 * State marker for a slot whose value exists but has not been computed, loaded or fetched yet. Singleton, obtained
 * through {@link Value#unresolved()}; every {@code UnresolvedValue<?>} equals every other.
 */
public final class UnresolvedValue<T> implements Value<T> {

  private static final UnresolvedValue<?> INSTANCE = new UnresolvedValue<>();
  private static final int SINGLETON_HASH = 2;

  private UnresolvedValue() {}

  @SuppressWarnings("unchecked")
  static <T> UnresolvedValue<T> instance() {
    return (UnresolvedValue<T>) INSTANCE;
  }

  @Override
  public TypeDescriptor<T> valueType() {
    return null;
  }

  @Override
  public T unbox() {
    throw new IllegalStateException("Cannot unbox state-marker value UnresolvedValue");
  }

  @Override
  public int hashCode() {
    return SINGLETON_HASH;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof UnresolvedValue<?>;
  }

  @Override
  public String toString() {
    return "UnresolvedValue[]";
  }
}
