package dev.zems.lib.value.builtin;

import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;

public record FloatValue(float floatValue) implements BuiltInValue<Float> {
  @Override
  public TypeDescriptor<Float> valueType() {
    return BuiltinTypeDescriptors.FLOAT;
  }

  @Override
  public Float unbox() {
    return floatValue;
  }
}
