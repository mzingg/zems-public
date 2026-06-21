package dev.zems.lib.value;

import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;

/**
 * State marker for a slot that has no value at all — distinct from {@link NullValue} (slot holds an explicit null),
 * {@link UnresolvedValue} (value exists but has not been computed or fetched yet), {@link TombstoneValue} (value was
 * deliberately removed), and {@link ErrorValue} (production of the value failed).
 *
 * <p>
 * The distinction from {@code NullValue} is load-bearing: in tree node attributes, configuration overlays, and
 * patch/merge flows, "I explicitly chose null" and "no value was ever supplied" must produce different downstream
 * behaviour. {@code UndefinedValue} carries the "absent" case without collapsing it into {@code null}.
 *
 * <p>
 * Singleton; obtain via {@link Value#undefined()}. Equality is type-based — every {@code UndefinedValue<?>} equals
 * every other regardless of the type parameter, which exists only for pattern-matching compatibility in the sealed
 * {@link Value} hierarchy.
 */
public final class UndefinedValue<T> implements Value<T> {

  private static final UndefinedValue<?> INSTANCE = new UndefinedValue<>();

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
  public int hashCode() {
    return 1; // Distinct constant for singleton - NullValue=0, UndefinedValue=1, UnresolvedValue=2
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
