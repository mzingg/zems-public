package dev.zems.lib.value.builtin;

import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.util.Objects;

public record StringValue(String string) implements BuiltInValue<String> {
  public StringValue {
    Objects.requireNonNull(string, "String must not be null");
  }

  @Override
  public TypeDescriptor<String> valueType() {
    return BuiltinTypeDescriptors.STRING;
  }
}
