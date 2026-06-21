package dev.zems.lib.value.builtin;

import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.time.LocalTime;
import java.util.Objects;

public record LocalTimeValue(LocalTime localTime) implements BuiltInValue<LocalTime> {
  public LocalTimeValue {
    Objects.requireNonNull(localTime, "LocalTime must not be null");
  }

  @Override
  public TypeDescriptor<LocalTime> valueType() {
    return BuiltinTypeDescriptors.LOCAL_TIME;
  }
}
