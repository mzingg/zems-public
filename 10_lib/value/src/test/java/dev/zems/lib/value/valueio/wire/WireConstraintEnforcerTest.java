package dev.zems.lib.value.valueio.wire;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("WireConstraintEnforcer negative-length rejection")
class WireConstraintEnforcerTest {

  @Test
  @DisplayName("negative string length is a structured violation")
  void negativeStringLength() {
    assertThatThrownBy(() -> WireConstraintEnforcer.checkStringLength(-1, WireConstraints.SECURE_DEFAULTS))
      .isInstanceOf(WireConstraintViolationException.class)
      .hasMessageContaining("maxStringLength")
      .hasMessageContaining("negative");
  }

  @Test
  @DisplayName("negative array length is a structured violation")
  void negativeArrayLength() {
    assertThatThrownBy(() -> WireConstraintEnforcer.checkArrayLength(-5, WireConstraints.SECURE_DEFAULTS))
      .isInstanceOf(WireConstraintViolationException.class)
      .hasMessageContaining("maxArrayLength")
      .hasMessageContaining("negative");
  }

  @Test
  @DisplayName("negative map entry count is a structured violation")
  void negativeMapEntries() {
    assertThatThrownBy(() -> WireConstraintEnforcer.checkMapEntries(-1, WireConstraints.SECURE_DEFAULTS))
      .isInstanceOf(WireConstraintViolationException.class)
      .hasMessageContaining("maxMapEntries")
      .hasMessageContaining("negative");
  }

  @Test
  @DisplayName("a negative length is rejected even under UNCHECKED bounds")
  void negativeRejectedEvenWhenUnchecked() {
    assertThatThrownBy(() -> WireConstraintEnforcer.checkArrayLength(-1, WireConstraints.UNCHECKED))
      .isInstanceOf(WireConstraintViolationException.class)
      .hasMessageContaining("maxArrayLength");
  }
}
