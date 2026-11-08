package dev.zems.lib.value.valueio.descriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("EvolutionPolicy")
class EvolutionPolicyTest {

  @Test
  @DisplayName("STRICT fails on unknown slots and unknown constants")
  void strictFailsOnBoth() {
    assertThat(EvolutionPolicy.STRICT).isEqualTo(
      new EvolutionPolicy(UnknownSlotPolicy.FAIL, UnknownConstantPolicy.FAIL)
    );
  }

  @Test
  @DisplayName("LENIENT skips unknown slots only; unknown constants still fail")
  void lenientSkipsSlotsButNotConstants() {
    assertThat(EvolutionPolicy.LENIENT).isEqualTo(
      new EvolutionPolicy(UnknownSlotPolicy.SKIP, UnknownConstantPolicy.FAIL)
    );
  }

  @Test
  @DisplayName("the one-argument constructor keeps its released meaning: unknown constants fail")
  void oneArgumentConstructorFailsOnUnknownConstants() {
    assertThat(new EvolutionPolicy(UnknownSlotPolicy.SKIP)).isEqualTo(EvolutionPolicy.LENIENT);
    assertThat(new EvolutionPolicy(UnknownSlotPolicy.FAIL)).isEqualTo(EvolutionPolicy.STRICT);
  }

  @Test
  @DisplayName("a null unknown-slot policy is refused with its name")
  void nullUnknownSlotsIsRefused() {
    assertThatThrownBy(() -> new EvolutionPolicy(null, UnknownConstantPolicy.FAIL))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("unknownSlots must not be null");
    assertThatThrownBy(() -> new EvolutionPolicy(null))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("unknownSlots must not be null");
  }

  @Test
  @DisplayName("a null unknown-constant policy is refused with its name")
  void nullUnknownConstantsIsRefused() {
    assertThatThrownBy(() -> new EvolutionPolicy(UnknownSlotPolicy.FAIL, null))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("unknownConstants must not be null");
  }
}
