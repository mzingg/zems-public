package dev.zems.lib.value.builtin;

import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;

public record IntegerValue(int intValue) implements BuiltInValue<Integer> {
  @Override
  public TypeDescriptor<Integer> valueType() {
    return BuiltinTypeDescriptors.INTEGER;
  }

  @Override
  public Integer unbox() {
    return intValue;
  }
}
