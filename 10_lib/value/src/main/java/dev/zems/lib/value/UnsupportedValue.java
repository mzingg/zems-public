package dev.zems.lib.value;

import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;

/**
 * State marker for a request that cannot be served because of the type pair or the operation, not the data: the reason
 * names what was asked and why it cannot be served, and retrying with other data is pointless. Equality is by reason
 * and expected type. Construct through {@link Value#unsupported(String, Class)}: the canonical constructor is public
 * only because records require it.
 */
public record UnsupportedValue<Expected>(
  String reason,
  TypeDescriptor<Expected> expectedType
) implements Value<Expected> {
  public UnsupportedValue {
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("Reason cannot be null or empty");
    }
    if (expectedType == null) {
      throw new IllegalArgumentException("Expected type cannot be null");
    }
  }

  @Override
  public TypeDescriptor<Expected> valueType() {
    return expectedType;
  }

  @Override
  public Expected unbox() {
    throw new IllegalStateException("Cannot unbox state-marker value UnsupportedValue");
  }
}
