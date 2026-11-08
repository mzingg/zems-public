package dev.zems.lib.value.builtin;

import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;

public record LongValue(long longValue) implements BuiltInValue<Long> {
  @Override
  public TypeDescriptor<Long> valueType() {
    return BuiltinTypeDescriptors.LONG;
  }

  @Override
  public Long unbox() {
    return longValue;
  }
}
