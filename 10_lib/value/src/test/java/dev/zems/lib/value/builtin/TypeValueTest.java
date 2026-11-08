package dev.zems.lib.value.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.TypeName;
import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("TypeValue")
class TypeValueTest {

  @Test
  @DisplayName("rejects a null payload with the documented message")
  void rejectsNullTypeName() {
    assertThatThrownBy(() -> new TypeValue(null))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("TypeName must not be null");
  }

  @Test
  @DisplayName("reports the shared TYPE_NAME descriptor as its wire type")
  void valueTypeIsTheSharedDescriptor() {
    assertThat(new TypeValue(new TypeName("com.example.Person")).valueType()).isSameAs(
      BuiltinTypeDescriptors.TYPE_NAME
    );
  }

  @Test
  @DisplayName("renders its one component as the bare name, so a second component could not hide")
  void toStringShowsOnlyTheName() {
    assertThat(new TypeValue(new TypeName("com.example.Person"))).hasToString("TypeValue[typeName=com.example.Person]");
  }
}
