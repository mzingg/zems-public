package dev.zems.lib.value.builtin;

import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.time.Duration;
import java.util.Objects;

public record DurationValue(Duration duration) implements BuiltInValue<Duration> {
  public DurationValue {
    Objects.requireNonNull(duration, "Duration must not be null");
  }

  @Override
  public TypeDescriptor<Duration> valueType() {
    return BuiltinTypeDescriptors.DURATION;
  }
}
