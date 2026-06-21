package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.ContractTest;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Value scalar accessors (string / boolean / url)")
@ContractTest
class ValueScalarAccessorTest {

  @Nested
  @DisplayName("isString")
  class IsString {

    @Test
    @DisplayName("returns true for NonNullValue wrapping String")
    void trueForString() {
      assertThat(Value.of("hello").isString()).isTrue();
    }

    @Test
    @DisplayName("returns false for NonNullValue wrapping Number")
    void falseForNumber() {
      assertThat(Value.of(42).isString()).isFalse();
    }

    @Test
    @DisplayName("returns false for UrlValue")
    void falseForUrl() {
      assertThat(Value.urlOf("https://example.com").isString()).isFalse();
    }

    @Test
    @DisplayName("returns false for state values")
    void falseForStateValues() {
      assertThat(Value.nullValue().isString()).isFalse();
      assertThat(Value.undefined().isString()).isFalse();
    }
  }

  @Nested
  @DisplayName("isBoolean")
  class IsBoolean {

    @Test
    @DisplayName("returns true for NonNullValue wrapping Boolean")
    void trueForBoolean() {
      assertThat(Value.of(true).isBoolean()).isTrue();
    }

    @Test
    @DisplayName("returns false for NonNullValue wrapping String")
    void falseForString() {
      assertThat(Value.of("true").isBoolean()).isFalse();
    }

    @Test
    @DisplayName("returns false for state values")
    void falseForStateValues() {
      assertThat(Value.undefined().isBoolean()).isFalse();
    }
  }

  @Nested
  @DisplayName("isUrl")
  class IsUrl {

    @Test
    @DisplayName("returns true for UrlValue")
    void trueForUrlValue() {
      assertThat(Value.urlOf("https://example.com").isUrl()).isTrue();
    }

    @Test
    @DisplayName("returns false for NonNullValue wrapping String")
    void falseForString() {
      assertThat(Value.of("https://example.com").isUrl()).isFalse();
    }

    @Test
    @DisplayName("returns false for state values")
    void falseForStateValues() {
      assertThat(Value.nullValue().isUrl()).isFalse();
    }
  }

  @Nested
  @DisplayName("asString")
  class AsString {

    @Test
    @DisplayName("returns String directly")
    void directString() {
      assertThat(Value.of("hello").asString()).contains("hello");
    }

    @Test
    @DisplayName("converts Number to String")
    void fromNumber() {
      assertThat(Value.of(42).asString()).contains("42");
    }

    @Test
    @DisplayName("converts Boolean to String")
    void fromBoolean() {
      assertThat(Value.of(true).asString()).contains("true");
    }

    @Test
    @DisplayName("converts UrlValue to String")
    void fromUrl() {
      assertThat(Value.urlOf("https://example.com").asString()).contains("https://example.com");
    }

    @Test
    @DisplayName("returns empty for state values")
    void emptyForStateValues() {
      assertThat(Value.nullValue().asString()).isEmpty();
      assertThat(Value.undefined().asString()).isEmpty();
    }

    @Test
    @DisplayName("returns empty for list value")
    void emptyForList() {
      assertThat(Value.emptyList().asString()).isEmpty();
    }

    @Test
    @DisplayName("returns empty for set value")
    void emptyForSet() {
      assertThat(Value.emptySet().asString()).isEmpty();
    }

    @Test
    @DisplayName("returns empty for map value")
    void emptyForMap() {
      assertThat(Value.emptyMap().asString()).isEmpty();
    }

    @Test
    @DisplayName("returns empty for sorted-map value")
    void emptyForSortedMap() {
      assertThat(Value.emptySortedMap().asString()).isEmpty();
    }
  }

  @Nested
  @DisplayName("asString — canonical forms across all BuiltInValue scalars")
  class AsStringCanonical {

    static Stream<AsStringCase> canonicalForms() {
      return Stream.of(
        new AsStringCase("String", Value.of("hello"), "hello"),
        new AsStringCase("Boolean true", Value.of(true), "true"),
        new AsStringCase("Boolean false", Value.of(false), "false"),
        new AsStringCase("Character", Value.of('x'), "x"),
        new AsStringCase("Integer", Value.of(42), "42"),
        // Long: > Integer.MAX_VALUE so an accidental int-cast in unbox would lose data.
        new AsStringCase("Long", Value.of(10_000_000_000L), "10000000000"),
        // Double: Math.PI has 16 significant digits — more precision than a float can carry.
        new AsStringCase("Double", Value.of(Math.PI), Double.toString(Math.PI)),
        // Float: a magnitude beyond what an int/short/byte can hold.
        new AsStringCase("Float", Value.of(1.0e20f), Float.toString(1.0e20f)),
        // Short: > Byte.MAX_VALUE so an accidental byte-cast would lose data.
        new AsStringCase("Short", Value.of((short) 30_000), "30000"),
        new AsStringCase("Byte", Value.of((byte) 9), "9"),
        // BigInteger: > Long.MAX_VALUE so an accidental long-cast would lose data.
        new AsStringCase("BigInteger", Value.of(new BigInteger("99999999999999999999")), "99999999999999999999"),
        // BigDecimal: precision beyond what a double can carry.
        new AsStringCase(
          "BigDecimal",
          Value.of(new BigDecimal("3.141592653589793238462643383279502884")),
          "3.141592653589793238462643383279502884"
        ),
        new AsStringCase(
          "UUID",
          Value.of(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
          "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        ),
        new AsStringCase("URI", Value.of(URI.create("https://x.com")), "https://x.com"),
        new AsStringCase("Instant", Value.of(Instant.parse("2026-01-01T00:00:00Z")), "2026-01-01T00:00:00Z"),
        new AsStringCase("LocalDate", Value.of(LocalDate.of(2026, 5, 9)), "2026-05-09"),
        new AsStringCase("LocalDateTime", Value.of(LocalDateTime.of(2026, 5, 9, 10, 15)), "2026-05-09T10:15"),
        new AsStringCase(
          "ZonedDateTime",
          Value.of(ZonedDateTime.parse("2026-05-09T10:15:30+02:00[Europe/Zurich]")),
          "2026-05-09T10:15:30+02:00[Europe/Zurich]"
        ),
        new AsStringCase(
          "OffsetDateTime",
          Value.of(OffsetDateTime.parse("2026-05-09T10:15:30+02:00")),
          "2026-05-09T10:15:30+02:00"
        ),
        new AsStringCase("LocalTime", Value.of(LocalTime.of(10, 15)), "10:15"),
        new AsStringCase("Year", Value.of(Year.of(2026)), "2026"),
        new AsStringCase("YearMonth", Value.of(YearMonth.of(2026, 5)), "2026-05"),
        new AsStringCase("ZoneId", Value.of(ZoneId.of("Europe/Zurich")), "Europe/Zurich"),
        new AsStringCase("Duration", Value.of(Duration.ofSeconds(60)), "PT1M"),
        new AsStringCase("Period", Value.of(Period.of(1, 2, 3)), "P1Y2M3D"),
        // Locale uses BCP 47 (toLanguageTag), not toString — pin explicitly.
        new AsStringCase("Locale", Value.of(Locale.forLanguageTag("en-US")), "en-US"),
        // Currency uses ISO 4217 (getCurrencyCode), not toString — pin explicitly.
        new AsStringCase("Currency", Value.of(Currency.getInstance("USD")), "USD"),
        // InetAddress uses getHostAddress, not toString (which prepends `/`) — pin explicitly.
        new AsStringCase("InetAddress", Value.of(InetAddress.ofLiteral("192.168.1.1")), "192.168.1.1")
      );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("canonicalForms")
    @DisplayName("scalar canonical form")
    void canonicalForm(AsStringCase c) {
      assertThat(c.input().asString()).contains(c.expected());
    }

    record AsStringCase(String name, Value<?> input, String expected) {
      @Override
      public String toString() {
        return name;
      }
    }
  }

  @Nested
  @DisplayName("asBoolean")
  class AsBoolean {

    @Test
    @DisplayName("returns Boolean directly")
    void directBoolean() {
      assertThat(Value.of(true).asBoolean()).contains(true);
      assertThat(Value.of(false).asBoolean()).contains(false);
    }

    @Test
    @DisplayName("parses String true case-insensitively")
    void fromStringTrue() {
      assertThat(Value.of("true").asBoolean()).contains(true);
      assertThat(Value.of("TRUE").asBoolean()).contains(true);
      assertThat(Value.of("True").asBoolean()).contains(true);
    }

    @Test
    @DisplayName("parses String false case-insensitively")
    void fromStringFalse() {
      assertThat(Value.of("false").asBoolean()).contains(false);
      assertThat(Value.of("FALSE").asBoolean()).contains(false);
    }

    @Test
    @DisplayName("returns empty for non-boolean String")
    void emptyForNonBooleanString() {
      assertThat(Value.of("yes").asBoolean()).isEmpty();
      assertThat(Value.of("1").asBoolean()).isEmpty();
    }

    @Test
    @DisplayName("returns empty for state values")
    void emptyForStateValues() {
      assertThat(Value.nullValue().asBoolean()).isEmpty();
    }

    @Test
    @DisplayName("returns empty for Number")
    void emptyForNumber() {
      assertThat(Value.of(1).asBoolean()).isEmpty();
    }
  }
}
