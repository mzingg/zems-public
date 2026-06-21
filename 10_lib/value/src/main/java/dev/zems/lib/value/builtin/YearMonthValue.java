package dev.zems.lib.value.builtin;

import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.time.YearMonth;
import java.util.Objects;

public record YearMonthValue(YearMonth yearMonth) implements BuiltInValue<YearMonth> {
  public YearMonthValue {
    Objects.requireNonNull(yearMonth, "YearMonth must not be null");
  }

  @Override
  public TypeDescriptor<YearMonth> valueType() {
    return BuiltinTypeDescriptors.YEAR_MONTH;
  }
}
