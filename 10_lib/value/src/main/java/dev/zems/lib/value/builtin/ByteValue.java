package dev.zems.lib.value.builtin;

import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;

public record ByteValue(byte byteValue) implements BuiltInValue<Byte> {
  @Override
  public TypeDescriptor<Byte> valueType() {
    return BuiltinTypeDescriptors.BYTE;
  }

  @Override
  public Byte unbox() {
    return byteValue;
  }
}
