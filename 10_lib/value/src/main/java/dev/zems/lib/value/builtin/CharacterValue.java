package dev.zems.lib.value.builtin;

import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;

public record CharacterValue(char character) implements BuiltInValue<Character> {
  @Override
  public TypeDescriptor<Character> valueType() {
    return BuiltinTypeDescriptors.CHARACTER;
  }
}
