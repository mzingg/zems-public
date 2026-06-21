package dev.zems.lib.value.builtin;

import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.time.Period;
import java.util.Objects;

public record PeriodValue(Period period) implements BuiltInValue<Period> {
  public PeriodValue {
    Objects.requireNonNull(period, "Period must not be null");
  }

  @Override
  public TypeDescriptor<Period> valueType() {
    return BuiltinTypeDescriptors.PERIOD;
  }
}
