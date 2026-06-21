package dev.zems.lib.value.builtin;

import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.time.ZonedDateTime;
import java.util.Objects;

public record ZonedDateTimeValue(ZonedDateTime zonedDateTime) implements BuiltInValue<ZonedDateTime> {
  public ZonedDateTimeValue {
    Objects.requireNonNull(zonedDateTime, "ZonedDateTime must not be null");
  }

  @Override
  public TypeDescriptor<ZonedDateTime> valueType() {
    return BuiltinTypeDescriptors.ZONED_DATE_TIME;
  }
}
