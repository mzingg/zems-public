package dev.zems.lib.value;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.THROWABLE;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.builtin.BigDecimalValue;
import dev.zems.lib.value.builtin.BigIntegerValue;
import dev.zems.lib.value.builtin.BooleanValue;
import dev.zems.lib.value.builtin.ByteValue;
import dev.zems.lib.value.builtin.CharacterValue;
import dev.zems.lib.value.builtin.CurrencyValue;
import dev.zems.lib.value.builtin.DoubleValue;
import dev.zems.lib.value.builtin.DurationValue;
import dev.zems.lib.value.builtin.EnumValue;
import dev.zems.lib.value.builtin.FloatValue;
import dev.zems.lib.value.builtin.InetAddressValue;
import dev.zems.lib.value.builtin.IntegerValue;
import dev.zems.lib.value.builtin.LocalDateTimeValue;
import dev.zems.lib.value.builtin.LocalDateValue;
import dev.zems.lib.value.builtin.LocalTimeValue;
import dev.zems.lib.value.builtin.LocaleValue;
import dev.zems.lib.value.builtin.LongValue;
import dev.zems.lib.value.builtin.MeasurableValue;
import dev.zems.lib.value.builtin.OffsetDateTimeValue;
import dev.zems.lib.value.builtin.PeriodValue;
import dev.zems.lib.value.builtin.ShortValue;
import dev.zems.lib.value.builtin.StringValue;
import dev.zems.lib.value.builtin.TimeInstantValue;
import dev.zems.lib.value.builtin.TypeValue;
import dev.zems.lib.value.builtin.UrlValue;
import dev.zems.lib.value.builtin.UuidValue;
import dev.zems.lib.value.builtin.VersionValue;
import dev.zems.lib.value.builtin.YearMonthValue;
import dev.zems.lib.value.builtin.YearValue;
import dev.zems.lib.value.builtin.ZoneIdValue;
import dev.zems.lib.value.builtin.ZonedDateTimeValue;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ContractTest
@DisplayName("ValueFactories")
class ValueFactoriesTest {

  static Stream<TypedFactory<?>> typedFactories() {
    return Stream.of(
      new TypedFactory<>("of(String)", Value::of, "hello", StringValue.class, "String must not be null"),
      new TypedFactory<>("of(Boolean)", Value::of, Boolean.TRUE, BooleanValue.class, "Boolean must not be null"),
      new TypedFactory<>("of(Character)", Value::of, 'x', CharacterValue.class, "Character must not be null"),
      new TypedFactory<>(
        "of(UUID)",
        Value::of,
        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
        UuidValue.class,
        "UUID must not be null"
      ),
      new TypedFactory<>("of(URI)", Value::of, URI.create("https://x.com"), UrlValue.class, "URI must not be null"),
      new TypedFactory<>(
        "of(Instant)",
        Value::of,
        Instant.parse("2026-01-01T00:00:00Z"),
        TimeInstantValue.class,
        "Instant must not be null"
      ),
      new TypedFactory<>(
        "of(LocalDate)",
        Value::of,
        LocalDate.of(2026, 5, 9),
        LocalDateValue.class,
        "LocalDate must not be null"
      ),
      new TypedFactory<>(
        "of(LocalDateTime)",
        Value::of,
        LocalDateTime.of(2026, 5, 9, 10, 15),
        LocalDateTimeValue.class,
        "LocalDateTime must not be null"
      ),
      new TypedFactory<>(
        "of(ZonedDateTime)",
        Value::of,
        ZonedDateTime.parse("2026-05-09T10:15:30+02:00[Europe/Zurich]"),
        ZonedDateTimeValue.class,
        "ZonedDateTime must not be null"
      ),
      new TypedFactory<>(
        "of(OffsetDateTime)",
        Value::of,
        OffsetDateTime.parse("2026-05-09T10:15:30+02:00"),
        OffsetDateTimeValue.class,
        "OffsetDateTime must not be null"
      ),
      new TypedFactory<>(
        "of(LocalTime)",
        Value::of,
        LocalTime.of(10, 15),
        LocalTimeValue.class,
        "LocalTime must not be null"
      ),
      new TypedFactory<>("of(Year)", Value::of, Year.of(2026), YearValue.class, "Year must not be null"),
      new TypedFactory<>(
        "of(YearMonth)",
        Value::of,
        YearMonth.of(2026, 5),
        YearMonthValue.class,
        "YearMonth must not be null"
      ),
      new TypedFactory<>(
        "of(ZoneId)",
        Value::of,
        ZoneId.of("Europe/Zurich"),
        ZoneIdValue.class,
        "ZoneId must not be null"
      ),
      new TypedFactory<>(
        "of(Duration)",
        Value::of,
        Duration.ofSeconds(60),
        DurationValue.class,
        "Duration must not be null"
      ),
      new TypedFactory<>("of(Period)", Value::of, Period.of(1, 2, 3), PeriodValue.class, "Period must not be null"),
      new TypedFactory<>(
        "of(Currency)",
        Value::of,
        Currency.getInstance("USD"),
        CurrencyValue.class,
        "Currency must not be null"
      ),
      new TypedFactory<>(
        "of(InetAddress)",
        Value::of,
        InetAddress.ofLiteral("127.0.0.1"),
        InetAddressValue.class,
        "InetAddress must not be null"
      ),
      new TypedFactory<>(
        "of(TypeName)",
        Value::of,
        new TypeName("com.example.Person"),
        TypeValue.class,
        "TypeName must not be null"
      ),
      new TypedFactory<>(
        "of(Version)",
        Value::of,
        Version.parse("1.0.0", VersionDialect.SEMVER),
        VersionValue.class,
        "Version must not be null"
      ),
      new TypedFactory<>(
        "of(Quantity)",
        Value::of,
        Quantity.parse("9.99 EUR"),
        MeasurableValue.class,
        "Quantity must not be null"
      )
    );
  }

  static Stream<MeasurableNullCase> measurableNullCases() {
    Currency euro = Currency.getInstance("EUR");
    return Stream.of(
      new MeasurableNullCase("of(null BigInteger, Unit)", () -> Value.of((BigInteger) null, Units.METRE), "Magnitude"),
      new MeasurableNullCase("of(null BigDecimal, Unit)", () -> Value.of((BigDecimal) null, Units.METRE), "Magnitude"),
      new MeasurableNullCase("of(null BigInteger, Currency)", () -> Value.of((BigInteger) null, euro), "Magnitude"),
      new MeasurableNullCase("of(null BigDecimal, Currency)", () -> Value.of((BigDecimal) null, euro), "Magnitude"),
      new MeasurableNullCase("of(long, null Unit)", () -> Value.of(5L, (Unit) null), "Unit"),
      new MeasurableNullCase("of(BigInteger, null Unit)", () -> Value.of(BigInteger.TWO, (Unit) null), "Unit"),
      new MeasurableNullCase("of(BigDecimal, null Unit)", () -> Value.of(BigDecimal.ONE, (Unit) null), "Unit"),
      new MeasurableNullCase("of(long, null Currency)", () -> Value.of(5L, (Currency) null), "Currency"),
      new MeasurableNullCase(
        "of(BigInteger, null Currency)",
        () -> Value.of(BigInteger.TWO, (Currency) null),
        "Currency"
      ),
      new MeasurableNullCase(
        "of(BigDecimal, null Currency)",
        () -> Value.of(BigDecimal.ONE, (Currency) null),
        "Currency"
      ),
      new MeasurableNullCase(
        "of(null BigDecimal, null Unit)",
        () -> Value.of((BigDecimal) null, (Unit) null),
        "Magnitude"
      ),
      new MeasurableNullCase(
        "of(null BigInteger, null Currency)",
        () -> Value.of((BigInteger) null, (Currency) null),
        "Magnitude"
      )
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("measurableNullCases")
  @DisplayName("a null magnitude, unit or currency is an ErrorValue expecting Quantity; the magnitude is checked first")
  void measurableNullArgumentsAreErrorValues(MeasurableNullCase nullCase) {
    assertThat(nullCase.factory().get())
      .isError()
      .hasErrorMessage(nullCase.nullArgument() + " must not be null")
      .hasExpectedTypeName("dev.zems.lib.value.Quantity");
  }

  static Stream<NumericFactory<?>> numericFactories() {
    return Stream.of(
      new NumericFactory<>("Integer", 42, IntegerValue.class),
      new NumericFactory<>("Long", 99L, LongValue.class),
      new NumericFactory<>("Double", 3.14, DoubleValue.class),
      new NumericFactory<>("Float", 2.5f, FloatValue.class),
      new NumericFactory<>("Short", (short) 7, ShortValue.class),
      new NumericFactory<>("Byte", (byte) 9, ByteValue.class),
      new NumericFactory<>("BigInteger", new BigInteger("123"), BigIntegerValue.class),
      new NumericFactory<>("BigDecimal", new BigDecimal("3.14"), BigDecimalValue.class)
    );
  }

  @ParameterizedTest(name = "{0} wraps non-null sample")
  @MethodSource("typedFactories")
  <T> void wrapsNonNull(TypedFactory<T> spec) {
    Value<T> result = spec.factory().apply(spec.sample());
    assertThat(result).isInstanceOf(spec.wrapperClass());
    assertThat(result).isNotInstanceOf(ErrorValue.class);
  }

  @ParameterizedTest(name = "{0} rejects null with documented message")
  @MethodSource("typedFactories")
  <T> void rejectsNull(TypedFactory<T> spec) {
    Value<T> result = spec.factory().apply(null);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).hasMessage(spec.nullErrorMessage());
  }

  @ParameterizedTest(name = "Number dispatch — {0}")
  @MethodSource("numericFactories")
  <N extends Number> void numberDispatchProducesCorrectWrapper(NumericFactory<N> spec) {
    Value<N> result = Value.of(spec.sample());
    assertThat(result).isInstanceOf(spec.wrapperClass());
  }

  @Test
  @DisplayName("of((Number) null) returns ErrorValue with documented message")
  void numberNullReturnsError() {
    Value<Number> result = Value.of((Number) null);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).hasMessage("Number must not be null");
  }

  @Test
  @DisplayName("of((Number) BigInteger) routes through the Number arm to BigIntegerValue")
  void numberDispatchBigIntegerViaNumberRef() {
    Number bigIntAsNumber = new BigInteger("99999999999999999999");
    Value<Number> result = Value.of(bigIntAsNumber);
    assertThat(result).isInstanceOf(BigIntegerValue.class);
  }

  @Test
  @DisplayName("of((Number) BigDecimal) routes through the Number arm to BigDecimalValue")
  void numberDispatchBigDecimalViaNumberRef() {
    Number bigDecAsNumber = new BigDecimal("3.141592653589793238462643383279502884");
    Value<Number> result = Value.of(bigDecAsNumber);
    assertThat(result).isInstanceOf(BigDecimalValue.class);
  }

  @Test
  @DisplayName("of((Object) BigInteger) routes via the generic dispatch to BigIntegerValue")
  void genericDispatchBigInteger() {
    Object bigIntAsObject = new BigInteger("99999999999999999999");
    Value<Object> result = Value.of(bigIntAsObject);
    assertThat(result).isInstanceOf(BigIntegerValue.class);
  }

  @Test
  @DisplayName("of((Object) Version) routes via the generic dispatch to an equal VersionValue")
  void genericDispatchVersion() {
    Version version = Version.parse("1.0", VersionDialect.MODULE);

    Value<Object> result = Value.of((Object) version);

    assertThat(result).isInstanceOf(VersionValue.class).isEqualTo(Value.of(version));
  }

  @Test
  @DisplayName("of((Object) Quantity) routes via the generic dispatch to an equal MeasurableValue")
  void genericDispatchQuantity() {
    Quantity quantity = Quantity.parse("4.50 EUR/kg");

    Value<Object> result = Value.of((Object) quantity);

    assertThat(result).isInstanceOf(MeasurableValue.class).isEqualTo(Value.of(quantity));
  }

  @Test
  @DisplayName("Value.listOf accepts raw BigInteger varargs without producing ErrorValue")
  void listOfRawBigInteger() {
    Value<?> list = Value.listOf(new BigInteger("99999999999999999999"));
    assertThat(list.isListOf(BigInteger.class)).isTrue();
  }

  @Test
  @DisplayName("Value.listOf accepts raw BigDecimal varargs without producing ErrorValue")
  void listOfRawBigDecimal() {
    Value<?> list = Value.listOf(new BigDecimal("3.141592653589793238462643383279502884"));
    assertThat(list.isListOf(BigDecimal.class)).isTrue();
  }

  @Test
  @DisplayName("of(unsupported Number subclass) returns ErrorValue with descriptive message")
  void unsupportedNumberSubclass() {
    var custom = new Number() {
      @Override
      public int intValue() {
        return 0;
      }

      @Override
      public long longValue() {
        return 0;
      }

      @Override
      public float floatValue() {
        return 0;
      }

      @Override
      public double doubleValue() {
        return 0;
      }
    };
    Value<? extends Number> result = Value.of(custom);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).hasMessageContaining("Unsupported Number type");
  }

  @Test
  @DisplayName("Value.of((Object) null) returns NullValue")
  void genericOfNullReturnsNullValue() {
    assertThat(Value.of((Object) null)).isInstanceOf(NullValue.class);
  }

  @Test
  @DisplayName("null reading splits by static type on purpose: typed -> ErrorValue, generic -> NullValue")
  void nullReadingSplitsByStaticTypeDeliberately() {
    assertThat(Value.of((String) null)).isInstanceOf(ErrorValue.class);
    assertThat(Value.of((Object) null)).isInstanceOf(NullValue.class);
  }

  @Test
  @DisplayName("Value.of(Value) returns the same Value (idempotent)")
  void genericOfValueIsIdempotent() {
    Value<String> wrapped = Value.of("hi");
    assertThat(Value.of((Object) wrapped)).isSameAs(wrapped);
  }

  @Test
  @DisplayName("Value.of(unknown raw type) wraps in BoxedValue")
  void genericOfUnknownTypeBoxes() {
    record Custom(int n) {}
    Value<Object> result = Value.of((Object) new Custom(7));
    assertThat(result).isInstanceOf(BoxedValue.class);
  }

  @Test
  @DisplayName("Value.of(enum constant) routes through the generic dispatch to EnumValue")
  void genericOfEnumConstantIsAnEnumValue() {
    Value<Status> result = Value.of(Status.ACTIVE);
    assertThat(result).isInstanceOf(EnumValue.class).isNotInstanceOf(BoxedValue.class);
    assertThat(result.unbox()).isSameAs(Status.ACTIVE);
  }

  @Test
  @DisplayName("Value.of((Status) null) stays NullValue: enums have no typed factory, so the generic entry answers")
  void genericOfNullEnumReturnsNullValue() {
    assertThat(Value.of((Status) null)).isInstanceOf(NullValue.class);
  }

  @Test
  @DisplayName("Value.of((Object) List) rejects with IAE pointing to Value.listOf")
  void genericOfListRejected() {
    assertThatThrownBy(() -> Value.of((Object) List.of(Value.of("a"), Value.of("b"))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Value.listOf");
  }

  @Test
  @DisplayName("Value.of((Object) Set) rejects with IAE pointing to Value.setOf")
  void genericOfSetRejected() {
    assertThatThrownBy(() -> Value.of((Object) Set.of(Value.of("a"), Value.of("b"))))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Value.setOf");
  }

  @Test
  @DisplayName("Value.of((Object) Map) rejects with IAE pointing to Value.mapOf")
  void genericOfMapRejected() {
    Map<String, Value<String>> raw = Map.of("k", Value.of("v"));
    assertThatThrownBy(() -> Value.of((Object) raw))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Value.mapOf");
  }

  @Test
  @DisplayName("Value.listOf wraps raw record varargs via BoxedValue")
  void listOfRawRecordsAutoWrapsBoxedValue() {
    record Pt(int x, int y) {}
    Value<List<Value<Pt>>> v = Value.listOf(new Pt(1, 2), new Pt(3, 4));
    var elements = v.asList().get();
    assertThat(elements).hasSize(2);
    assertThat(elements).allMatch(e -> e instanceof BoxedValue<?>);
  }

  @Test
  @DisplayName("Value.listOf((Type) null) wraps null as a NullValue element")
  void listOfNullVarargBecomesNullValue() {
    Value<List<Value<String>>> v = Value.listOf((String) null);
    var elements = v.asList().get();
    assertThat(elements).hasSize(1);
    assertThat(elements.getFirst()).isInstanceOf(NullValue.class);
  }

  @Test
  @DisplayName("Value.mapOf wraps raw record entry values via BoxedValue")
  void mapOfRawRecordEntriesAutoWrapsBoxedValue() {
    record Pt(int x, int y) {}
    Value<Map<String, Value<Pt>>> v = Value.mapOf(Map.entry("a", new Pt(1, 2)), Map.entry("b", new Pt(3, 4)));
    var entries = v.asMap().get();
    assertThat(entries.get("a")).isInstanceOf(BoxedValue.class);
    assertThat(entries.get("b")).isInstanceOf(BoxedValue.class);
  }

  @Test
  @DisplayName("of(Locale) rejects the undetermined locale (\"und\")")
  void localeOfUndefined() {
    Locale und = Locale.forLanguageTag("");
    Value<Locale> result = Value.of(und);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).hasMessageContaining("undetermined locale");
  }

  @Test
  @DisplayName("of(Locale) wraps a valid locale")
  void localeOfValid() {
    assertThat(Value.of(Locale.forLanguageTag("en-US"))).isInstanceOf(LocaleValue.class);
  }

  @Test
  @DisplayName("randomUuid() produces a non-null UuidValue")
  void randomUuidProducesUuidValue() {
    Value<UUID> a = Value.randomUuid();
    Value<UUID> b = Value.randomUuid();
    assertThat(a).isInstanceOf(UuidValue.class);
    assertThat(b).isInstanceOf(UuidValue.class);
    assertThat(a.asUuid()).isPresent();
    assertThat(b.asUuid()).isPresent();
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  @DisplayName("ofNullable(null, fn) returns NullValue (does not call fn)")
  void ofNullableNullSkipsLambda() {
    String input = null;
    Value<String> result = Value.ofNullable(input, Value::of);
    assertThat(result).isInstanceOf(NullValue.class);
  }

  @Test
  @DisplayName("ofNullable returns NullValue when the mapper returns null (never a literal null Value)")
  void ofNullableMapperReturningNullYieldsNullValue() {
    Function<String, Value<String>> nullMapper = s -> null;
    Value<String> result = Value.ofNullable("hello", nullMapper);
    assertThat(result).isInstanceOf(NullValue.class);
  }

  @Test
  @DisplayName("ofNullable(value, fn) calls fn on non-null input")
  void ofNullableNonNullCallsLambda() {
    Value<String> result = Value.ofNullable("hello", Value::of);
    assertThat(result).isInstanceOf(StringValue.class);
  }

  @Test
  @DisplayName("ofNullable(null) on a typed null reference returns NullValue")
  void ofNullableSingleArgNullReturnsNullValue() {
    String input = null;
    Value<String> result = Value.ofNullable(input);
    assertThat(result).isInstanceOf(NullValue.class);
  }

  @Test
  @DisplayName("ofNullable(value) wraps a non-null reference in the matching wrapper")
  void ofNullableSingleArgNonNullWraps() {
    Value<String> result = Value.ofNullable("hello");
    assertThat(result).isInstanceOf(StringValue.class);
    assertThat(result).hasStringValue("hello");
  }

  @Test
  @DisplayName("ofNullable(input) and of(input) split on the same typed null: NullValue vs ErrorValue")
  void ofNullableSingleArgDiffersFromTypedOf() {
    String input = null;
    assertThat(Value.ofNullable(input)).isInstanceOf(NullValue.class);
    assertThat(Value.of(input)).isInstanceOf(ErrorValue.class);
  }

  @Test
  @DisplayName("errorMessage(String, Class) wraps a message into an ErrorValue")
  void errorMessageClass() {
    Value<String> result = Value.errorMessage("bad", String.class);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).hasMessage("bad");
  }

  @Test
  @DisplayName("errorOf(Throwable, Class) preserves the original throwable")
  void errorOfClass() {
    var cause = new IllegalStateException("boom");
    Value<String> result = Value.errorOf(cause, String.class);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).hasValue(cause);
  }

  record TypedFactory<T>(
    String label,
    Function<T, Value<T>> factory,
    T sample,
    Class<? extends Value<?>> wrapperClass,
    String nullErrorMessage
  ) {
    @Override
    public String toString() {
      return label;
    }
  }

  record MeasurableNullCase(String label, Supplier<Value<Quantity>> factory, String nullArgument) {
    @Override
    public String toString() {
      return label;
    }
  }

  record NumericFactory<N extends Number>(String label, N sample, Class<? extends Value<?>> wrapperClass) {
    @Override
    public String toString() {
      return label;
    }
  }

  enum Status {
    ACTIVE,
  }
}
