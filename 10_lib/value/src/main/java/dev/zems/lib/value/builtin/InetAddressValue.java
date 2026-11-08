package dev.zems.lib.value.builtin;

import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.net.InetAddress;
import java.util.Objects;

public record InetAddressValue(InetAddress inetAddress) implements BuiltInValue<InetAddress> {
  public InetAddressValue {
    Objects.requireNonNull(inetAddress, "InetAddress must not be null");
  }

  @Override
  public TypeDescriptor<InetAddress> valueType() {
    return BuiltinTypeDescriptors.INET_ADDRESS;
  }

  @Override
  public InetAddress unbox() {
    return inetAddress;
  }
}
