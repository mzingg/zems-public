package dev.zems.lib.value.builtin;

import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
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
}
