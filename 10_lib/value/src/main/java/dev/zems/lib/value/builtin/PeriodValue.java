package dev.zems.lib.value.builtin;

import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
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

  @Override
  public Period unbox() {
    return period;
  }
}
