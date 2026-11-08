package dev.zems.lib.value.builtin;

import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
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

  @Override
  public LocalDateTime unbox() {
    return localDateTime;
  }
}
