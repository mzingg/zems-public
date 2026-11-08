package dev.zems.lib.value.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("EnumValue")
class EnumValueTest {

  @Test
  @DisplayName("rejects a null constant with the documented message")
  void rejectsNullConstant() {
    assertThatThrownBy(() -> new EnumValue<Operation>(null))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("Enum constant must not be null");
  }

  @Test
  @DisplayName("a constant with a body reports the descriptor of its declaring enum, not of its anonymous subclass")
  void constantWithABodyReportsItsDeclaringEnumDescriptor() {
    EnumValue<Operation> value = new EnumValue<>(Operation.NEGATE);

    assertThat(Operation.NEGATE.getClass().isEnum()).as("fixture: the constant's class is anonymous").isFalse();
    assertThat(value.valueType()).isEqualTo(TypeDescriptor.of(Operation.class));
    assertThat(value.valueType().descriptorName()).isEqualTo("dev.zems.lib.value.builtin.EnumValueTest$Operation");
  }

  public enum Operation {
    NEGATE {
      @Override
      int apply(int operand) {
        return -operand;
      }
    };

    abstract int apply(int operand);
  }
}
