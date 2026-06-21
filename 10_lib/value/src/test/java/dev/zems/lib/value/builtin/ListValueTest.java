package dev.zems.lib.value.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("ListValue (injected descriptor + sound valueType)")
class ListValueTest {

  @SuppressWarnings("unchecked")
  private static Value<Object> up(Value<?> value) {
    return (Value<Object>) value;
  }

  @Test
  @DisplayName("equality is by elements only — the injected descriptor is marshalling metadata")
  void equalityIgnoresInjectedDescriptor() {
    var inferred = Value.listOf(1, 2);
    var typed = Value.listOfTyped(BuiltinTypeDescriptors.INTEGER, Value.of(1), Value.of(2));

    assertThat(typed).isEqualTo(inferred).hasSameHashCodeAs(inferred);
  }

  @Test
  @DisplayName("valueType uses the injected element descriptor when present")
  void valueTypeUsesInjectedDescriptor() {
    var union = TypeDescriptor.oneOf("v", BuiltinTypeDescriptors.STRING, BuiltinTypeDescriptors.INTEGER);
    var typed = Value.listOfTyped(union, Value.of("a"), Value.of(1));

    var descriptor = typed.valueType();
    assertThat(descriptor).isNotNull();
    assertThat(descriptor.qualifiedName()).isEqualTo("List<OneOf<java.lang.String | java.lang.Integer>>");
  }

  @Test
  @DisplayName("valueType infers a homogeneous list's descriptor from its elements")
  void valueTypeInfersHomogeneous() {
    var descriptor = Value.listOf("a", "b").valueType();
    assertThat(descriptor).isNotNull();
    assertThat(descriptor.qualifiedName()).isEqualTo("List<java.lang.String>");
  }

  @Test
  @DisplayName("valueType returns null for a heterogeneous list with no injected descriptor")
  void valueTypeNullForHeterogeneous() {
    List<Value<Object>> mixed = List.of(up(Value.of("a")), up(Value.of(1)));
    assertThat(Value.listOf(mixed).valueType()).isNull();
  }
}
