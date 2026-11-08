package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.UnitAtom.PrefixRule;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("UnitAtom")
class UnitAtomTest {

  private static final Set<Prefix> BINARY = EnumSet.of(
    Prefix.KIBI,
    Prefix.MEBI,
    Prefix.GIBI,
    Prefix.TEBI,
    Prefix.PEBI,
    Prefix.EXBI,
    Prefix.ZEBI,
    Prefix.YOBI,
    Prefix.ROBI,
    Prefix.QUEBI
  );
  private static final Set<Prefix> MULTIPLES_FROM_KILO = EnumSet.of(
    Prefix.KILO,
    Prefix.MEGA,
    Prefix.GIGA,
    Prefix.TERA,
    Prefix.PETA,
    Prefix.EXA,
    Prefix.ZETTA,
    Prefix.YOTTA,
    Prefix.RONNA,
    Prefix.QUETTA
  );

  @Nested
  @DisplayName("PrefixRule")
  class Rules {

    @Test
    @DisplayName("NONE takes no prefix")
    void noneTakesNothing() {
      assertThat(takenBy(PrefixRule.NONE)).isEmpty();
    }

    @Test
    @DisplayName("ALL_DECIMAL takes the 24 SI prefixes and no binary one")
    void allDecimalTakesTheSiPrefixes() {
      Set<Prefix> taken = takenBy(PrefixRule.ALL_DECIMAL);

      assertThat(taken).hasSize(24).doesNotContainAnyElementsOf(BINARY);
      assertThat(taken).contains(Prefix.QUECTO, Prefix.DECA, Prefix.HECTO, Prefix.MICRO, Prefix.QUETTA);
    }

    @Test
    @DisplayName("DECIMAL_MULTIPLES_FROM_KILO takes kilo up to quetta only")
    void multiplesFromKiloTakesKiloUp() {
      assertThat(takenBy(PrefixRule.DECIMAL_MULTIPLES_FROM_KILO)).isEqualTo(MULTIPLES_FROM_KILO);
    }

    @Test
    @DisplayName("DECIMAL_MULTIPLES_FROM_KILO_AND_BINARY adds the ten binary prefixes")
    void multiplesFromKiloAndBinaryAddsTheBinaryPrefixes() {
      Set<Prefix> expected = EnumSet.copyOf(MULTIPLES_FROM_KILO);
      expected.addAll(BINARY);

      assertThat(takenBy(PrefixRule.DECIMAL_MULTIPLES_FROM_KILO_AND_BINARY)).isEqualTo(expected);
    }

    private static Set<Prefix> takenBy(PrefixRule rule) {
      return Arrays.stream(Prefix.values()).filter(rule::takes).collect(Collectors.toSet());
    }
  }

  @Test
  @DisplayName("a prefixed atom joins the prefix symbol, keeps dimension and system, and takes no further prefix")
  void aPrefixedAtomKeepsItsBaseTraits() {
    UnitAtom metre = UnitAtom.of("m", Dimension.of(BaseDimension.LENGTH), UnitSystem.PHYSICAL, PrefixRule.ALL_DECIMAL);

    UnitAtom kilometre = metre.prefixedWith(Prefix.KILO);

    assertThat(kilometre.symbol()).isEqualTo("km");
    assertThat(kilometre.dimension()).isEqualTo(Dimension.of(BaseDimension.LENGTH));
    assertThat(kilometre.system()).isEqualTo(UnitSystem.PHYSICAL);
    assertThat(kilometre.isPrefixed()).isTrue();
    assertThat(kilometre.takesAnyPrefix()).isFalse();
    assertThat(metre.isPrefixed()).isFalse();
    assertThat(metre.takesAnyPrefix()).isTrue();
  }

  @Test
  @DisplayName("two atoms are equal by symbol")
  void atomsAreEqualBySymbol() {
    UnitAtom first = UnitAtom.of("m", Dimension.of(BaseDimension.LENGTH), UnitSystem.PHYSICAL, PrefixRule.ALL_DECIMAL);
    UnitAtom second = UnitAtom.of("m", Dimension.of(BaseDimension.LENGTH), UnitSystem.PHYSICAL, PrefixRule.NONE);

    assertThat(second).isEqualTo(first).hasSameHashCodeAs(first);
    assertThat(first).isNotEqualTo(
      UnitAtom.of("s", Dimension.of(BaseDimension.TIME), UnitSystem.PHYSICAL, PrefixRule.ALL_DECIMAL)
    );
  }
}
