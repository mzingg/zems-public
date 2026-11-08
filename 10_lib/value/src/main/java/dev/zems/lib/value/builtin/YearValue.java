package dev.zems.lib.value.builtin;

import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.time.Year;
import java.util.Objects;

public record YearValue(Year year) implements BuiltInValue<Year> {
  public YearValue {
    Objects.requireNonNull(year, "Year must not be null");
  }

  @Override
  public TypeDescriptor<Year> valueType() {
    return BuiltinTypeDescriptors.YEAR;
  }

  @Override
  public Year unbox() {
    return year;
  }
}
