package dev.zems.lib.value.marshal.descriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.marshal.JournalingStateWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("UnionTypeDescriptor (oneOf)")
class UnionTypeDescriptorTest {

  @Test
  @DisplayName("rejects a blank descriptor name")
  void rejectsBlankName() {
    assertThatThrownBy(() -> TypeDescriptor.oneOf(" ", BuiltinTypeDescriptors.STRING))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("descriptorName must not be blank");
  }

  @Test
  @DisplayName("rejects an empty branch set")
  void rejectsEmptyBranches() {
    assertThatThrownBy(() -> TypeDescriptor.oneOf("u"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("at least one branch");
  }

  @Test
  @DisplayName("rejects a null branch with a sharpened message")
  void rejectsNullBranch() {
    assertThatThrownBy(() -> TypeDescriptor.oneOf("u", BuiltinTypeDescriptors.STRING, null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("oneOf branch 1 must not be null");
  }

  @Test
  @DisplayName("rejects a nested oneOf branch")
  void rejectsNestedUnion() {
    var inner = TypeDescriptor.oneOf("inner", BuiltinTypeDescriptors.STRING);
    assertThatThrownBy(() -> TypeDescriptor.oneOf("outer", BuiltinTypeDescriptors.LONG, inner))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("flatten the union");
  }

  @Test
  @DisplayName("qualifiedName lists the branches in declared order")
  void qualifiedNameListsBranches() {
    var union = TypeDescriptor.oneOf("u", BuiltinTypeDescriptors.STRING, BuiltinTypeDescriptors.LONG);
    assertThat(union.qualifiedName()).isEqualTo("OneOf<java.lang.String | java.lang.Long>");
  }

  @Test
  @DisplayName("a single-branch union writes no discriminator (wire == the sole branch)")
  void singleBranchWritesNoDiscriminator() {
    var union = TypeDescriptor.oneOf("u", BuiltinTypeDescriptors.STRING);
    var writer = new JournalingStateWriter();

    union.write(writer, "hi");

    assertThat(writer.journal()).containsExactly("writeString(0, value, hi)");
  }

  @Test
  @DisplayName("a multi-branch union writes the branch index then the payload record")
  void multiBranchWritesDiscriminator() {
    var union = TypeDescriptor.oneOf("u", BuiltinTypeDescriptors.STRING, BuiltinTypeDescriptors.LONG);
    var writer = new JournalingStateWriter();

    union.write(writer, 42L);

    assertThat(writer.journal()).containsExactly(
      "writeInt(0, $branch, 1)",
      "writeRecord(1, $value, java.lang.Long, 42)"
    );
  }

  @Test
  @DisplayName("equality is by descriptor name and branches")
  void equalityByNameAndBranches() {
    var a = TypeDescriptor.oneOf("u", BuiltinTypeDescriptors.STRING, BuiltinTypeDescriptors.LONG);
    var b = TypeDescriptor.oneOf("u", BuiltinTypeDescriptors.STRING, BuiltinTypeDescriptors.LONG);
    var fewerBranches = TypeDescriptor.oneOf("u", BuiltinTypeDescriptors.STRING);

    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a).isNotEqualTo(fewerBranches);
  }
}
