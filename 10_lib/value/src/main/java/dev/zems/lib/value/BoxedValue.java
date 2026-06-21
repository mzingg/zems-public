package dev.zems.lib.value;

import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.util.Objects;

/**
 * Wraps any non-built-in {@code T} as a leaf in the {@link Value} hierarchy. Produced automatically by
 * {@link Value#of(Object) Value.of(t)} when {@code t} is neither a built-in JDK type (String/Integer/UUID/…) nor
 * already a {@code Value}.
 *
 * <p>
 * <b>BoxedValue vs {@link CoreValue} — when to use which:</b>
 *
 * <p>
 * {@code BoxedValue} is the implicit fallback for incidental wrapping — a record carried inside another structure, a
 * payload that occasionally needs to flow through a {@code Value}-typed slot, anything that doesn't itself want to be a
 * first-class Value. Just call {@code Value.of(myInstance)} and you get a {@code BoxedValue<MyType>} back; no
 * declaration needed on the user type.
 *
 * <p>
 * {@code CoreValue} is the deliberate opt-in for domain types where being a {@code Value} is part of the model —
 * pattern-matched directly in switches over {@code Value<?>}, streamed through marshalling APIs without an intermediate
 * {@code .map(Value::of)}, allocating one object per instance instead of two. See {@link CoreValue} for the full
 * trade-off.
 *
 * <p>
 * Built-in JDK types (String, Integer, UUID, Instant, …) bypass {@code BoxedValue} entirely and live in their matching
 * {@link dev.zems.lib.value.builtin.BuiltInValue BuiltInValue} subtype.
 *
 * <p>
 * <b>Direct invocation of the canonical constructor is unsupported.</b> Records require a public
 * canonical constructor, but production code must construct values via {@link Value#of(Object)}. Calling
 * {@code new BoxedValue<>(...)} directly bypasses the {@link Value#of(Object)} factory and may produce wrappers that
 * violate library invariants — this is not detected at runtime. Tests in the same package may be called directly when
 * exercising the wrapper itself.
 *
 * @param <T> the wrapped Java type
 */
public record BoxedValue<T>(T inner) implements Value<T> {
  /** Per-class cache: lookup happens once per {@code Class<?>}, the result is shared across all instances. */
  private static final ClassValue<TypeDescriptor<?>> TYPE_CACHE = new ClassValue<>() {
    @Override
    protected TypeDescriptor<?> computeValue(Class<?> type) {
      return TypeDescriptor.find(type).orElse(null);
    }
  };

  public BoxedValue {
    Objects.requireNonNull(inner, "inner must not be null");
  }

  @Override
  @SuppressWarnings("unchecked")
  public TypeDescriptor<T> valueType() {
    return (TypeDescriptor<T>) TYPE_CACHE.get(inner.getClass());
  }
}
