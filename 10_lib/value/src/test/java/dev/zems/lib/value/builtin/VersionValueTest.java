package dev.zems.lib.value.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.VersionDialect;
import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("VersionValue")
class VersionValueTest {

  @Test
  @DisplayName("reports its dialect's descriptor as its value type, never the dialect-less VERSION")
  void valueTypeIsTheDialectDescriptor() {
    assertThat(Value.versionOf("1.0.0", VersionDialect.SEMVER).valueType()).isSameAs(
      BuiltinTypeDescriptors.VERSION_SEMVER
    );
    assertThat(Value.versionOf("25", VersionDialect.JDK).valueType()).isSameAs(BuiltinTypeDescriptors.VERSION_JDK);
    assertThat(Value.versionOf("1.0", VersionDialect.MODULE).valueType()).isSameAs(
      BuiltinTypeDescriptors.VERSION_MODULE
    );
  }
}
