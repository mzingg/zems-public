package dev.zems.lib.value.builtin;

import dev.zems.lib.value.Version;
import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.util.Objects;

/**
 * A {@link Version} with its dialect. The value type is the dialect's descriptor, so the short type symbol of a value
 * names its dialect ({@code semver}, {@code jdk} or {@code jpms}).
 */
public record VersionValue(Version version) implements BuiltInValue<Version> {
  public VersionValue {
    Objects.requireNonNull(version, "Version must not be null");
  }

  @Override
  public TypeDescriptor<Version> valueType() {
    return switch (version.dialect()) {
      case SEMVER -> BuiltinTypeDescriptors.VERSION_SEMVER;
      case JDK -> BuiltinTypeDescriptors.VERSION_JDK;
      case MODULE -> BuiltinTypeDescriptors.VERSION_MODULE;
    };
  }

  @Override
  public Version unbox() {
    return version;
  }
}
