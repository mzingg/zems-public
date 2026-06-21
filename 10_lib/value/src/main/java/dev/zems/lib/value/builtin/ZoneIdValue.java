package dev.zems.lib.value.builtin;

import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.time.ZoneId;
import java.util.Objects;

public record ZoneIdValue(ZoneId zoneId) implements BuiltInValue<ZoneId> {
  public ZoneIdValue {
    Objects.requireNonNull(zoneId, "ZoneId must not be null");
  }

  @Override
  public TypeDescriptor<ZoneId> valueType() {
    return BuiltinTypeDescriptors.ZONE_ID;
  }
}
