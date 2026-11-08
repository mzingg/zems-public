package dev.zems.lib.value.builtin;

import dev.zems.lib.value.TypeName;
import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.util.Objects;

/**
 * A symbolic Java type name and nothing else: no {@code Class}, no binding, no lookup handle.
 *
 * <p>
 * The name is never resolved to a {@code Class}; no path that builds, reads, writes or unboxes one touches a class
 * loader, so a name read off the wire is as safe as any other string. {@code Value.typeOf(Class)} and
 * {@code Value.typeOf(String)} produce indistinguishable values: equality, hash and wire form are the name alone.
 */
public record TypeValue(TypeName typeName) implements BuiltInValue<TypeName> {
  public TypeValue {
    Objects.requireNonNull(typeName, "TypeName must not be null");
  }

  @Override
  public TypeDescriptor<TypeName> valueType() {
    return BuiltinTypeDescriptors.TYPE_NAME;
  }

  @Override
  public TypeName unbox() {
    return typeName;
  }
}
