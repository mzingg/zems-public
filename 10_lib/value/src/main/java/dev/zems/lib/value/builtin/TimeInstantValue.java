package dev.zems.lib.value.builtin;

import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.time.Instant;
import java.util.Objects;

public record TimeInstantValue(Instant instant) implements BuiltInValue<Instant> {
  public TimeInstantValue {
    Objects.requireNonNull(instant, "Instant must not be null");
  }

  @Override
  public TypeDescriptor<Instant> valueType() {
    return BuiltinTypeDescriptors.INSTANT;
  }

  @Override
  public Instant unbox() {
    return instant;
  }
}
