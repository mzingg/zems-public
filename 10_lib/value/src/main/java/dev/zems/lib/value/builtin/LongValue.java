package dev.zems.lib.value.builtin;

import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;

public record LongValue(long longValue) implements BuiltInValue<Long> {
  @Override
  public TypeDescriptor<Long> valueType() {
    return BuiltinTypeDescriptors.LONG;
  }
}
