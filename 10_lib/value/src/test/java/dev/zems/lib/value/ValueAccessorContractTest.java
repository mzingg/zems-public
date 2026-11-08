package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
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
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ContractTest
@DisplayName("Value accessor contract")
class ValueAccessorContractTest {

  static Stream<AccessorCase<?>> temporalAndJdkAccessors() {
    return Stream.of(
      new AccessorCase<>(
        "UUID",
        Value.of(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
        Value::isUuid,
        Value::asUuid,
        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
      ),
      new AccessorCase<>(
        "URI",
        Value.of(URI.create("https://example.com")),
        Value::isUrl,
        Value::asUrl,
        URI.create("https://example.com")
      ),
      new AccessorCase<>(
        "BigInteger",
        Value.of(new BigInteger("123")),
        Value::isBigInteger,
        Value::asBigInteger,
        new BigInteger("123")
      ),
      new AccessorCase<>(
        "BigDecimal",
        Value.of(new BigDecimal("3.14")),
        Value::isBigDecimal,
        Value::asBigDecimal,
        new BigDecimal("3.14")
      ),
      new AccessorCase<>(
        "Locale",
        Value.of(Locale.forLanguageTag("en-US")),
        Value::isLocale,
        Value::asLocale,
        Locale.forLanguageTag("en-US")
      ),
      new AccessorCase<>(
        "Currency",
        Value.of(Currency.getInstance("USD")),
        Value::isCurrency,
        Value::asCurrency,
        Currency.getInstance("USD")
      ),
      new AccessorCase<>(
        "InetAddress",
        Value.of(InetAddress.ofLiteral("127.0.0.1")),
        Value::isInetAddress,
        Value::asInetAddress,
        InetAddress.ofLiteral("127.0.0.1")
      ),
      new AccessorCase<>("Character", Value.of('x'), Value::isCharacter, Value::asCharacter, 'x'),
      new AccessorCase<>(
        "Instant",
        Value.of(Instant.parse("2026-01-01T00:00:00Z")),
        Value::isTimeInstant,
        Value::asTimeInstant,
        Instant.parse("2026-01-01T00:00:00Z")
      ),
      new AccessorCase<>(
        "LocalDate",
        Value.of(LocalDate.of(2026, 5, 9)),
        Value::isLocalDate,
        Value::asLocalDate,
        LocalDate.of(2026, 5, 9)
      ),
      new AccessorCase<>(
        "LocalDateTime",
        Value.of(LocalDateTime.of(2026, 5, 9, 10, 15)),
        Value::isLocalDateTime,
        Value::asLocalDateTime,
        LocalDateTime.of(2026, 5, 9, 10, 15)
      ),
      new AccessorCase<>(
        "ZonedDateTime",
        Value.of(ZonedDateTime.parse("2026-05-09T10:15:30+02:00[Europe/Zurich]")),
        Value::isZonedDateTime,
        Value::asZonedDateTime,
        ZonedDateTime.parse("2026-05-09T10:15:30+02:00[Europe/Zurich]")
      ),
      new AccessorCase<>(
        "OffsetDateTime",
        Value.of(OffsetDateTime.parse("2026-05-09T10:15:30+02:00")),
        Value::isOffsetDateTime,
        Value::asOffsetDateTime,
        OffsetDateTime.parse("2026-05-09T10:15:30+02:00")
      ),
      new AccessorCase<>(
        "LocalTime",
        Value.of(LocalTime.of(10, 15)),
        Value::isLocalTime,
        Value::asLocalTime,
        LocalTime.of(10, 15)
      ),
      new AccessorCase<>("Year", Value.of(Year.of(2026)), Value::isYear, Value::asYear, Year.of(2026)),
      new AccessorCase<>(
        "YearMonth",
        Value.of(YearMonth.of(2026, 5)),
        Value::isYearMonth,
        Value::asYearMonth,
        YearMonth.of(2026, 5)
      ),
      new AccessorCase<>(
        "ZoneId",
        Value.of(ZoneId.of("Europe/Zurich")),
        Value::isZoneId,
        Value::asZoneId,
        ZoneId.of("Europe/Zurich")
      ),
      new AccessorCase<>(
        "Duration",
        Value.of(Duration.ofSeconds(60)),
        Value::isDuration,
        Value::asDuration,
        Duration.ofSeconds(60)
      ),
      new AccessorCase<>("Period", Value.of(Period.of(1, 2, 3)), Value::isPeriod, Value::asPeriod, Period.of(1, 2, 3)),
      new AccessorCase<>("Enum", Value.of(Status.ACTIVE), Value::isEnum, Value::asEnum, Status.ACTIVE)
    );
  }

  @ParameterizedTest(name = "is{0}() returns true for matching wrapper")
  @MethodSource("temporalAndJdkAccessors")
  <T> void isProbeMatchesWrapper(AccessorCase<T> spec) {
    assertThat(spec.isProbe().test(spec.sample())).as(spec.label()).isTrue();
  }

  @ParameterizedTest(name = "as{0}() extracts wrapped value")
  @MethodSource("temporalAndJdkAccessors")
  <T> void asAccessorExtractsValue(AccessorCase<T> spec) {
    java.util.Optional<?> result = spec.asAccessor().apply(spec.sample());
    assertThat(result)
      .isPresent()
      .hasValueSatisfying(v -> assertThat(v).isEqualTo(spec.expected()));
  }

  @ParameterizedTest(name = "is{0}() returns false for unrelated wrapper (StringValue)")
  @MethodSource("temporalAndJdkAccessors")
  <T> void isProbeFalseForUnrelated(AccessorCase<T> spec) {
    assertThat(spec.isProbe().test(Value.of("not me")))
      .as(spec.label())
      .isFalse();
  }

  @ParameterizedTest(name = "as{0}() returns Optional.empty() for unrelated wrapper")
  @MethodSource("temporalAndJdkAccessors")
  <T> void asAccessorEmptyForUnrelated(AccessorCase<T> spec) {
    assertThat(spec.asAccessor().apply(Value.of("not me"))).isEmpty();
  }

  @ParameterizedTest(name = "as{0}() returns Optional.empty() for state markers")
  @MethodSource("temporalAndJdkAccessors")
  <T> void asAccessorEmptyForStateMarkers(AccessorCase<T> spec) {
    assertThat(spec.asAccessor().apply(Value.nullValue())).isEmpty();
    assertThat(spec.asAccessor().apply(Value.undefined())).isEmpty();
    assertThat(spec.asAccessor().apply(Value.unresolved())).isEmpty();
  }

  @Test
  @DisplayName("isTypeName/asTypeName single out a type name among ordinary text and state markers")
  void typeNameProbeAndAccessor() {
    Value<TypeName> typeName = Value.typeOf("com.example.Person");
    assertThat(typeName.isTypeName()).isTrue();
    assertThat(typeName.asTypeName()).hasValue("com.example.Person");

    Value<String> text = Value.of("com.example.Person");
    assertThat(text.isTypeName()).isFalse();
    assertThat(text.asTypeName()).isEmpty();

    assertThat(Value.nullValue().asTypeName()).isEmpty();
    assertThat(Value.undefined().asTypeName()).isEmpty();
    assertThat(Value.unresolved().asTypeName()).isEmpty();
    assertThat(Value.nullValue().isTypeName()).isFalse();
  }

  @Test
  @DisplayName("asEnum(Class) answers the constant only for its own enum class")
  void asEnumOfClassMatchesOnlyTheDeclaringEnum() {
    Value<Status> status = Value.of(Status.ACTIVE);
    assertThat(status.asEnum(Status.class)).hasValue(Status.ACTIVE);
    assertThat(status.asEnum(Colour.class)).isEmpty();

    Value<String> text = Value.of("ACTIVE");
    assertThat(text.isEnum()).isFalse();
    assertThat(text.asEnum(Status.class)).isEmpty();

    assertThat(Value.nullValue().asEnum(Status.class)).isEmpty();
    assertThat(Value.undefined().asEnum(Status.class)).isEmpty();
    assertThat(Value.unresolved().asEnum(Status.class)).isEmpty();
    assertThat(Value.errorMessage("err", Status.class).asEnum(Status.class)).isEmpty();
    assertThat(Value.unsupported("no conversion exists", Status.class).asEnum(Status.class)).isEmpty();
    assertThat(Value.tombstone().asEnum(Status.class)).isEmpty();
    assertThat(Value.errorMessage("err", Status.class).isEnum()).isFalse();
  }

  @Test
  @DisplayName("asEnum(null) is a caller bug and throws IllegalArgumentException")
  void asEnumOfNullClassThrows() {
    Value<Status> status = Value.of(Status.ACTIVE);
    assertThatThrownBy(() -> status.asEnum((Class<Status>) null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("type must not be null");
  }

  @Test
  @DisplayName("isMapOf returns true when keys and values match")
  void isMapOfPositive() {
    Value<?> map = Value.mapOf(java.util.Map.entry("a", 1), java.util.Map.entry("b", 2));
    assertThat(map.isMapOf(String.class, Integer.class)).isTrue();
  }

  @Test
  @DisplayName("isMapOf returns false when key type differs")
  void isMapOfWrongKeyType() {
    Value<?> map = Value.mapOf(java.util.Map.entry("a", 1));
    assertThat(map.isMapOf(Integer.class, Integer.class)).isFalse();
  }

  @Test
  @DisplayName("isMapOf returns false when value type differs")
  void isMapOfWrongValueType() {
    Value<?> map = Value.mapOf(java.util.Map.entry("a", 1));
    assertThat(map.isMapOf(String.class, String.class)).isFalse();
  }

  @Test
  @DisplayName("isMapOf returns true on empty map (vacuous)")
  void isMapOfEmpty() {
    Value<?> map = Value.emptyMap();
    assertThat(map.isMapOf(String.class, Integer.class)).isTrue();
  }

  @Test
  @DisplayName("isMapOf returns false for non-map wrappers")
  void isMapOfNonMap() {
    assertThat(Value.of("hi").isMapOf(String.class, Integer.class)).isFalse();
    assertThat(Value.nullValue().isMapOf(String.class, Integer.class)).isFalse();
  }

  @Test
  @DisplayName("asList() returns the wrapped element list")
  void asListReturnsElements() {
    Value<?> list = Value.listOf("a", "b");
    assertThat(list.asList())
      .get()
      .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
      .containsExactly(Value.of("a"), Value.of("b"));
  }

  @Test
  @DisplayName("asList() returns Optional.empty() for non-list wrappers")
  void asListEmptyForNonList() {
    assertThat(Value.of("hi").asList()).isEmpty();
    assertThat(Value.nullValue().asList()).isEmpty();
  }

  @Test
  @DisplayName("asMap() returns the wrapped entries")
  void asMapReturnsEntries() {
    Value<?> map = Value.mapOf(java.util.Map.entry("k", 7));
    assertThat(map.asMap()).isPresent();
  }

  @Test
  @DisplayName("asMap() returns Optional.empty() for non-map wrappers")
  void asMapEmptyForNonMap() {
    assertThat(Value.of("hi").asMap()).isEmpty();
    assertThat(Value.nullValue().asMap()).isEmpty();
  }

  @Test
  @DisplayName("isDefined / isNotNull / isResolved on state markers")
  void statePredicates() {
    Value<String> nullV = Value.nullValue();
    assertThat(nullV.isDefined()).isTrue();
    assertThat(nullV.isNotNull()).isFalse();
    assertThat(nullV.isResolved()).isTrue();

    Value<String> undef = Value.undefined();
    assertThat(undef.isDefined()).isFalse();

    Value<String> unres = Value.unresolved();
    assertThat(unres.isResolved()).isFalse();

    Value<String> err = Value.errorMessage("e", String.class);
    assertThat(err.error()).isPresent();
  }

  record AccessorCase<T>(
    String label,
    Value<T> sample,
    Predicate<Value<?>> isProbe,
    Function<Value<?>, java.util.Optional<?>> asAccessor,
    T expected
  ) {
    @Override
    public String toString() {
      return label;
    }
  }

  enum Status {
    ACTIVE,
  }

  enum Colour {
    ACTIVE,
  }
}
