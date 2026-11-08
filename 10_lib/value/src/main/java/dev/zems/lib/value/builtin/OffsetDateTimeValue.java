package dev.zems.lib.value.builtin;

import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.time.OffsetDateTime;
import java.util.Objects;

public record OffsetDateTimeValue(OffsetDateTime offsetDateTime) implements BuiltInValue<OffsetDateTime> {
  public OffsetDateTimeValue {
    Objects.requireNonNull(offsetDateTime, "OffsetDateTime must not be null");
  }

  @Override
  public TypeDescriptor<OffsetDateTime> valueType() {
    return BuiltinTypeDescriptors.OFFSET_DATE_TIME;
  }

  @Override
  public OffsetDateTime unbox() {
    return offsetDateTime;
  }
}
