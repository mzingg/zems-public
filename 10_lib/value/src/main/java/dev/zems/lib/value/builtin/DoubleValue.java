package dev.zems.lib.value.builtin;

import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;

public record DoubleValue(double doubleValue) implements BuiltInValue<Double> {
  @Override
  public TypeDescriptor<Double> valueType() {
    return BuiltinTypeDescriptors.DOUBLE;
  }

  @Override
  public Double unbox() {
    return doubleValue;
  }
}
