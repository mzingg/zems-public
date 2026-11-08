package dev.zems.lib.value;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;

import dev.zems.lib.common.junit.JourneyTest;
import dev.zems.lib.value.junit.RoundTripFormatHarness;
import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Version — a value that carries its dialect and parses, orders and compares by its rules")
class VersionJourneyTest {

  static Stream<RoundTripCase> roundTripCases() {
    List<VersionCase> versions = List.of(
      new VersionCase(VersionDialect.SEMVER, "1.4.0-rc.1+build.7", "semver:1.4.0-rc.1+build.7"),
      new VersionCase(VersionDialect.JDK, "25.0.4.1+1", "jdk:25.0.4.1+1"),
      new VersionCase(VersionDialect.MODULE, "1.0", "jpms:1.0")
    );
    return RoundTripFormatHarness.all().flatMap(format ->
      versions.stream().map(version -> new RoundTripCase(format, version))
    );
  }

  @Test
  @JourneyTest(speakingId = "version-value-with-dialects", acceptance = "dialect")
  @DisplayName("a parsed version reports the dialect it was parsed under")
  void aParsedVersionReportsItsDialect() {
    Value<Version> semver = Value.versionOf("0.3.0", VersionDialect.SEMVER);
    Value<Version> jdk = Value.versionOf("25.0.4.1+1", VersionDialect.JDK);
    Value<Version> module = Value.versionOf("1.0", VersionDialect.MODULE);

    assertThat(semver.unbox().dialect()).isEqualTo(VersionDialect.SEMVER);
    assertThat(jdk.unbox().dialect()).isEqualTo(VersionDialect.JDK);
    assertThat(module.unbox().dialect()).isEqualTo(VersionDialect.MODULE);
    assertThat(semver.isVersion()).isTrue();
    assertThat(semver.asVersion()).hasValue(semver.unbox());
    assertThat(Value.of("0.3.0").isVersion()).isFalse();
    assertThat(Value.of("0.3.0").asVersion()).isEmpty();
  }

  @Test
  @JourneyTest(speakingId = "version-value-with-dialects", acceptance = "dialect")
  @DisplayName("the same text parsed under three dialects yields three distinct values")
  void theSameTextUnderTwoDialectsYieldsDistinctValues() {
    Value<Version> semver = Value.versionOf("25.0.1", VersionDialect.SEMVER);
    Value<Version> jdk = Value.versionOf("25.0.1", VersionDialect.JDK);
    Value<Version> module = Value.versionOf("25.0.1", VersionDialect.MODULE);

    assertThat(semver.unbox().text()).isEqualTo("25.0.1");
    assertThat(jdk.unbox().text()).isEqualTo("25.0.1");
    assertThat(module.unbox().text()).isEqualTo("25.0.1");
    assertThat(semver).isNotEqualTo(jdk);
    assertThat(semver).isNotEqualTo(module);
    assertThat(jdk).isNotEqualTo(module);
    assertThat(semver.unbox()).isNotEqualTo(jdk.unbox());
  }

  @Test
  @JourneyTest(speakingId = "version-value-with-dialects", acceptance = "strict")
  @DisplayName("1.0 is an error under SemVer and JDK and a version under JPMS")
  void oneDotZeroIsValidOnlyUnderJpms() {
    assertThat(Value.versionOf("1.0", VersionDialect.SEMVER))
      .isError()
      .hasErrorMessage("Invalid SemVer version '1.0': expected MAJOR.MINOR.PATCH")
      .hasExpectedTypeName("dev.zems.lib.value.Version");
    assertThat(Value.versionOf("1.0", VersionDialect.JDK))
      .isError()
      .hasErrorMessage("Invalid JDK version '1.0': Invalid version string: '1.0'");

    Value<Version> module = Value.versionOf("1.0", VersionDialect.MODULE);
    assertThat(module).isPresent();
    assertThat(module.unbox().text()).isEqualTo("1.0");
  }

  @Test
  @JourneyTest(speakingId = "version-value-with-dialects", acceptance = "strict")
  @DisplayName("a dotted pre-release is a version under SemVer and an error under JDK")
  void aDottedPreReleaseIsValidUnderSemverOnly() {
    Value<Version> semver = Value.versionOf("1.4.0-rc.1", VersionDialect.SEMVER);
    assertThat(semver).isPresent();
    assertThat(semver.unbox().text()).isEqualTo("1.4.0-rc.1");

    assertThat(Value.versionOf("1.4.0-rc.1", VersionDialect.JDK))
      .isError()
      .hasErrorMessage("Invalid JDK version '1.4.0-rc.1': Invalid version string: '1.4.0-rc.1'");
    assertThat(Value.versionOf("1.4-rc.1", VersionDialect.JDK))
      .isError()
      .hasErrorMessage(
        "Invalid JDK version '1.4-rc.1': optional component must be preceded by a pre-release component or '+': '1.4-rc.1'"
      );
  }

  @Test
  @JourneyTest(speakingId = "version-value-with-dialects", acceptance = "strict")
  @DisplayName("a four-component JDK version with a build number parses")
  void aFourComponentJdkVersionParses() {
    Value<Version> jdk = Value.versionOf("25.0.4.1+1", VersionDialect.JDK);

    assertThat(jdk).isPresent();
    assertThat(jdk.unbox().text()).isEqualTo("25.0.4.1+1");
  }

  @Test
  @JourneyTest(speakingId = "version-value-with-dialects", acceptance = "order")
  @DisplayName("a pre-release sorts before its release in every dialect")
  void aPreReleaseSortsBeforeItsRelease() {
    assertThat(
      Versions.comparePrecedence(
        Value.versionOf("1.0.0-rc.1", VersionDialect.SEMVER),
        Value.versionOf("1.0.0", VersionDialect.SEMVER)
      )
    ).hasIntValue(-1);
    assertThat(
      Versions.comparePrecedence(
        Value.versionOf("25-ea", VersionDialect.JDK),
        Value.versionOf("25", VersionDialect.JDK)
      )
    ).hasIntValue(-1);
    assertThat(
      Versions.comparePrecedence(
        Value.versionOf("1.0-rc1", VersionDialect.MODULE),
        Value.versionOf("1.0", VersionDialect.MODULE)
      )
    ).hasIntValue(-1);
    assertThat(
      Versions.comparePrecedence(
        Value.versionOf("1.0.0", VersionDialect.SEMVER),
        Value.versionOf("1.0.0-rc.1", VersionDialect.SEMVER)
      )
    ).hasIntValue(1);
  }

  @Test
  @JourneyTest(speakingId = "version-value-with-dialects", acceptance = "order")
  @DisplayName("SemVer ignores build metadata in precedence; the JDK dialects order by it")
  void buildMetadataCountsOnlyInTheJdkDialects() {
    assertThat(
      Versions.comparePrecedence(
        Value.versionOf("1.0.0+a", VersionDialect.SEMVER),
        Value.versionOf("1.0.0+b", VersionDialect.SEMVER)
      )
    ).hasIntValue(0);
    assertThat(
      Versions.comparePrecedence(
        Value.versionOf("25+7", VersionDialect.JDK),
        Value.versionOf("25+8", VersionDialect.JDK)
      )
    ).hasIntValue(-1);
    assertThat(
      Versions.comparePrecedence(
        Value.versionOf("1.0+a", VersionDialect.MODULE),
        Value.versionOf("1.0+b", VersionDialect.MODULE)
      )
    ).hasIntValue(-1);
  }

  @Test
  @JourneyTest(speakingId = "version-value-with-dialects", acceptance = "order")
  @DisplayName("SemVer numeric identifiers order by value, not by text")
  void semverNumericIdentifiersOrderByValue() {
    assertThat(
      Versions.comparePrecedence(
        Value.versionOf("1.9.0", VersionDialect.SEMVER),
        Value.versionOf("1.10.0", VersionDialect.SEMVER)
      )
    ).hasIntValue(-1);
    assertThat(
      Versions.comparePrecedence(
        Value.versionOf("1.0.0-beta.2", VersionDialect.SEMVER),
        Value.versionOf("1.0.0-beta.11", VersionDialect.SEMVER)
      )
    ).hasIntValue(-1);
  }

  @Test
  @JourneyTest(speakingId = "version-value-with-dialects", acceptance = "equal")
  @DisplayName("under JPMS 1.0 equals 1.0.0, hashes alike and keeps its text as written")
  void jpmsTrailingZerosAreEqualAndHashAlike() {
    Value<Version> shorter = Value.versionOf("1.0", VersionDialect.MODULE);
    Value<Version> longer = Value.versionOf("1.0.0", VersionDialect.MODULE);

    assertThat(shorter).isEqualTo(longer);
    assertThat(shorter.unbox()).isEqualTo(longer.unbox());
    assertThat(shorter.unbox()).hasSameHashCodeAs(longer.unbox());
    assertThat(shorter).hasSameHashCodeAs(longer);
    assertThat(shorter.unbox().text()).isEqualTo("1.0");
    assertThat(longer.unbox().text()).isEqualTo("1.0.0");

    Set<Version> distinct = new HashSet<>(List.of(shorter.unbox(), longer.unbox()));
    assertThat(distinct).as("equal versions collapse in a hash set").hasSize(1);
  }

  @Test
  @JourneyTest(speakingId = "version-value-with-dialects", acceptance = "equal")
  @DisplayName("under SemVer a build-metadata difference is unequal yet of equal precedence")
  void semverBuildMetadataSplitsEqualityFromPrecedence() {
    Value<Version> first = Value.versionOf("1.0.0+a", VersionDialect.SEMVER);
    Value<Version> second = Value.versionOf("1.0.0+b", VersionDialect.SEMVER);

    assertThat(first).isNotEqualTo(second);
    assertThat(first.unbox()).isNotEqualTo(second.unbox());
    assertThat(Versions.comparePrecedence(first, second)).hasIntValue(0);
  }

  @Test
  @JourneyTest(speakingId = "version-value-with-dialects", acceptance = "equal")
  @DisplayName("two parses of the same text under the same dialect are equal and hash alike")
  void sameTextSameDialectIsEqual() {
    Value<Version> first = Value.versionOf("1.4.0-rc.1+build.7", VersionDialect.SEMVER);
    Value<Version> second = Value.versionOf("1.4.0-rc.1+build.7", VersionDialect.SEMVER);
    Value<Version> jdkFirst = Value.versionOf("25+7", VersionDialect.JDK);
    Value<Version> jdkSecond = Value.versionOf("25+7", VersionDialect.JDK);

    assertThat(first).isEqualTo(second);
    assertThat(first).hasSameHashCodeAs(second);
    assertThat(jdkFirst).isEqualTo(jdkSecond);
    assertThat(jdkFirst).hasSameHashCodeAs(jdkSecond);
  }

  @Test
  @JourneyTest(speakingId = "version-value-with-dialects", acceptance = "atleast")
  @DisplayName("1.4.0 satisfies at least 1.0.0 and 1.0.0-rc.1 does not")
  void atLeastFollowsSemverPrecedence() {
    Value<Version> need = Value.versionOf("1.0.0", VersionDialect.SEMVER);

    assertThat(Versions.atLeast(Value.versionOf("1.4.0", VersionDialect.SEMVER), need)).hasBooleanValue(true);
    assertThat(Versions.atLeast(Value.versionOf("1.0.0-rc.1", VersionDialect.SEMVER), need)).hasBooleanValue(false);
    assertThat(Versions.atLeast(Value.versionOf("1.0.0", VersionDialect.SEMVER), need)).hasBooleanValue(true);
    assertThat(Versions.atLeast(Value.versionOf("1.0.0+build.9", VersionDialect.SEMVER), need))
      .as("build metadata does not count")
      .hasBooleanValue(true);
    assertThat(Versions.atLeast(Value.versionOf("1.4.0-rc.1+build.7", VersionDialect.SEMVER), need)).hasBooleanValue(
      true
    );
  }

  @Test
  @JourneyTest(speakingId = "version-value-with-dialects", acceptance = "atleast")
  @DisplayName("at-least holds for the JDK dialects by their own precedence")
  void atLeastFollowsJdkDialectPrecedence() {
    assertThat(
      Versions.atLeast(Value.versionOf("25.0.1", VersionDialect.JDK), Value.versionOf("25", VersionDialect.JDK))
    ).hasBooleanValue(true);
    assertThat(
      Versions.atLeast(Value.versionOf("25-ea", VersionDialect.JDK), Value.versionOf("25", VersionDialect.JDK))
    ).hasBooleanValue(false);
    assertThat(
      Versions.atLeast(Value.versionOf("1.0", VersionDialect.MODULE), Value.versionOf("1.0.0", VersionDialect.MODULE))
    ).hasBooleanValue(true);
  }

  @Test
  @JourneyTest(speakingId = "version-value-with-dialects", acceptance = "cross")
  @DisplayName("comparing versions of different dialects is refused as an UnsupportedValue")
  void crossDialectComparisonIsRefused() {
    Value<Version> semver = Value.versionOf("1.0.0", VersionDialect.SEMVER);
    Value<Version> module = Value.versionOf("1.0", VersionDialect.MODULE);

    assertThat(Versions.comparePrecedence(semver, module))
      .isUnsupported()
      .hasUnsupportedReason("Cannot compare a SemVer version with a JPMS module version: dialects differ")
      .hasExpectedTypeName("java.lang.Integer");
    assertThat(Versions.atLeast(semver, module))
      .isUnsupported()
      .hasUnsupportedReason("Cannot compare a SemVer version with a JPMS module version: dialects differ")
      .hasExpectedTypeName("java.lang.Boolean");
  }

  @Test
  @JourneyTest(speakingId = "version-value-with-dialects", acceptance = "cross")
  @DisplayName("identical text in two dialects is still refused, never ordered by text")
  void identicalTextInTwoDialectsIsStillRefused() {
    Value<Version> jdk = Value.versionOf("25.0.1", VersionDialect.JDK);
    Value<Version> semver = Value.versionOf("25.0.1", VersionDialect.SEMVER);

    assertThat(Versions.comparePrecedence(jdk, semver))
      .isUnsupported()
      .hasUnsupportedReason("Cannot compare a JDK version with a SemVer version: dialects differ");
    assertThat(Versions.atLeast(jdk, semver))
      .isUnsupported()
      .hasUnsupportedReason("Cannot compare a JDK version with a SemVer version: dialects differ");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("roundTripCases")
  @JourneyTest(speakingId = "version-value-with-dialects", acceptance = "roundtrip")
  @DisplayName("a version round-trips through its dialect descriptor with its dialect and its text as written")
  void versionRoundTripsThroughItsDialectDescriptor(RoundTripCase roundTrip) {
    VersionCase version = roundTrip.version();
    Value<Version> original = Value.versionOf(version.text(), version.dialect());
    TypeDescriptor<Version> descriptor = version.dialectDescriptor();

    Value<Version> reread = roundTrip.format().read(roundTrip.format().write(original, descriptor), descriptor);

    assertThat(reread).isEqualTo(original);
    assertThat(reread.unbox().dialect()).isEqualTo(version.dialect());
    assertThat(reread.unbox().text()).isEqualTo(version.text());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("roundTripCases")
  @JourneyTest(speakingId = "version-value-with-dialects", acceptance = "roundtrip")
  @DisplayName("a version round-trips through the dialect-less VERSION descriptor with its dialect and text")
  void versionRoundTripsThroughTheDialectLessDescriptor(RoundTripCase roundTrip) {
    VersionCase version = roundTrip.version();
    Value<Version> original = Value.versionOf(version.text(), version.dialect());

    Value<Version> reread = roundTrip
      .format()
      .read(roundTrip.format().write(original, BuiltinTypeDescriptors.VERSION), BuiltinTypeDescriptors.VERSION);

    assertThat(reread).isEqualTo(original);
    assertThat(reread.unbox().dialect()).isEqualTo(version.dialect());
    assertThat(reread.unbox().text()).isEqualTo(version.text());
  }

  @Test
  @JourneyTest(speakingId = "version-value-with-dialects", acceptance = "roundtrip")
  @DisplayName("the dialect-qualified string names the dialect and parses back to an equal value")
  void theQualifiedStringParsesBackToAnEqualValue() {
    for (VersionCase version : roundTripCases().map(RoundTripCase::version).distinct().toList()) {
      Value<Version> original = Value.versionOf(version.text(), version.dialect());

      String qualified = original.unbox().toString();
      Value<Version> reparsed = Value.qualifiedVersionOf(qualified);

      assertThat(qualified).isEqualTo(version.qualified());
      assertThat(reparsed).as(qualified).isEqualTo(original);
      assertThat(reparsed.unbox().dialect()).as(qualified).isEqualTo(version.dialect());
      assertThat(reparsed.unbox().text()).as(qualified).isEqualTo(version.text());
    }
  }

  @Test
  @JourneyTest(speakingId = "version-value-with-dialects", acceptance = "roundtrip")
  @DisplayName("the canonical string form is the text as written; the qualified form is toString()")
  void theCanonicalStringFormIsTheBareText() {
    Value<Version> semver = Value.versionOf("0.3.0", VersionDialect.SEMVER);

    assertThat(semver.convertTo(String.class)).hasStringValue("0.3.0");
    assertThat(semver.unbox().toString()).isEqualTo("semver:0.3.0");
  }

  @Test
  @JourneyTest(speakingId = "version-value-with-dialects", acceptance = "symbol")
  @DisplayName("fromSymbol parses a version under each dialect symbol and under the qualified version symbol")
  void fromSymbolParsesEachDialect() {
    assertThat(Value.fromSymbol("semver", "0.3.0")).isEqualTo(Value.versionOf("0.3.0", VersionDialect.SEMVER));
    assertThat(Value.fromSymbol("jdk", "25.0.4.1+1")).isEqualTo(Value.versionOf("25.0.4.1+1", VersionDialect.JDK));
    assertThat(Value.fromSymbol("jpms", "1.0")).isEqualTo(Value.versionOf("1.0", VersionDialect.MODULE));
    assertThat(Value.fromSymbol("version", "semver:0.3.0")).isEqualTo(Value.versionOf("0.3.0", VersionDialect.SEMVER));
    assertThat(Value.fromSymbol("version", "jpms:1.0")).isEqualTo(Value.versionOf("1.0", VersionDialect.MODULE));
    assertThat(Value.fromSymbol("semver", "1.0")).hasErrorMessage(
      "Invalid SemVer version '1.0': expected MAJOR.MINOR.PATCH"
    );
  }

  @Test
  @JourneyTest(speakingId = "version-value-with-dialects", acceptance = "symbol")
  @DisplayName("each version's value type maps back to its dialect's symbol")
  void eachValueTypeMapsBackToItsDialectSymbol() {
    assertThat(BuiltinTypeDescriptors.symbolFor(Value.versionOf("0.3.0", VersionDialect.SEMVER).valueType())).hasValue(
      "semver"
    );
    assertThat(
      BuiltinTypeDescriptors.symbolFor(Value.versionOf("25.0.4.1+1", VersionDialect.JDK).valueType())
    ).hasValue("jdk");
    assertThat(BuiltinTypeDescriptors.symbolFor(Value.versionOf("1.0", VersionDialect.MODULE).valueType())).hasValue(
      "jpms"
    );
  }

  record VersionCase(VersionDialect dialect, String text, String qualified) {
    TypeDescriptor<Version> dialectDescriptor() {
      return switch (dialect) {
        case SEMVER -> BuiltinTypeDescriptors.VERSION_SEMVER;
        case JDK -> BuiltinTypeDescriptors.VERSION_JDK;
        case MODULE -> BuiltinTypeDescriptors.VERSION_MODULE;
      };
    }
  }

  record RoundTripCase(RoundTripFormatHarness.Format format, VersionCase version) {
    @Override
    public String toString() {
      return format.name() + " " + version.qualified();
    }
  }
}
