package dev.zems.lib.value.builtin;

import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;

public record ShortValue(short shortValue) implements BuiltInValue<Short> {
  @Override
  public TypeDescriptor<Short> valueType() {
    return BuiltinTypeDescriptors.SHORT;
  }
}
