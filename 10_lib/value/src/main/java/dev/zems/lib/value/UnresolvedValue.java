package dev.zems.lib.value;

import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;

/**
 * State marker for a slot whose value exists in principle but has not yet been computed, loaded, or fetched — distinct
 * from {@link NullValue} (slot holds an explicit null), {@link UndefinedValue} (slot has no value at all),
 * {@link TombstoneValue} (value was deliberately removed), and {@link ErrorValue} (production of the value failed).
 *
 * <p>
 * The semantic is "ask again later, the answer is coming." Used for lazy-loaded fields, pending remote lookups, and
 * references that have not yet been dereferenced. A consumer pattern-matching on {@code UnresolvedValue} typically
 * triggers the resolution path (cache fetch, remote call, lazy compute) and re-reads the slot.
 *
 * <p>
 * Singleton; obtain via {@link Value#unresolved()}. Equality is type-based — every {@code UnresolvedValue<?>} equals
 * every other regardless of the type parameter, which exists only for pattern-matching compatibility in the sealed
 * {@link Value} hierarchy.
 */
public final class UnresolvedValue<T> implements Value<T> {

  private static final UnresolvedValue<?> INSTANCE = new UnresolvedValue<>();

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
  public int hashCode() {
    return 2; // Distinct constant for singleton - NullValue=0, UndefinedValue=1, UnresolvedValue=2
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
