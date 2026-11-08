package dev.zems.lib.value.builtin;

import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.math.BigDecimal;
import java.util.Objects;

public record BigDecimalValue(BigDecimal bigDecimal) implements BuiltInValue<BigDecimal> {
  public BigDecimalValue {
    Objects.requireNonNull(bigDecimal, "BigDecimal must not be null");
  }

  @Override
  public TypeDescriptor<BigDecimal> valueType() {
    return BuiltinTypeDescriptors.BIG_DECIMAL;
  }

  @Override
  public BigDecimal unbox() {
    return bigDecimal;
  }
}
