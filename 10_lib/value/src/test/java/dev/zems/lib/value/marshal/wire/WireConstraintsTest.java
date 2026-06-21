package dev.zems.lib.value.marshal.wire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.marshal.ChecksumAlgorithm;
import dev.zems.lib.value.marshal.Protocol;
import dev.zems.lib.value.marshal.ValueIo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("WireConstraints")
@ContractTest
class WireConstraintsTest {

  @Nested
  @DisplayName("Defaults")
  class Defaults {

    @Test
    void builderProducesSecureDefaults() {
      assertThat(WireConstraints.builder().build()).isEqualTo(WireConstraints.SECURE_DEFAULTS);
    }

    @Test
    void secureDefaultsMatchJacksonValues() {
      var d = WireConstraints.SECURE_DEFAULTS;
      assertThat(d.maxNestingDepth()).isEqualTo(1_000);
      assertThat(d.maxStringLength()).isEqualTo(5 * 1024 * 1024);
      assertThat(d.maxNumberLength()).isEqualTo(1_000);
      assertThat(d.maxArrayLength()).isEqualTo(1_000_000);
      assertThat(d.maxMapEntries()).isEqualTo(1_000_000);
      assertThat(d.duplicateKeyPolicy()).isEqualTo(WireConstraints.DuplicateKeyPolicy.FAIL);
      assertThat(d.allowNonFiniteNumbers()).isFalse();
    }

    @Test
    void uncheckedRelaxesEverything() {
      var u = WireConstraints.UNCHECKED;
      assertThat(u.maxNestingDepth()).isEqualTo(Integer.MAX_VALUE);
      assertThat(u.maxStringLength()).isEqualTo(Integer.MAX_VALUE);
      assertThat(u.maxNumberLength()).isEqualTo(Integer.MAX_VALUE);
      assertThat(u.maxArrayLength()).isEqualTo(Integer.MAX_VALUE);
      assertThat(u.maxMapEntries()).isEqualTo(Integer.MAX_VALUE);
      assertThat(u.duplicateKeyPolicy()).isEqualTo(WireConstraints.DuplicateKeyPolicy.ALLOW);
      assertThat(u.allowNonFiniteNumbers()).isTrue();
    }

    @Test
    void protocolFactoryUsesSecureDefaults() {
      assertThat(ValueIo.framed().wireConstraints()).isEqualTo(WireConstraints.SECURE_DEFAULTS);
      assertThat(ValueIo.streaming().wireConstraints()).isEqualTo(WireConstraints.SECURE_DEFAULTS);
    }
  }

  @Nested
  @DisplayName("Builder")
  class Builder {

    @Test
    void overrideStringLengthKeepsOtherDefaults() {
      var c = WireConstraints.builder().maxStringLength(42).build();
      assertThat(c.maxStringLength()).isEqualTo(42);
      assertThat(c.maxNestingDepth()).isEqualTo(WireConstraints.SECURE_DEFAULTS.maxNestingDepth());
      assertThat(c.duplicateKeyPolicy()).isEqualTo(WireConstraints.SECURE_DEFAULTS.duplicateKeyPolicy());
    }

    @Test
    void uncheckedSingleMethodOptOut() {
      assertThat(WireConstraints.builder().unchecked().build()).isEqualTo(WireConstraints.UNCHECKED);
    }

    @Test
    void chainedOverridesApplyInOrder() {
      var c = WireConstraints.builder().unchecked().maxStringLength(100).build();
      assertThat(c.maxStringLength()).isEqualTo(100);
      assertThat(c.maxNestingDepth()).isEqualTo(Integer.MAX_VALUE);
      assertThat(c.duplicateKeyPolicy()).isEqualTo(WireConstraints.DuplicateKeyPolicy.ALLOW);
    }

    @Test
    void duplicateKeyPolicyNullThrows() {
      assertThatThrownBy(() -> WireConstraints.builder().duplicateKeyPolicy(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("duplicateKeyPolicy");
    }
  }

  @Nested
  @DisplayName("Record validation")
  class RecordValidation {

    @Test
    void zeroDepthRejected() {
      assertThatThrownBy(() -> new WireConstraints(0, 1, 1, 1, 1, WireConstraints.DuplicateKeyPolicy.FAIL, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxNestingDepth");
    }

    @Test
    void negativeStringLengthRejected() {
      assertThatThrownBy(() -> new WireConstraints(1, -1, 1, 1, 1, WireConstraints.DuplicateKeyPolicy.FAIL, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxStringLength");
    }

    @Test
    void nullDuplicateKeyPolicyRejected() {
      assertThatThrownBy(() -> new WireConstraints(1, 1, 1, 1, 1, null, false))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("duplicateKeyPolicy");
    }
  }

  @Nested
  @DisplayName("Protocol chain")
  class ProtocolChain {

    @Test
    void withWireConstraintsReturnsNewInstance() {
      var custom = WireConstraints.builder().maxStringLength(100).build();
      var v1 = ValueIo.framed().withWireConstraints(custom);
      assertThat(v1.wireConstraints()).isSameAs(custom);
    }

    @Test
    void withWireConstraintsPreservesOtherOptions() {
      var v1 = ValueIo.framed()
        .withChecksum(ChecksumAlgorithm.SHA_256)
        .usingTypeVerification()
        .withWireConstraints(WireConstraints.UNCHECKED);

      assertThat(v1.checksumAlgorithm()).isEqualTo(ChecksumAlgorithm.SHA_256);
      assertThat(v1.typeVerificationEnabled()).isTrue();
      assertThat(v1.wireConstraints()).isEqualTo(WireConstraints.UNCHECKED);
    }

    @Test
    void withWireConstraintsRejectsNull() {
      assertThatThrownBy(() -> ValueIo.framed().withWireConstraints(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("wireConstraints");
    }
  }
}
