package dev.zems.lib.value.builtin;

import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
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
}
