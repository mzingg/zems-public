package dev.zems.lib.value.builtin;

import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.net.URI;
import java.util.Objects;

public record UrlValue(URI uri) implements BuiltInValue<URI> {
  public UrlValue {
    Objects.requireNonNull(uri, "URI must not be null");
  }

  @Override
  public TypeDescriptor<URI> valueType() {
    return BuiltinTypeDescriptors.URI_DESCRIPTOR;
  }
}
