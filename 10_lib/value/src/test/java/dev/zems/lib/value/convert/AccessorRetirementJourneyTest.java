package dev.zems.lib.value.convert;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;

import dev.zems.lib.common.junit.JourneyTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.VersionDialect;
import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
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
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Cross-type accessor retirement — canonical rendering and the factory parse dialect")
class AccessorRetirementJourneyTest {

  static Stream<RenderCase> renderCases() {
    return Stream.of(
      new RenderCase("boolean", Value.of(Boolean.TRUE), "true"),
      new RenderCase("character", Value.of('x'), "x"),
      new RenderCase("integer", Value.of(42), "42"),
      new RenderCase("long", Value.of(3_000_000_000L), "3000000000"),
      new RenderCase("short", Value.of((short) 1024), "1024"),
      new RenderCase("byte", Value.of((byte) 7), "7"),
      new RenderCase(
        "big integer",
        Value.bigIntegerOf("170141183460469231731687303715884105727"),
        "170141183460469231731687303715884105727"
      ),
      new RenderCase("double", Value.of(42.0d), "42.0"),
      new RenderCase("float", Value.of(0.25f), "0.25"),
      new RenderCase("big decimal keeps its exponent form", Value.bigDecimalOf("1E+3"), "1E+3"),
      new RenderCase(
        "uuid",
        Value.uuidOf("3f2504e0-4f89-11d3-9a0c-0305e82c3301"),
        "3f2504e0-4f89-11d3-9a0c-0305e82c3301"
      ),
      new RenderCase("uri", Value.of(URI.create("https://zems.dev/values")), "https://zems.dev/values"),
      new RenderCase("instant", Value.of(Instant.parse("2026-06-04T10:00:00Z")), "2026-06-04T10:00:00Z"),
      new RenderCase("local date", Value.of(LocalDate.parse("2026-08-19")), "2026-08-19"),
      new RenderCase("local date time", Value.of(LocalDateTime.parse("2031-04-01T08:30:15")), "2031-04-01T08:30:15"),
      new RenderCase(
        "zoned date time",
        Value.of(ZonedDateTime.parse("2031-04-01T08:30:15+02:00[Europe/Zurich]")),
        "2031-04-01T08:30:15+02:00[Europe/Zurich]"
      ),
      new RenderCase(
        "offset date time",
        Value.of(OffsetDateTime.parse("2031-04-01T08:30:15+02:00")),
        "2031-04-01T08:30:15+02:00"
      ),
      new RenderCase("local time", Value.of(LocalTime.parse("08:30:15")), "08:30:15"),
      new RenderCase("year", Value.of(Year.of(2031)), "2031"),
      new RenderCase("year month", Value.of(YearMonth.parse("2031-04")), "2031-04"),
      new RenderCase("zone id", Value.of(ZoneId.of("Europe/Zurich")), "Europe/Zurich"),
      new RenderCase("duration", Value.of(Duration.parse("PT8H30M")), "PT8H30M"),
      new RenderCase("period", Value.of(Period.parse("P1Y2M3D")), "P1Y2M3D"),
      new RenderCase("locale as a BCP 47 tag", Value.of(Locale.forLanguageTag("de-CH")), "de-CH"),
      new RenderCase("currency", Value.of(Currency.getInstance("CHF")), "CHF"),
      new RenderCase(
        "inet address v4 as the host address",
        Value.of(InetAddress.ofLiteral("192.168.1.1")),
        "192.168.1.1"
      ),
      new RenderCase("type name", Value.typeOf("com.example.Foo"), "com.example.Foo"),
      new RenderCase("version", Value.versionOf("1.4.0", VersionDialect.SEMVER), "1.4.0")
    );
  }

  static Stream<DialectCase> dialectCases() {
    return Stream.of(
      new DialectCase("whole-valued decimal answers an integral ask", "42.0", Integer.class, Value.of(42)),
      new DialectCase("whole-valued decimal answers a long ask", "42.0", Long.class, Value.of(42L)),
      new DialectCase(
        "a fractional decimal refuses an integral ask, with the reason",
        "42.5",
        Integer.class,
        Value.errorMessage(
          "Cannot convert String to Integer: 42.5 is not an integer (use !double or !bigdec)",
          Integer.class
        )
      ),
      new DialectCase(
        "NaN is not a number the library parses",
        "NaN",
        Double.class,
        Value.errorMessage("Cannot convert String to Double: Not a number: NaN", Double.class)
      ),
      new DialectCase(
        "Infinity is not a number the library parses",
        "Infinity",
        Double.class,
        Value.errorMessage("Cannot convert String to Double: Not a number: Infinity", Double.class)
      ),
      new DialectCase(
        "a hexadecimal float literal is not a number the library parses",
        "0x1p3",
        Double.class,
        Value.errorMessage("Cannot convert String to Double: Not a number: 0x1p3", Double.class)
      ),
      new DialectCase("a boolean literal is read case-insensitively", "TRUE", Boolean.class, Value.of(true)),
      new DialectCase(
        "a truthy word that is not a boolean literal refuses",
        "yes",
        Boolean.class,
        Value.errorMessage("Cannot convert String to Boolean: Not a boolean: yes", Boolean.class)
      )
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("renderCases")
  @JourneyTest(speakingId = "accessor-silent-narrowing", acceptance = "render")
  @DisplayName("each canonical-form builtin renders its canonical string through the engine")
  void canonicalFormBuiltinsRenderThroughTheEngine(RenderCase renderCase) {
    assertThat(renderCase.source().convertTo(String.class)).hasStringValue(renderCase.expected());
  }

  @Test
  @JourneyTest(speakingId = "accessor-silent-narrowing", acceptance = "render")
  @DisplayName("an IPv6 address renders as the uncompressed host address, not as InetAddress.toString()")
  void inetAddressRendersTheHostAddressRatherThanItsToString() {
    Value<InetAddress> address = Value.of(InetAddress.ofLiteral("2001:db8::1"));

    assertThat(address.convertTo(String.class)).hasStringValue("2001:db8:0:0:0:0:0:1");
  }

  @Test
  @JourneyTest(speakingId = "accessor-silent-narrowing", acceptance = "render")
  @DisplayName("a string renders as itself — the engine answers it by identity, with no rendering step")
  void aStringRendersAsItself() {
    assertThat(Value.of("hello").convertTo(String.class)).hasStringValue("hello");
  }

  @Test
  @JourneyTest(speakingId = "accessor-silent-narrowing", acceptance = "render")
  @DisplayName("the render table leaves no canonical-form builtin unpinned")
  void everyCanonicalFormBuiltinIsPinned() {
    List<String> builtins = BuiltinTypeDescriptors.all().stream().map(TypeDescriptor::qualifiedName).sorted().toList();

    List<String> pinned = Stream.concat(
      Stream.of(String.class.getName()),
      renderCases().map(renderCase -> renderCase.source().unbox().getClass().getName())
    )
      .map(AccessorRetirementJourneyTest::declaredName)
      .distinct()
      .sorted()
      .toList();

    assertThat(pinned).isEqualTo(builtins);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("dialectCases")
  @JourneyTest(speakingId = "accessor-silent-narrowing", acceptance = "dialect")
  @DisplayName("a string-held value reads cross-type in the factory dialect, and the java.lang extras refuse")
  void stringReadsUseTheFactoryDialect(DialectCase dialectCase) {
    Value<String> source = Value.of(dialectCase.text());

    assertThat(source.convertTo(dialectCase.target())).isEqualTo(dialectCase.expected());
  }

  @Test
  @JourneyTest(speakingId = "accessor-silent-narrowing", acceptance = "dialect")
  @DisplayName("a cross-type read that refuses says so as an error, not as an absent value")
  void aRefusedReadIsAnErrorRatherThanAnAbsence() {
    Value<String> word = Value.of("hello");

    assertThat(word.convertTo(Integer.class))
      .isError()
      .hasErrorMessage("Cannot convert String to Integer: Not a number: hello");
    assertThat(Value.undefined().convertTo(Integer.class)).isUndefined();
  }

  private static String declaredName(String concreteName) {
    return switch (concreteName) {
      case "java.net.Inet4Address", "java.net.Inet6Address" -> InetAddress.class.getName();
      case "java.time.ZoneRegion" -> ZoneId.class.getName();
      default -> concreteName;
    };
  }

  record RenderCase(String label, Value<?> source, String expected) {
    @Override
    public String toString() {
      return label;
    }
  }

  record DialectCase(String label, String text, Class<?> target, Value<?> expected) {
    @Override
    public String toString() {
      return label;
    }
  }
}
