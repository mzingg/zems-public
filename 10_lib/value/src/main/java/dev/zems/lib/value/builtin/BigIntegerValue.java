package dev.zems.lib.value.builtin;

import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.math.BigInteger;
import java.util.Objects;

public record BigIntegerValue(BigInteger bigInteger) implements BuiltInValue<BigInteger> {
  public BigIntegerValue {
    Objects.requireNonNull(bigInteger, "BigInteger must not be null");
  }

  @Override
  public TypeDescriptor<BigInteger> valueType() {
    return BuiltinTypeDescriptors.BIG_INTEGER;
  }
}
