package dev.zems.lib.value.builtin;

import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;

public record CharacterValue(char character) implements BuiltInValue<Character> {
  @Override
  public TypeDescriptor<Character> valueType() {
    return BuiltinTypeDescriptors.CHARACTER;
  }

  @Override
  public Character unbox() {
    return character;
  }
}
