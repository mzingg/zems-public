package dev.zems.lib.value.builtin;

import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;

public record DoubleValue(double doubleValue) implements BuiltInValue<Double> {
  @Override
  public TypeDescriptor<Double> valueType() {
    return BuiltinTypeDescriptors.DOUBLE;
  }
}
