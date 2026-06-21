package dev.zems.lib.value.builtin;

import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.time.LocalDate;
import java.util.Objects;

public record LocalDateValue(LocalDate localDate) implements BuiltInValue<LocalDate> {
  public LocalDateValue {
    Objects.requireNonNull(localDate, "LocalDate must not be null");
  }

  @Override
  public TypeDescriptor<LocalDate> valueType() {
    return BuiltinTypeDescriptors.LOCAL_DATE;
  }
}
