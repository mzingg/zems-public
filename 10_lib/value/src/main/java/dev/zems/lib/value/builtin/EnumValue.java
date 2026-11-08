package dev.zems.lib.value.builtin;

import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.util.Objects;

/**
 * A constant of any enum type; equality and hash are the constant itself.
 *
 * <p>
 * The wire form is the constant's {@link Enum#name()}, never its ordinal or {@code toString()}.
 */
public record EnumValue<E extends Enum<E>>(E constant) implements BuiltInValue<E> {
  public EnumValue {
    Objects.requireNonNull(constant, "Enum constant must not be null");
  }

  @Override
  @SuppressWarnings("unchecked")
  public TypeDescriptor<E> valueType() {
    // getDeclaringClass, not getClass: a constant with a body is an anonymous subclass that is not itself an enum.
    return (TypeDescriptor<E>) TypeDescriptor.find(constant.getDeclaringClass()).orElseThrow();
  }

  @Override
  public E unbox() {
    return constant;
  }
}
