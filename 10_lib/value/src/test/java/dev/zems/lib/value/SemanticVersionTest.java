package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@ContractTest
@DisplayName("SemanticVersion")
class SemanticVersionTest {

  private static final String CORE_SHAPE = "expected MAJOR.MINOR.PATCH";

  static Stream<Fault> faults() {
    return Stream.of(
      new Fault("1.0", CORE_SHAPE),
      new Fault("1.x.0", CORE_SHAPE),
      new Fault("1.0.0.0", CORE_SHAPE),
      new Fault("1..0", CORE_SHAPE),
      new Fault("01.0.0", "numeric identifier '01' has a leading zero"),
      new Fault("1.00.0", "numeric identifier '00' has a leading zero"),
      new Fault("1.0.0-01", "numeric identifier '01' has a leading zero"),
      new Fault("1.0.0-", "empty identifier"),
      new Fault("1.0.0-a..b", "empty identifier"),
      new Fault("1.0.0+", "empty identifier"),
      new Fault("1.0.0-a_b", "identifier 'a_b' has a character outside [0-9A-Za-z-]"),
      new Fault("1.0.0+build.ü", "identifier 'ü' has a character outside [0-9A-Za-z-]")
    );
  }

  @Nested
  @DisplayName("parse")
  class Parse {

    @Test
    @DisplayName("splits build at the first '+' and pre-release at the first '-' of the rest")
    void splitsCorePreReleaseAndBuild() {
      SemanticVersion version = SemanticVersion.parse("1.2.3-alpha-1.7+build.01-x");

      assertThat(List.of(version.major(), version.minor(), version.patch())).containsExactly(
        BigInteger.ONE,
        BigInteger.TWO,
        BigInteger.valueOf(3)
      );
      assertThat(version.preRelease()).containsExactly("alpha-1", "7");
      assertThat(version.build()).containsExactly("build", "01-x");
    }

    @Test
    @DisplayName("core numbers have no size limit")
    void coreNumbersAreUnbounded() {
      SemanticVersion version = SemanticVersion.parse("99999999999999999999.0.0");

      assertThat(version.major()).isEqualTo(new BigInteger("99999999999999999999"));
    }

    @ParameterizedTest(name = "accepts \"{0}\"")
    @ValueSource(strings = { "0.0.0", "1.0.0-0", "1.0.0-0a", "1.0.0+01", "1.0.0-x-y-z.--", "1.0.0-rc.1+build.1" })
    @DisplayName("accepts a single zero, an alphanumeric identifier led by 0, and a leading zero in build metadata")
    void acceptsTheGrammarsEdges(String text) {
      assertThatCode(() -> SemanticVersion.parse(text)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @MethodSource("dev.zems.lib.value.SemanticVersionTest#faults")
    @DisplayName("rejects invalid text with the fault only, never the dialect frame")
    void rejectsWithTheFault(Fault fault) {
      assertThatThrownBy(() -> SemanticVersion.parse(fault.text()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(fault.message());
    }
  }

  @Nested
  @DisplayName("comparePrecedence")
  class ComparePrecedence {

    @Test
    @DisplayName("the SemVer 11 example chain is strictly increasing")
    void specificationChainIsStrictlyIncreasing() {
      List<SemanticVersion> chain = Stream.of(
        "1.0.0-alpha",
        "1.0.0-alpha.1",
        "1.0.0-alpha.beta",
        "1.0.0-beta",
        "1.0.0-beta.2",
        "1.0.0-beta.11",
        "1.0.0-rc.1",
        "1.0.0",
        "2.0.0",
        "2.1.0",
        "2.1.1"
      )
        .map(SemanticVersion::parse)
        .toList();

      IntStream.range(1, chain.size()).forEach(i -> {
        assertThat(chain.get(i - 1).comparePrecedence(chain.get(i)))
          .as("%s < %s", i - 1, i)
          .isNegative();
        assertThat(chain.get(i).comparePrecedence(chain.get(i - 1)))
          .as("%s > %s", i, i - 1)
          .isPositive();
      });
    }

    @Test
    @DisplayName("core numbers compare by value, not by text")
    void coreComparesNumerically() {
      assertThat(SemanticVersion.parse("1.9.0").comparePrecedence(SemanticVersion.parse("1.10.0"))).isNegative();
      assertThat(
        SemanticVersion.parse("99999999999999999999.0.0").comparePrecedence(SemanticVersion.parse("9.0.0"))
      ).isPositive();
    }

    @Test
    @DisplayName("a numeric identifier sorts before an alphanumeric one, and alphanumerics compare in ASCII order")
    void identifierKindsAndAsciiOrder() {
      assertThat(SemanticVersion.parse("1.0.0-9").comparePrecedence(SemanticVersion.parse("1.0.0-a"))).isNegative();
      assertThat(SemanticVersion.parse("1.0.0-B").comparePrecedence(SemanticVersion.parse("1.0.0-a"))).isNegative();
      assertThat(
        SemanticVersion.parse("1.0.0-rc.2").comparePrecedence(SemanticVersion.parse("1.0.0-rc.10"))
      ).isNegative();
    }

    @Test
    @DisplayName("build metadata never counts")
    void buildMetadataIsIgnored() {
      assertThat(SemanticVersion.parse("1.0.0+a").comparePrecedence(SemanticVersion.parse("1.0.0+b"))).isZero();
      assertThat(SemanticVersion.parse("1.0.0+a").comparePrecedence(SemanticVersion.parse("1.0.0"))).isZero();
      assertThat(
        SemanticVersion.parse("1.0.0-rc.1+z").comparePrecedence(SemanticVersion.parse("1.0.0-rc.1+a"))
      ).isZero();
    }
  }

  record Fault(String text, String message) {
    @Override
    public String toString() {
      return text;
    }
  }
}
