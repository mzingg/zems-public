package dev.zems.lib.value.builtin;

import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.time.LocalDateTime;
import java.util.Objects;

public record LocalDateTimeValue(LocalDateTime localDateTime) implements BuiltInValue<LocalDateTime> {
  public LocalDateTimeValue {
    Objects.requireNonNull(localDateTime, "LocalDateTime must not be null");
  }

  @Override
  public TypeDescriptor<LocalDateTime> valueType() {
    return BuiltinTypeDescriptors.LOCAL_DATE_TIME;
  }
}
