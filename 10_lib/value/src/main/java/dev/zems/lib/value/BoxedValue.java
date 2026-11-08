package dev.zems.lib.value;

import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.util.Objects;

/**
 * Wraps any non-built-in {@code T} as a leaf in the {@link Value} hierarchy; {@link Value#of(Object)} produces it when
 * the argument is neither a built-in JDK type nor already a {@code Value}. Construct through {@code Value.of}: the
 * canonical constructor is public only because records require it.
 */
public record BoxedValue<T>(T inner) implements Value<T> {
  private static final ClassValue<TypeDescriptor<?>> TYPE_CACHE = new ClassValue<>() {
    @Override
    protected TypeDescriptor<?> computeValue(Class<?> type) {
      return TypeDescriptor.find(type).orElse(null);
    }
  };

  public BoxedValue {
    Objects.requireNonNull(inner, "inner must not be null");
    if (inner instanceof Value<?> value) {
      throw new IllegalArgumentException(
        "Cannot box a Value (" + value.getClass().getSimpleName() + ") - use the Value directly"
      );
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public TypeDescriptor<T> valueType() {
    return (TypeDescriptor<T>) TYPE_CACHE.get(inner.getClass());
  }

  @Override
  public T unbox() {
    return inner;
  }
}
