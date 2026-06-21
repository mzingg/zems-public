package dev.zems.lib.value;

import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;

/**
 * State marker for a slot whose value is explicitly {@code null} — distinct from {@link UndefinedValue} (slot has no
 * value at all), {@link UnresolvedValue} (value exists but has not been computed or fetched yet),
 * {@link TombstoneValue} (value was deliberately removed), and {@link ErrorValue} (production of the value failed).
 *
 * <p>
 * Used in place of a raw Java {@code null} so a {@link Value} reference is itself never null. Callers receiving a
 * {@code Value<T>} can pattern-match on {@code NullValue<T>} without a preceding null check — the library's "Explicit
 * State Representation" principle (see the module CLAUDE.md) in practice.
 *
 * <p>
 * Singleton; obtain via {@link Value#nullValue()}. Equality is type-based — every {@code NullValue<?>} equals every
 * other regardless of the type parameter, which exists only for pattern-matching compatibility in the sealed
 * {@link Value} hierarchy.
 */
public final class NullValue<T> implements Value<T> {

  private static final NullValue<?> INSTANCE = new NullValue<>();

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
  public int hashCode() {
    return 0; // Distinct constant for singleton - NullValue=0, UndefinedValue=1, UnresolvedValue=2
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
