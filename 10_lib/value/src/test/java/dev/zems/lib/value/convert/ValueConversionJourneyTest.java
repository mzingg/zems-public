package dev.zems.lib.value.convert;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.zems.lib.common.junit.JourneyTest;
import dev.zems.lib.value.TypeName;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.Version;
import dev.zems.lib.value.fixture.SlotBag;
import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
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
import java.time.format.DateTimeParseException;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Value conversion — one verb, stated policies, refusals as data")
class ValueConversionJourneyTest {

  private static final String PERSON_NAME = "dev.zems.lib.value.convert.ValueConversionJourneyTest$Person";

  static Stream<ScalarCase> scalarCases() {
    return Stream.of(
      new ScalarCase("string", String.class, "hello"),
      new ScalarCase("bool", Boolean.class, "true"),
      new ScalarCase("char", Character.class, "x"),
      new ScalarCase("int", Integer.class, "42"),
      new ScalarCase("long", Long.class, "3000000000"),
      new ScalarCase("short", Short.class, "1024"),
      new ScalarCase("byte", Byte.class, "7"),
      new ScalarCase("bigint", BigInteger.class, "170141183460469231731687303715884105727"),
      new ScalarCase("double", Double.class, "0.5"),
      new ScalarCase("float", Float.class, "0.25"),
      new ScalarCase("bigdec", BigDecimal.class, "0.1234567890123456789"),
      new ScalarCase("uuid", UUID.class, "3f2504e0-4f89-11d3-9a0c-0305e82c3301"),
      new ScalarCase("url", URI.class, "https://zems.dev/values"),
      new ScalarCase("instant", Instant.class, "2026-06-04T10:00:00Z"),
      new ScalarCase("date", LocalDate.class, "2031-04-01"),
      new ScalarCase("datetime", LocalDateTime.class, "2031-04-01T08:30:00"),
      new ScalarCase("zdatetime", ZonedDateTime.class, "2031-04-01T08:30:00+02:00[Europe/Zurich]"),
      new ScalarCase("odatetime", OffsetDateTime.class, "2031-04-01T08:30:00+02:00"),
      new ScalarCase("time", LocalTime.class, "08:30:00"),
      new ScalarCase("year", Year.class, "2031"),
      new ScalarCase("yearmonth", YearMonth.class, "2031-04"),
      new ScalarCase("zone", ZoneId.class, "Europe/Zurich"),
      new ScalarCase("duration", Duration.class, "PT8H30M"),
      new ScalarCase("period", Period.class, "P1Y2M3D"),
      new ScalarCase("locale", Locale.class, "de-CH"),
      new ScalarCase("currency", Currency.class, "CHF"),
      new ScalarCase("ip", InetAddress.class, "192.168.1.1"),
      new ScalarCase("type", TypeName.class, "com.example.Person")
    );
  }

  static Stream<SurfaceCase> surfaceCases() {
    return Stream.of(
      new SurfaceCase("string to date", Value.of("2031-04-01"), LocalDate.class, Value.of(LocalDate.of(2031, 4, 1))),
      new SurfaceCase("int to long", Value.of(42), Long.class, Value.of(42L)),
      new SurfaceCase(
        "string to uuid",
        Value.of("3f2504e0-4f89-11d3-9a0c-0305e82c3301"),
        UUID.class,
        Value.uuidOf("3f2504e0-4f89-11d3-9a0c-0305e82c3301")
      ),
      new SurfaceCase("double to string", Value.of(2.5d), String.class, Value.of("2.5")),
      new SurfaceCase(
        "lossy double to int",
        Value.of(3.7d),
        Integer.class,
        Value.errorMessage("Cannot convert Double to Integer: 3.7 is not a whole number", Integer.class)
      ),
      new SurfaceCase(
        "overflowing long to int",
        Value.of(5_000_000_000L),
        Integer.class,
        Value.errorMessage("Cannot convert Long to Integer: 5000000000 overflows Integer", Integer.class)
      ),
      new SurfaceCase(
        "unparsable string to date",
        Value.of("not-a-date"),
        LocalDate.class,
        Value.errorMessage(
          "Cannot convert String to LocalDate: Text 'not-a-date' could not be parsed at index 0",
          LocalDate.class
        )
      ),
      new SurfaceCase(
        "no conversion for the pair",
        Value.of(LocalDate.of(2031, 4, 1)),
        Integer.class,
        Value.unsupported("Cannot convert LocalDate to Integer: no conversion exists", Integer.class)
      )
    );
  }

  @Test
  @JourneyTest(speakingId = "value-conversion-api", acceptance = "uniform")
  @DisplayName("one verb takes a value and a target class — string to builtin, number to number, and a record alike")
  void oneVerbCoversEveryTargetKind() {
    Converter converter = Converter.standard();

    Value<LocalDate> parsed = converter.convert(Value.of("2031-04-01"), LocalDate.class);
    Value<Long> widened = converter.convert(Value.of(42), Long.class);
    Value<Person> structural = converter.convert(Value.of("2031-04-01"), Person.class);

    assertThat(parsed).isEqualTo(Value.of(LocalDate.of(2031, 4, 1)));
    assertThat(widened).isEqualTo(Value.of(42L));

    assertThatCode(() -> converter.convert(Value.of("2031-04-01"), Person.class)).doesNotThrowAnyException();
    assertThat(structural).isUnsupported().hasExpectedTypeName(PERSON_NAME);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("scalarCases")
  @JourneyTest(speakingId = "value-conversion-api", acceptance = "scalar")
  @DisplayName("a string converts to every canonical-form builtin, equal to what that type's own factory produces")
  void stringConvertsToEachBuiltinExactlyAsItsFactoryDoes(ScalarCase scalarCase) {
    var converted = Converter.standard().convert(Value.of(scalarCase.canonicalText()), scalarCase.targetType());
    var fromFactory = Value.fromSymbol(scalarCase.symbol(), scalarCase.canonicalText());

    assertThat(converted).isPresent().isEqualTo(fromFactory);
  }

  @Test
  @JourneyTest(speakingId = "value-conversion-api", acceptance = "scalar")
  @DisplayName("every builtin with a canonical string form has a String conversion — none is left out")
  void everyCanonicalFormBuiltinHasAStringConversion() {
    Converter converter = Converter.standard();
    List<String> canonicalFormTypes = BuiltinTypeDescriptors.all()
      .stream()
      .map(TypeDescriptor::qualifiedName)
      .filter(name -> !name.equals(Version.class.getName()))
      .sorted()
      .toList();

    List<String> convertible = scalarCases()
      .filter(scalarCase ->
        converter.convert(Value.of(scalarCase.canonicalText()), scalarCase.targetType()).isPresent()
      )
      .map(scalarCase -> scalarCase.targetType().getName())
      .sorted()
      .toList();

    assertThat(convertible).isEqualTo(canonicalFormTypes);
    assertThat(convertible)
      .as("the 35 builtins minus the four collection wrappers, EnumValue, VersionValue and MeasurableValue")
      .hasSize(28);
  }

  @Test
  @JourneyTest(speakingId = "version-value-with-dialects", acceptance = "roundtrip")
  @DisplayName("bare text names no dialect, so a String never converts to a Version")
  void stringNeverConvertsToAVersion() {
    var converted = Value.of("1.0.0").convertTo(Version.class);

    assertThat(converted)
      .isUnsupported()
      .hasUnsupportedReason("Cannot convert String to Version: no conversion exists");
  }

  @Test
  @JourneyTest(speakingId = "value-conversion-api", acceptance = "policy")
  @DisplayName("lossy and unit-ambiguous requests are refused until a conversion states the policy")
  void lossyAndUnitAmbiguousConversionsNeedAStatedPolicy() {
    Converter standard = Converter.standard();

    assertThat(standard.convert(Value.of(3.7d), Integer.class)).hasErrorMessage(
      "Cannot convert Double to Integer: 3.7 is not a whole number"
    );

    Converter rounding = standard.with(Conversions.doubleToInt(Rounding.DOWN));
    assertThat(rounding.convert(Value.of(3.7d), Integer.class)).isEqualTo(Value.of(3));

    assertThat(standard.convert(Value.of(42L), Integer.class)).isEqualTo(Value.of(42));
    assertThat(standard.convert(Value.of(5_000_000_000L), Integer.class)).hasErrorMessage(
      "Cannot convert Long to Integer: 5000000000 overflows Integer"
    );

    assertThat(standard.convert(Value.of(1_700_000_000L), Instant.class))
      .isUnsupported()
      .hasUnsupportedReason("Cannot convert Long to Instant: no conversion exists");
    Converter epochs = standard.with(Conversions.epochToInstant(EpochUnit.SECONDS));
    assertThat(epochs.convert(Value.of(1_700_000_000L), Instant.class)).isEqualTo(
      Value.of(Instant.parse("2023-11-14T22:13:20Z"))
    );

    assertThat(standard.convert(Value.of(3.7d), Integer.class)).hasErrorMessage(
      "Cannot convert Double to Integer: 3.7 is not a whole number"
    );
    assertThat(standard.convert(Value.of(1_700_000_000L), Instant.class))
      .isUnsupported()
      .hasUnsupportedReason("Cannot convert Long to Instant: no conversion exists");
  }

  @Test
  @JourneyTest(speakingId = "value-conversion-api", acceptance = "contract")
  @DisplayName("a custom conversion rides the shipped contract: same registration, same call site, same dispatch")
  void customConversionsAreIndistinguishableFromShippedOnes() {
    Conversion<Double, Celsius> fromKelvin = Conversion.of(Double.class, Celsius.class, kelvin ->
      Value.of(new Celsius(kelvin - 273.15d))
    );

    assertThat(fromKelvin.sourceType()).isEqualTo(Double.class);
    assertThat(fromKelvin.targetType()).isEqualTo(Celsius.class);

    Converter standard = Converter.standard();
    Converter withCelsius = standard.with(fromKelvin);

    Value<Celsius> custom = withCelsius.convert(Value.of(300.0d), Celsius.class);
    Value<Long> shipped = withCelsius.convert(Value.of(42), Long.class);

    assertThat(custom).isEqualTo(Value.of(new Celsius(300.0d - 273.15d)));
    assertThat(shipped).isEqualTo(Value.of(42L));

    assertThat(standard.convert(Value.of(300.0d), Celsius.class))
      .isUnsupported()
      .hasUnsupportedReason("Cannot convert Double to Celsius: no conversion exists");

    assertThat(withCelsius.convert(Value.of("300.0"), Celsius.class))
      .isUnsupported()
      .hasUnsupportedReason("Cannot convert String to Celsius: no conversion exists");
  }

  @Test
  @JourneyTest(speakingId = "value-conversion-api", acceptance = "error")
  @DisplayName("a refusal names the source type, the target type and the reason, and carries the target as data")
  void refusalsNameSourceTargetAndReason() {
    Converter converter = Converter.standard();

    assertThatCode(() -> converter.convert(Value.of("not-a-date"), LocalDate.class))
      .as("a data problem never escapes as an exception")
      .doesNotThrowAnyException();

    assertThat(converter.convert(Value.of(LocalDate.of(2031, 4, 1)), Integer.class))
      .isUnsupported()
      .hasUnsupportedReason("Cannot convert LocalDate to Integer: no conversion exists")
      .hasExpectedTypeName("java.lang.Integer");

    assertThat(converter.convert(Value.of(3.7d), Integer.class))
      .hasErrorMessage("Cannot convert Double to Integer: 3.7 is not a whole number")
      .hasExpectedTypeName("java.lang.Integer");

    Value<LocalDate> parseFailure = converter.convert(Value.of("not-a-date"), LocalDate.class);
    assertThat(parseFailure)
      .hasErrorMessage("Cannot convert String to LocalDate: Text 'not-a-date' could not be parsed at index 0")
      .hasExpectedTypeName("java.time.LocalDate");
    assertThat(parseFailure)
      .extractingThrowable()
      .cause()
      .isInstanceOf(DateTimeParseException.class)
      .hasMessage("Text 'not-a-date' could not be parsed at index 0");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("surfaceCases")
  @JourneyTest(speakingId = "value-conversion-api", acceptance = "surface")
  @DisplayName("convertTo on the value needs no converter in hand and answers exactly what the verb answers")
  void convertToOnTheValueSurfaceMatchesTheVerb(SurfaceCase surfaceCase) {
    var throughSugar = surfaceCase.source().convertTo(surfaceCase.target());
    var throughConverter = Converter.standard().convert(surfaceCase.source(), surfaceCase.target());

    assertThat(throughSugar).isEqualTo(surfaceCase.expected());
    assertThat(throughConverter).isEqualTo(surfaceCase.expected());
  }

  @Test
  @JourneyTest(speakingId = "structure-object-conversion", acceptance = "toobject")
  @DisplayName("the structural conversions reach the Value surface — a structure to a record answers as the verb does")
  void structuralTargetsReachTheValueSurface() {
    Value<SlotBag> source = Value.of(SlotBag.empty().with("name", Value.of("Ada")).with("age", Value.of(36)));

    Value<Person> throughSugar = source.convertTo(Person.class);
    Value<Person> throughConverter = Converter.standard().convert(source, Person.class);

    assertThat(throughSugar).isEqualTo(throughConverter);
    assertThat(throughSugar)
      .isPresent()
      .isEqualTo(Value.of(new Person("Ada", 36)));
  }

  record ScalarCase(String symbol, Class<?> targetType, String canonicalText) {
    @Override
    public String toString() {
      return symbol;
    }
  }

  record SurfaceCase(String label, Value<?> source, Class<?> target, Value<?> expected) {
    @Override
    public String toString() {
      return label;
    }
  }

  record Person(String name, int age) {}

  record Celsius(double degrees) {}
}
