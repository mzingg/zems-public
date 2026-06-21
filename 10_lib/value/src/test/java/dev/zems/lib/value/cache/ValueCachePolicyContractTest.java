package dev.zems.lib.value.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("ValueCachePolicy")
class ValueCachePolicyContractTest {

  @Nested
  @DisplayName("Constants")
  class Constants {

    @Test
    void defaultUsesSensibleBoundsForEveryType() {
      assertThat(ValueCachePolicy.DEFAULT.intMax()).isEqualTo(32_767);
      assertThat(ValueCachePolicy.DEFAULT.longMax()).isEqualTo(32_767);
      assertThat(ValueCachePolicy.DEFAULT.stringMax()).isEqualTo(1024);
    }

    @Test
    void aggressiveExtendsTheStringLruOnly() {
      assertThat(ValueCachePolicy.AGGRESSIVE.intMax()).isEqualTo(ValueCachePolicy.DEFAULT.intMax());
      assertThat(ValueCachePolicy.AGGRESSIVE.longMax()).isEqualTo(ValueCachePolicy.DEFAULT.longMax());
      assertThat(ValueCachePolicy.AGGRESSIVE.stringMax()).isGreaterThan(ValueCachePolicy.DEFAULT.stringMax());
    }

    @Test
    void minimalAndDisabledZeroEveryOptionalCache() {
      assertThat(ValueCachePolicy.MINIMAL).isEqualTo(new ValueCachePolicy(0, 0, 0, false));
      assertThat(ValueCachePolicy.DISABLED).isEqualTo(ValueCachePolicy.MINIMAL);
      assertThat(ValueCachePolicy.MINIMAL.stringIsUnbounded()).isFalse();
    }

    @Test
    void unboundedSpellsIntegerMaxForIntAndLongAndFlagsStringExplicitly() {
      assertThat(ValueCachePolicy.UNBOUNDED.intMax()).isEqualTo(Integer.MAX_VALUE);
      assertThat(ValueCachePolicy.UNBOUNDED.longMax()).isEqualTo(Integer.MAX_VALUE);
      assertThat(ValueCachePolicy.UNBOUNDED.stringMax()).isZero();
      assertThat(ValueCachePolicy.UNBOUNDED.stringIsUnbounded()).isTrue();
    }
  }

  @Nested
  @DisplayName("Validation")
  class Validation {

    @Test
    void rejectsNegativeIntMax() {
      assertThatThrownBy(() -> new ValueCachePolicy(-1, 0, 0, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("int.max")
        .hasMessageContaining("-1");
    }

    @Test
    void rejectsNegativeLongMax() {
      assertThatThrownBy(() -> new ValueCachePolicy(0, -5, 0, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("long.max")
        .hasMessageContaining("-5");
    }

    @Test
    void rejectsNegativeStringMax() {
      assertThatThrownBy(() -> new ValueCachePolicy(0, 0, -1, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("string.max")
        .hasMessageContaining("-1");
    }

    @Test
    void rejectsNonZeroStringMaxWhenStringIsUnbounded() {
      assertThatThrownBy(() -> new ValueCachePolicy(0, 0, 100, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("string.max")
        .hasMessageContaining("stringIsUnbounded")
        .hasMessageContaining("100");
    }
  }

  @Nested
  @DisplayName("parse(Map)")
  class Parse {

    @Test
    void emptyMapReturnsDefault() {
      assertThat(ValueCachePolicy.parse(Map.of())).isEqualTo(ValueCachePolicy.DEFAULT);
    }

    @Test
    void modeAloneSelectsPreset() {
      assertThat(ValueCachePolicy.parse(Map.of("zems.value.cache.mode", "aggressive"))).isEqualTo(
        ValueCachePolicy.AGGRESSIVE
      );
      assertThat(ValueCachePolicy.parse(Map.of("zems.value.cache.mode", "minimal"))).isEqualTo(
        ValueCachePolicy.MINIMAL
      );
      assertThat(ValueCachePolicy.parse(Map.of("zems.value.cache.mode", "disabled"))).isEqualTo(
        ValueCachePolicy.DISABLED
      );
      assertThat(ValueCachePolicy.parse(Map.of("zems.value.cache.mode", "unbounded"))).isEqualTo(
        ValueCachePolicy.UNBOUNDED
      );
    }

    @Test
    void modeIsCaseInsensitive() {
      assertThat(ValueCachePolicy.parse(Map.of("zems.value.cache.mode", "AGGRESSIVE"))).isEqualTo(
        ValueCachePolicy.AGGRESSIVE
      );
      assertThat(ValueCachePolicy.parse(Map.of("zems.value.cache.mode", "Default"))).isEqualTo(
        ValueCachePolicy.DEFAULT
      );
    }

    @Test
    void unknownModeFailsFast() {
      assertThatThrownBy(() -> ValueCachePolicy.parse(Map.of("zems.value.cache.mode", "wat")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mode")
        .hasMessageContaining("wat");
    }

    @Test
    void perTypeOverrideStacksOnTopOfMode() {
      var p = ValueCachePolicy.parse(
        Map.of(
          "zems.value.cache.mode",
          "minimal",
          "zems.value.cache.int.max",
          "100",
          "zems.value.cache.long.max",
          "200",
          "zems.value.cache.string.max",
          "300"
        )
      );
      assertThat(p).isEqualTo(new ValueCachePolicy(100, 200, 300, false));
    }

    @Test
    void unboundedLiteralResolvesToIntegerMaxForIntAndLongAndFlagsStringExplicitly() {
      var p = ValueCachePolicy.parse(
        Map.of(
          "zems.value.cache.int.max",
          "unbounded",
          "zems.value.cache.long.max",
          "UNBOUNDED",
          "zems.value.cache.string.max",
          " Unbounded "
        )
      );
      assertThat(p.intMax()).isEqualTo(Integer.MAX_VALUE);
      assertThat(p.longMax()).isEqualTo(Integer.MAX_VALUE);
      assertThat(p.stringMax()).isZero();
      assertThat(p.stringIsUnbounded()).isTrue();
    }

    @Test
    void zeroStringMaxIsHonouredAsExplicitDisable() {
      var p = ValueCachePolicy.parse(Map.of("zems.value.cache.string.max", "0"));
      assertThat(p.stringMax()).isZero();
    }

    @Test
    void nonNumericOverrideFailsFast() {
      assertThatThrownBy(() -> ValueCachePolicy.parse(Map.of("zems.value.cache.int.max", "many")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("int.max")
        .hasMessageContaining("many");
    }

    @Test
    void negativeOverrideFailsFast() {
      assertThatThrownBy(() -> ValueCachePolicy.parse(Map.of("zems.value.cache.int.max", "-1")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("int.max");
    }

    @Test
    void overflowOverrideFailsFast() {
      assertThatThrownBy(() -> ValueCachePolicy.parse(Map.of("zems.value.cache.int.max", "9999999999")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("int.max")
        .hasMessageContaining("9999999999");
    }
  }
}
