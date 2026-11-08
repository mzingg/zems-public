package dev.zems.lib.value;

import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.util.Objects;

/**
 * State marker for a failed attempt to produce a value. Equality compares the throwable's class and message and the
 * expected-type descriptor, not throwable identity. Construct through {@link Value#errorOf} or
 * {@link Value#errorMessage}: the canonical constructor is public only because records require it.
 */
public record ErrorValue<Expected>(
  Throwable throwable,
  TypeDescriptor<Expected> expectedType
) implements Value<Expected> {
  public ErrorValue {
    if (throwable == null) {
      throw new IllegalArgumentException("Error cannot be null");
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
    throw new IllegalStateException("Cannot unbox state-marker value ErrorValue");
  }

  @Override
  @SuppressWarnings("PMD.SimplifyBooleanReturns") // the record-pattern guard reads clearer than one folded expression
  public boolean equals(Object o) {
    if (!(o instanceof ErrorValue<?>(Throwable otherThrowable, TypeDescriptor<?> otherType))) {
      return false;
    }
    return (
      throwable().getClass().equals(otherThrowable.getClass()) &&
      Objects.equals(throwable().getMessage(), otherThrowable.getMessage()) &&
      expectedType().equals(otherType)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(throwable().getClass(), throwable().getMessage(), expectedType());
  }
}
