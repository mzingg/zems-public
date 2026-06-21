package dev.zems.lib.value;

import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.util.Objects;

/**
 * Represents a failed attempt to produce a value.
 *
 * <p>
 * Equality is based on the throwable's class and message and on the expected-type descriptor — not on throwable
 * identity. This is because {@link Throwable#equals(Object)} uses identity semantics, but ErrorValue follows the value
 * library's convention of value-based equality.
 *
 * <p>
 * Carrying a {@link TypeDescriptor} (rather than a raw {@link Class}) preserves generic structure: an
 * {@code ErrorValue} for a {@code List<Foo>} reads back as {@code ErrorValue<List<Foo>>}, not the erased
 * {@code ErrorValue<List>}.
 *
 * <p>
 * <b>Direct invocation of the canonical constructor is unsupported.</b> Records require a public
 * canonical constructor, but production code must construct error values via {@link Value#errorOf(Throwable, Class)} /
 * {@link Value#errorMessage(String, Class)} (or the {@link TypeDescriptor}-typed overloads). Calling
 * {@code new ErrorValue<>(...)} directly may produce wrappers that violate library invariants — this is not detected at
 * runtime.
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
  @SuppressWarnings("PMD.SimplifyBooleanReturns")
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
