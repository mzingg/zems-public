package dev.zems.lib.value;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("Versions")
class VersionsTest {

  @Nested
  @DisplayName("comparePrecedence")
  class ComparePrecedence {

    @Test
    @DisplayName("answers -1, 0 or 1 by the dialect's precedence")
    void answersTheSignOfThePrecedence() {
      Value<Version> older = Value.versionOf("21", VersionDialect.JDK);
      Value<Version> newer = Value.versionOf("25", VersionDialect.JDK);

      assertThat(Versions.comparePrecedence(older, newer)).hasIntValue(-1);
      assertThat(Versions.comparePrecedence(newer, older)).hasIntValue(1);
      assertThat(Versions.comparePrecedence(newer, Value.versionOf("25", VersionDialect.JDK))).hasIntValue(0);
    }

    @Test
    @DisplayName("a state marker comes back as the same instance, left first")
    void markersPropagateByIdentityLeftFirst() {
      Value<Version> version = Value.versionOf("1.0.0", VersionDialect.SEMVER);
      Value<Version> leftError = Value.errorMessage("left broke", Version.class);
      Value<Version> rightMissing = Value.undefined();

      assertThat(Versions.comparePrecedence(leftError, version)).isSameAs(leftError);
      assertThat(Versions.comparePrecedence(version, rightMissing)).isSameAs(rightMissing);
      assertThat(Versions.comparePrecedence(leftError, rightMissing)).isSameAs(leftError);
    }

    @Test
    @DisplayName("different dialects answer an UnsupportedValue expecting an Integer")
    void differentDialectsAreUnsupported() {
      assertThat(
        Versions.comparePrecedence(
          Value.versionOf("1.0", VersionDialect.MODULE),
          Value.versionOf("25", VersionDialect.JDK)
        )
      )
        .isUnsupported()
        .hasUnsupportedReason("Cannot compare a JPMS module version with a JDK version: dialects differ")
        .hasExpectedTypeName("java.lang.Integer");
    }

    @Test
    @DisplayName("a null reference argument is a caller bug and throws")
    void nullArgumentThrows() {
      Value<Version> version = Value.versionOf("1.0.0", VersionDialect.SEMVER);

      assertThatThrownBy(() -> Versions.comparePrecedence(null, version))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("left must not be null");
      assertThatThrownBy(() -> Versions.comparePrecedence(version, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("right must not be null");
    }
  }

  @Nested
  @DisplayName("atLeast")
  class AtLeast {

    @Test
    @DisplayName("holds for equal or higher precedence only")
    void holdsForEqualOrHigherPrecedence() {
      Value<Version> need = Value.versionOf("1.0.0", VersionDialect.SEMVER);

      assertThat(Versions.atLeast(Value.versionOf("1.0.1", VersionDialect.SEMVER), need)).hasBooleanValue(true);
      assertThat(Versions.atLeast(Value.versionOf("1.0.0+b", VersionDialect.SEMVER), need)).hasBooleanValue(true);
      assertThat(Versions.atLeast(Value.versionOf("0.9.9", VersionDialect.SEMVER), need)).hasBooleanValue(false);
    }

    @Test
    @DisplayName("a state marker comes back as the same instance, left first")
    void markersPropagateByIdentityLeftFirst() {
      Value<Version> version = Value.versionOf("1.0.0", VersionDialect.SEMVER);
      Value<Version> leftUnsupported = Value.unsupported("no version here", Version.class);
      Value<Version> rightError = Value.errorMessage("right broke", Version.class);

      assertThat(Versions.atLeast(leftUnsupported, version)).isSameAs(leftUnsupported);
      assertThat(Versions.atLeast(version, rightError)).isSameAs(rightError);
      assertThat(Versions.atLeast(leftUnsupported, rightError)).isSameAs(leftUnsupported);
    }

    @Test
    @DisplayName("different dialects answer an UnsupportedValue expecting a Boolean")
    void differentDialectsAreUnsupported() {
      assertThat(
        Versions.atLeast(Value.versionOf("25", VersionDialect.JDK), Value.versionOf("1.0.0", VersionDialect.SEMVER))
      )
        .isUnsupported()
        .hasUnsupportedReason("Cannot compare a JDK version with a SemVer version: dialects differ")
        .hasExpectedTypeName("java.lang.Boolean");
    }

    @Test
    @DisplayName("a null reference argument is a caller bug and throws")
    void nullArgumentThrows() {
      Value<Version> version = Value.versionOf("1.0.0", VersionDialect.SEMVER);

      assertThatThrownBy(() -> Versions.atLeast(null, version))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("have must not be null");
      assertThatThrownBy(() -> Versions.atLeast(version, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("need must not be null");
    }
  }
}
