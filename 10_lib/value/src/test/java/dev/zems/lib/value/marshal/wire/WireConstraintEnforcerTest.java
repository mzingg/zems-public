package dev.zems.lib.value.marshal.wire;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contract test for {@link WireConstraintEnforcer}, focused on rejecting a negative declared length/size. A corrupt or
 * hostile wire can decode a count into a signed slot and wrap it negative; without this guard that surfaces as a
 * generic JDK error (negative array capacity) or a silently empty container instead of a structured violation.
 */
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
    // A negative size is never valid input — it is rejected regardless of the opt-out, since the > max test alone
    // (Integer.MAX_VALUE under UNCHECKED) would let it slip through to a JDK negative-capacity error.
    assertThatThrownBy(() -> WireConstraintEnforcer.checkArrayLength(-1, WireConstraints.UNCHECKED))
      .isInstanceOf(WireConstraintViolationException.class)
      .hasMessageContaining("maxArrayLength");
  }
}
