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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Value collection accessors (isList / isListOf / isMap / isMapOf)")
@ContractTest
class ValueCollectionAccessorTest {

  @Nested
  @DisplayName("isList")
  class IsList {

    @Test
    @DisplayName("returns true for ListValue")
    void trueForList() {
      Value<?> value = Value.listOf(Value.of("a"), Value.of("b"));
      assertThat(value.isList()).isTrue();
    }

    @Test
    @DisplayName("returns false for NonNullValue wrapping String")
    void falseForString() {
      assertThat(Value.of("not a list").isList()).isFalse();
    }

    @Test
    @DisplayName("returns false for state values")
    void falseForStateValues() {
      assertThat(Value.nullValue().isList()).isFalse();
    }
  }

  @Nested
  @DisplayName("isListOf")
  class IsListOf {

    @Test
    @DisplayName("returns true when all elements match the type")
    void trueForMatchingElements() {
      Value<?> value = Value.listOf(Value.of("a"), Value.of("b"));
      assertThat(value.isListOf(String.class)).isTrue();
    }

    @Test
    @DisplayName("returns false when elements have mixed types")
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void falseForMixedTypes() {
      List<Value<?>> mixed = List.of(Value.of("a"), Value.of(42));
      Value<?> value = Value.listOf((List) mixed);
      assertThat(value.isListOf(String.class)).isFalse();
    }

    @Test
    @DisplayName("returns true for empty list")
    void trueForEmptyList() {
      Value<?> value = Value.emptyList();
      assertThat(value.isListOf(String.class)).isTrue();
    }

    @Test
    @DisplayName("returns false for non-list value")
    void falseForNonList() {
      assertThat(Value.of("hello").isListOf(String.class)).isFalse();
    }

    @Test
    @DisplayName("returns false when elements wrap a different type")
    void falseForNonMatchingElementWrapType() {
      Value<?> value = Value.listOf(Value.urlOf("https://example.com"));
      assertThat(value.isListOf(String.class)).isFalse();
    }
  }

  @Nested
  @DisplayName("isMap")
  class IsMap {

    @Test
    @DisplayName("returns true for MapValue")
    void trueForMap() {
      Value<?> value = Value.mapOf(Map.entry("k", Value.of("v")));
      assertThat(value.isMap()).isTrue();
    }

    @Test
    @DisplayName("returns false for NonNullValue wrapping String")
    void falseForString() {
      assertThat(Value.of("not a map").isMap()).isFalse();
    }

    @Test
    @DisplayName("returns false for state values")
    void falseForStateValues() {
      assertThat(Value.nullValue().isMap()).isFalse();
    }
  }

  @Nested
  @DisplayName("isMapOf")
  class IsMapOf {

    @Test
    @DisplayName("returns true when all keys and values match")
    void trueForMatchingTypes() {
      Value<?> value = Value.mapOf(Map.entry("k", Value.of("v")));
      assertThat(value.isMapOf(String.class, String.class)).isTrue();
    }

    @Test
    @DisplayName("returns false when value types don't match")
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void falseForWrongValueType() {
      Map<String, Value<?>> withInt = Map.of("k", Value.of(42));
      Value<?> value = Value.mapOf((Map) withInt);
      assertThat(value.isMapOf(String.class, String.class)).isFalse();
    }

    @Test
    @DisplayName("returns true for empty map")
    void trueForEmptyMap() {
      Value<?> value = Value.emptyMap();
      assertThat(value.isMapOf(String.class, String.class)).isTrue();
    }

    @Test
    @DisplayName("returns false when key types don't match")
    void falseForWrongKeyType() {
      Value<?> value = Value.mapOf(Map.entry("k", Value.of("v")));
      assertThat(value.isMapOf(Integer.class, String.class)).isFalse();
    }

    @Test
    @DisplayName("returns false for non-map value")
    void falseForNonMap() {
      assertThat(Value.of("hello").isMapOf(String.class, String.class)).isFalse();
    }

    @Test
    @DisplayName("returns false when values wrap a different type")
    void falseForNonMatchingValueWrapType() {
      Value<?> value = Value.mapOf(Map.entry("k", Value.urlOf("https://example.com")));
      assertThat(value.isMapOf(String.class, String.class)).isFalse();
    }
  }

  @Nested
  @DisplayName("isXxxOf — parameterised across all BuiltInValue scalars")
  class CollectionPredicateParameterised {

    static Stream<ScalarCase> scalars() {
      return Stream.of(
        new ScalarCase("String", String.class, Value.of("hello")),
        new ScalarCase("Boolean", Boolean.class, Value.of(true)),
        new ScalarCase("Character", Character.class, Value.of('x')),
        new ScalarCase("Integer", Integer.class, Value.of(42)),
        // Long > Integer.MAX_VALUE so it's unambiguously a Long, not also an Integer.
        new ScalarCase("Long", Long.class, Value.of(10_000_000_000L)),
        new ScalarCase("Double", Double.class, Value.of(Math.PI)),
        new ScalarCase("Float", Float.class, Value.of(1.0e20f)),
        // Short > Byte.MAX_VALUE so it's unambiguously a Short.
        new ScalarCase("Short", Short.class, Value.of((short) 30_000)),
        new ScalarCase("Byte", Byte.class, Value.of((byte) 9)),
        // BigInteger > Long.MAX_VALUE so it's unambiguously a BigInteger.
        new ScalarCase("BigInteger", BigInteger.class, Value.of(new BigInteger("99999999999999999999"))),
        new ScalarCase(
          "BigDecimal",
          BigDecimal.class,
          Value.of(new BigDecimal("3.141592653589793238462643383279502884"))
        ),
        new ScalarCase("UUID", UUID.class, Value.of(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))),
        new ScalarCase("URI", URI.class, Value.of(URI.create("https://x.com"))),
        new ScalarCase("Instant", Instant.class, Value.of(Instant.parse("2026-01-01T00:00:00Z"))),
        new ScalarCase("LocalDate", LocalDate.class, Value.of(LocalDate.of(2026, 5, 9))),
        new ScalarCase("LocalDateTime", LocalDateTime.class, Value.of(LocalDateTime.of(2026, 5, 9, 10, 15))),
        new ScalarCase(
          "ZonedDateTime",
          ZonedDateTime.class,
          Value.of(ZonedDateTime.parse("2026-05-09T10:15:30+02:00[Europe/Zurich]"))
        ),
        new ScalarCase(
          "OffsetDateTime",
          OffsetDateTime.class,
          Value.of(OffsetDateTime.parse("2026-05-09T10:15:30+02:00"))
        ),
        new ScalarCase("LocalTime", LocalTime.class, Value.of(LocalTime.of(10, 15))),
        new ScalarCase("Year", Year.class, Value.of(Year.of(2026))),
        new ScalarCase("YearMonth", YearMonth.class, Value.of(YearMonth.of(2026, 5))),
        new ScalarCase("ZoneId", ZoneId.class, Value.of(ZoneId.of("Europe/Zurich"))),
        new ScalarCase("Duration", Duration.class, Value.of(Duration.ofSeconds(60))),
        new ScalarCase("Period", Period.class, Value.of(Period.of(1, 2, 3))),
        new ScalarCase("Locale", Locale.class, Value.of(Locale.forLanguageTag("en-US"))),
        new ScalarCase("Currency", Currency.class, Value.of(Currency.getInstance("USD"))),
        new ScalarCase("InetAddress", InetAddress.class, Value.of(InetAddress.ofLiteral("192.168.1.1")))
      );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scalars")
    @DisplayName("isListOf matches the wrapped type and rejects a mismatched type")
    void isListOf_systematic(ScalarCase c) {
      Value<?> list = Value.listOf(c.wrapped());
      assertThat(list.isListOf(c.type())).as("positive for %s", c.name()).isTrue();
      assertThat(list.isListOf(Throwable.class)).as("negative for %s", c.name()).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scalars")
    @DisplayName("isSetOf matches the wrapped type and rejects a mismatched type")
    void isSetOf_systematic(ScalarCase c) {
      Value<?> set = Value.setOf(c.wrapped());
      assertThat(set.isSetOf(c.type())).as("positive for %s", c.name()).isTrue();
      assertThat(set.isSetOf(Throwable.class)).as("negative for %s", c.name()).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scalars")
    @DisplayName("isMapOf matches the wrapped value type and rejects a mismatched type")
    void isMapOf_systematic(ScalarCase c) {
      Value<?> map = Value.mapOf(Map.entry("k", c.wrapped()));
      assertThat(map.isMapOf(String.class, c.type())).as("positive for %s", c.name()).isTrue();
      assertThat(map.isMapOf(String.class, Throwable.class)).as("negative for %s", c.name()).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scalars")
    @DisplayName("isSortedMapOf matches the wrapped value type and rejects a mismatched type")
    void isSortedMapOf_systematic(ScalarCase c) {
      Value<?> sortedMap = Value.sortedMapOf(Map.entry("k", c.wrapped()));
      assertThat(sortedMap.isSortedMapOf(String.class, c.type())).as("positive for %s", c.name()).isTrue();
      assertThat(sortedMap.isSortedMapOf(String.class, Throwable.class)).as("negative for %s", c.name()).isFalse();
    }

    record ScalarCase(String name, Class<?> type, Value<?> wrapped) {
      @Override
      public String toString() {
        return name;
      }
    }
  }

  @Nested
  @DisplayName("collection predicates — state-marker elements never match")
  class StateMarkerElements {

    @Test
    @DisplayName("isListOf rejects when an element is NullValue / UndefinedValue / UnresolvedValue")
    void listOf_stateMarkerElements() {
      assertThat(Value.listOf(Value.nullValue()).isListOf(String.class)).isFalse();
      assertThat(Value.listOf(Value.undefined()).isListOf(String.class)).isFalse();
      assertThat(Value.listOf(Value.unresolved()).isListOf(String.class)).isFalse();
    }

    @Test
    @DisplayName("isSetOf rejects when an element is a state marker")
    void setOf_stateMarkerElements() {
      assertThat(Value.setOf(Value.nullValue()).isSetOf(String.class)).isFalse();
      assertThat(Value.setOf(Value.undefined()).isSetOf(String.class)).isFalse();
      assertThat(Value.setOf(Value.unresolved()).isSetOf(String.class)).isFalse();
    }

    @Test
    @DisplayName("isMapOf rejects when a value is a state marker")
    void mapOf_stateMarkerValues() {
      assertThat(Value.mapOf(Map.entry("k", Value.nullValue())).isMapOf(String.class, String.class)).isFalse();
      assertThat(Value.mapOf(Map.entry("k", Value.undefined())).isMapOf(String.class, String.class)).isFalse();
      assertThat(Value.mapOf(Map.entry("k", Value.unresolved())).isMapOf(String.class, String.class)).isFalse();
    }

    @Test
    @DisplayName("isSortedMapOf rejects when a value is a state marker")
    void sortedMapOf_stateMarkerValues() {
      assertThat(
        Value.sortedMapOf(Map.entry("k", Value.nullValue())).isSortedMapOf(String.class, String.class)
      ).isFalse();
      assertThat(
        Value.sortedMapOf(Map.entry("k", Value.undefined())).isSortedMapOf(String.class, String.class)
      ).isFalse();
      assertThat(
        Value.sortedMapOf(Map.entry("k", Value.unresolved())).isSortedMapOf(String.class, String.class)
      ).isFalse();
    }
  }
}
