package dev.zems.lib.value.builtin;

import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;

public record ByteValue(byte byteValue) implements BuiltInValue<Byte> {
  @Override
  public TypeDescriptor<Byte> valueType() {
    return BuiltinTypeDescriptors.BYTE;
  }
}
