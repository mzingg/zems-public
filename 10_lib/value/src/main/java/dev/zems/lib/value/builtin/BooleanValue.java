package dev.zems.lib.value.builtin;

import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;

public record BooleanValue(boolean bool) implements BuiltInValue<Boolean> {
  @Override
  public TypeDescriptor<Boolean> valueType() {
    return BuiltinTypeDescriptors.BOOLEAN;
  }
}
