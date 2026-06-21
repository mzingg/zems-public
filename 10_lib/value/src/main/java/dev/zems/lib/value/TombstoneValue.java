package dev.zems.lib.value;

import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;

/**
 * State marker for a slot whose value was once present and has since been explicitly removed — distinct from
 * {@link NullValue} (slot holds an explicit null), {@link UndefinedValue} (slot never had a value), and
 * {@link ErrorValue} (couldn't determine the value).
 *
 * <p>
 * Useful for distributed / replicated stores (CRDT tombstones for delete propagation), versioned data (audit trails:
 * "deleted at version N"), and cache invalidation flows that need to distinguish "explicitly removed" from "never set"
 * or "missed."
 */
public final class TombstoneValue<T> implements Value<T> {

  private static final TombstoneValue<?> INSTANCE = new TombstoneValue<>();

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
  public int hashCode() {
    return 4; // Distinct constant for singleton — Null=0, Undefined=1, Unresolved=2, (Error=3 is non-singleton), Tombstone=4
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
