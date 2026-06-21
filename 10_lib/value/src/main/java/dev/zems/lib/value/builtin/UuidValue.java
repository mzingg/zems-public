package dev.zems.lib.value.builtin;

import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.util.Objects;
import java.util.UUID;

public record UuidValue(UUID uuid) implements BuiltInValue<UUID> {
  public UuidValue {
    Objects.requireNonNull(uuid, "UUID must not be null");
  }

  @Override
  public TypeDescriptor<UUID> valueType() {
    return BuiltinTypeDescriptors.UUID_DESCRIPTOR;
  }
}
