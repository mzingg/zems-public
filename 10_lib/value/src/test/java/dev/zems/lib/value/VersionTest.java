package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@ContractTest
@DisplayName("Version")
class VersionTest {

  private static final String QUALIFIED_FAULT = "expected <symbol>:<version> with symbol semver, jdk or jpms";

  static Stream<EqualPair> jpmsEqualPairs() {
    return Stream.of(
      new EqualPair("1.0", "1.0.0"),
      new EqualPair("01", "1"),
      new EqualPair("1a", "1.a"),
      new EqualPair("4294967297", "1")
    );
  }

  @Nested
  @DisplayName("parse")
  class Parse {

    @Test
    @DisplayName("a null dialect is checked before the text")
    void nullDialectIsCheckedFirst() {
      assertThatThrownBy(() -> Version.parse(null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Version dialect must not be null");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = { "", "   " })
    @DisplayName("blank text is refused")
    void blankTextIsRefused(String text) {
      assertThatThrownBy(() -> Version.parse(text, VersionDialect.SEMVER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Version text must not be null or empty");
    }

    @Test
    @DisplayName("null text is refused")
    void nullTextIsRefused() {
      assertThatThrownBy(() -> Version.parse(null, VersionDialect.JDK))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Version text must not be null or empty");
    }

    @Test
    @DisplayName("invalid text is framed with the dialect's display name, the text and the fault")
    void invalidTextNamesTheDialectAndTheFault() {
      assertThatThrownBy(() -> Version.parse("1.0.0-01", VersionDialect.SEMVER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid SemVer version '1.0.0-01': numeric identifier '01' has a leading zero");
      assertThatThrownBy(() -> Version.parse("abc", VersionDialect.MODULE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid JPMS module version 'abc': abc: Version string does not start with a number");
    }
  }

  @Nested
  @DisplayName("parseQualified")
  class ParseQualified {

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = { "1.0.0", "npm:1.0.0", ":1.0.0", "SEMVER:1.0.0" })
    @DisplayName("a missing or unknown symbol is refused with the qualified-form message")
    void missingOrUnknownSymbolIsRefused(String qualified) {
      assertThatThrownBy(() -> Version.parseQualified(qualified))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid qualified version '" + qualified + "': " + QUALIFIED_FAULT);
    }

    @Test
    @DisplayName("a known symbol with invalid text answers the dialect's own frame")
    void knownSymbolWithInvalidTextUsesTheDialectFrame() {
      assertThatThrownBy(() -> Version.parseQualified("semver:1.0"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid SemVer version '1.0': expected MAJOR.MINOR.PATCH");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = { "", "  ", "semver:" })
    @DisplayName("blank text, or a symbol with nothing after it, is refused as empty")
    void blankIsRefused(String qualified) {
      assertThatThrownBy(() -> Version.parseQualified(qualified))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Version text must not be null or empty");
    }

    @Test
    @DisplayName("only the first ':' separates the symbol, so a JPMS version may itself contain ':'")
    void onlyTheFirstColonSeparates() {
      Version version = Version.parseQualified("jpms:1:x");

      assertThat(version.dialect()).isEqualTo(VersionDialect.MODULE);
      assertThat(version.text()).isEqualTo("1:x");
    }

    @Test
    @DisplayName("reads back what toString() writes, keeping the text as written")
    void readsBackToString() {
      Version written = Version.parse("1.0", VersionDialect.MODULE);

      Version read = Version.parseQualified(written.toString());

      assertThat(written).hasToString("jpms:1.0");
      assertThat(read).isEqualTo(written);
      assertThat(read.text()).isEqualTo("1.0");
    }
  }

  @Nested
  @DisplayName("equals and hashCode")
  class Equality {

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.zems.lib.value.VersionTest#jpmsEqualPairs")
    @DisplayName("JPMS versions the JDK calls equal are equal and hash alike, whatever their text")
    void jpmsEqualVersionsHashAlike(EqualPair pair) {
      Version left = Version.parse(pair.left(), VersionDialect.MODULE);
      Version right = Version.parse(pair.right(), VersionDialect.MODULE);

      assertThat(left).isEqualTo(right);
      assertThat(left).hasSameHashCodeAs(right);
    }

    @Test
    @DisplayName("the same text under two dialects is never equal")
    void differentDialectsAreNeverEqual() {
      assertThat(Version.parse("25.0.1", VersionDialect.JDK)).isNotEqualTo(
        Version.parse("25.0.1", VersionDialect.MODULE)
      );
      assertThat(Version.parse("25.0.1", VersionDialect.SEMVER)).isNotEqualTo(
        Version.parse("25.0.1", VersionDialect.JDK)
      );
    }

    @Test
    @DisplayName("SemVer build metadata takes part in equality")
    void semverBuildMetadataCountsForEquality() {
      assertThat(Version.parse("1.0.0+a", VersionDialect.SEMVER)).isNotEqualTo(
        Version.parse("1.0.0+b", VersionDialect.SEMVER)
      );
      assertThat(Version.parse("1.0.0+a", VersionDialect.SEMVER)).isEqualTo(
        Version.parse("1.0.0+a", VersionDialect.SEMVER)
      );
      assertThat(Version.parse("1.0.0+a", VersionDialect.SEMVER)).hasSameHashCodeAs(
        Version.parse("1.0.0+a", VersionDialect.SEMVER)
      );
    }

    @Test
    @DisplayName("JDK versions follow Runtime.Version: the optional part counts for equality")
    void jdkOptionalPartCountsForEquality() {
      assertThat(Version.parse("25+7-foo", VersionDialect.JDK)).isNotEqualTo(
        Version.parse("25+7-bar", VersionDialect.JDK)
      );
      assertThat(Version.parse("25+7", VersionDialect.JDK)).isEqualTo(Version.parse("25+7", VersionDialect.JDK));
      assertThat(Version.parse("25+7", VersionDialect.JDK)).hasSameHashCodeAs(
        Version.parse("25+7", VersionDialect.JDK)
      );
    }
  }

  @Nested
  @DisplayName("comparePrecedence")
  class ComparePrecedence {

    @Test
    @DisplayName("normalises the JDK's distance to -1 or 1")
    void normalisesToSignum() {
      Version older = Version.parse("21", VersionDialect.JDK);
      Version newer = Version.parse("25", VersionDialect.JDK);

      assertThat(older.comparePrecedence(newer)).isEqualTo(-1);
      assertThat(newer.comparePrecedence(older)).isEqualTo(1);
      assertThat(newer.comparePrecedence(Version.parse("25", VersionDialect.JDK))).isZero();
    }

    @Test
    @DisplayName("JPMS orders by the JDK's rules, including build metadata")
    void jpmsOrdersByTheJdk() {
      assertThat(
        Version.parse("1.0+a", VersionDialect.MODULE).comparePrecedence(Version.parse("1.0+b", VersionDialect.MODULE))
      ).isEqualTo(-1);
      assertThat(
        Version.parse("1.0", VersionDialect.MODULE).comparePrecedence(Version.parse("1.0.0", VersionDialect.MODULE))
      ).isZero();
    }
  }

  record EqualPair(String left, String right) {
    @Override
    public String toString() {
      return left + " = " + right;
    }
  }
}
