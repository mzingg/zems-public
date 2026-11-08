package dev.zems.lib.value.builtin;

import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;

public record ShortValue(short shortValue) implements BuiltInValue<Short> {
  @Override
  public TypeDescriptor<Short> valueType() {
    return BuiltinTypeDescriptors.SHORT;
  }

  @Override
  public Short unbox() {
    return shortValue;
  }
}
