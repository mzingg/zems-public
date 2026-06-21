package dev.zems.lib.value.builtin;

import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;

public record IntegerValue(int intValue) implements BuiltInValue<Integer> {
  @Override
  public TypeDescriptor<Integer> valueType() {
    return BuiltinTypeDescriptors.INTEGER;
  }
}
