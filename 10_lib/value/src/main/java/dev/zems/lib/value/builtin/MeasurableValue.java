package dev.zems.lib.value.builtin;

import dev.zems.lib.value.Quantity;
import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.util.Objects;

/** A {@link Quantity}: an exact magnitude with its unit, a physical quantity or an amount of money alike. */
public record MeasurableValue(Quantity quantity) implements BuiltInValue<Quantity> {
  public MeasurableValue {
    Objects.requireNonNull(quantity, "Quantity must not be null");
  }

  @Override
  public TypeDescriptor<Quantity> valueType() {
    return BuiltinTypeDescriptors.QUANTITY;
  }

  @Override
  public Quantity unbox() {
    return quantity;
  }
}
