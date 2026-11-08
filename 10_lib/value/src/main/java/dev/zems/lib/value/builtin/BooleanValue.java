package dev.zems.lib.value.builtin;

import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;

public record BooleanValue(boolean bool) implements BuiltInValue<Boolean> {
  @Override
  public TypeDescriptor<Boolean> valueType() {
    return BuiltinTypeDescriptors.BOOLEAN;
  }

  @Override
  public Boolean unbox() {
    return bool;
  }
}
