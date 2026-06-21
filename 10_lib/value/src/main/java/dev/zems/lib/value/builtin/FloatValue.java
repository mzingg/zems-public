package dev.zems.lib.value.builtin;

import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;

public record FloatValue(float floatValue) implements BuiltInValue<Float> {
  @Override
  public TypeDescriptor<Float> valueType() {
    return BuiltinTypeDescriptors.FLOAT;
  }
}
