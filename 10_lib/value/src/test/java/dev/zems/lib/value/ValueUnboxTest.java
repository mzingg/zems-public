package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.builtin.ListValue;
import dev.zems.lib.value.builtin.MapValue;
import dev.zems.lib.value.builtin.SetValue;
import dev.zems.lib.value.builtin.SortedMapValue;
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
import java.util.SortedMap;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Contract test for {@link ValueUnboxing} — the inverse of {@code Value.of(Object)}. Round-trips each non-state
 * {@code Value<T>} subtype through {@link Value#unbox(Value)} and asserts the raw JDK value comes back equal. Also pins
 * the typed collection overloads ({@link Value#unbox(ListValue)} / {@link Value#unbox(SetValue)} /
 * {@link Value#unbox(MapValue)} / {@link Value#unbox(SortedMapValue)}) that recursively unbox collection elements.
 */
@ContractTest
@DisplayName("ValueUnboxing")
class ValueUnwrapTest {

  static Stream<UnboxCase<?>> roundTripCases() {
    return Stream.of(
      new UnboxCase<>("String", Value.of("hello"), "hello"),
      new UnboxCase<>("Boolean", Value.of(true), true),
      new UnboxCase<>("Character", Value.of('x'), 'x'),
      new UnboxCase<>("Integer", Value.of(42), 42),
      new UnboxCase<>("Long", Value.of(99L), 99L),
      new UnboxCase<>("Double", Value.of(3.14), 3.14),
      new UnboxCase<>("Float", Value.of(2.5f), 2.5f),
      new UnboxCase<>("Short", Value.of((short) 7), (short) 7),
      new UnboxCase<>("Byte", Value.of((byte) 9), (byte) 9),
      new UnboxCase<>("BigInteger", Value.of(new BigInteger("123")), new BigInteger("123")),
      new UnboxCase<>("BigDecimal", Value.of(new BigDecimal("3.14")), new BigDecimal("3.14")),
      new UnboxCase<>(
        "UUID",
        Value.of(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
      ),
      new UnboxCase<>("URI", Value.of(URI.create("https://x.com")), URI.create("https://x.com")),
      new UnboxCase<>(
        "Instant",
        Value.of(Instant.parse("2026-01-01T00:00:00Z")),
        Instant.parse("2026-01-01T00:00:00Z")
      ),
      new UnboxCase<>("LocalDate", Value.of(LocalDate.of(2026, 5, 9)), LocalDate.of(2026, 5, 9)),
      new UnboxCase<>(
        "LocalDateTime",
        Value.of(LocalDateTime.of(2026, 5, 9, 10, 15)),
        LocalDateTime.of(2026, 5, 9, 10, 15)
      ),
      new UnboxCase<>(
        "ZonedDateTime",
        Value.of(ZonedDateTime.parse("2026-05-09T10:15:30+02:00[Europe/Zurich]")),
        ZonedDateTime.parse("2026-05-09T10:15:30+02:00[Europe/Zurich]")
      ),
      new UnboxCase<>(
        "OffsetDateTime",
        Value.of(OffsetDateTime.parse("2026-05-09T10:15:30+02:00")),
        OffsetDateTime.parse("2026-05-09T10:15:30+02:00")
      ),
      new UnboxCase<>("LocalTime", Value.of(LocalTime.of(10, 15)), LocalTime.of(10, 15)),
      new UnboxCase<>("Year", Value.of(Year.of(2026)), Year.of(2026)),
      new UnboxCase<>("YearMonth", Value.of(YearMonth.of(2026, 5)), YearMonth.of(2026, 5)),
      new UnboxCase<>("ZoneId", Value.of(ZoneId.of("Europe/Zurich")), ZoneId.of("Europe/Zurich")),
      new UnboxCase<>("Duration", Value.of(Duration.ofSeconds(60)), Duration.ofSeconds(60)),
      new UnboxCase<>("Period", Value.of(Period.of(1, 2, 3)), Period.of(1, 2, 3)),
      new UnboxCase<>("Locale", Value.of(Locale.forLanguageTag("en-US")), Locale.forLanguageTag("en-US")),
      new UnboxCase<>("Currency", Value.of(Currency.getInstance("USD")), Currency.getInstance("USD")),
      new UnboxCase<>("InetAddress", Value.of(InetAddress.ofLiteral("127.0.0.1")), InetAddress.ofLiteral("127.0.0.1"))
    );
  }

  static Stream<Value<?>> stateMarkers() {
    return Stream.of(
      Value.nullValue(),
      Value.undefined(),
      Value.unresolved(),
      Value.errorMessage("err", String.class),
      TombstoneValue.instance()
    );
  }

  @ParameterizedTest(name = "Value.unbox({0}) returns the original raw value")
  @MethodSource("roundTripCases")
  <T> void unboxRoundTrips(UnboxCase<T> spec) {
    assertThat(Value.unbox(spec.wrapped())).isEqualTo(spec.expected());
  }

  // ============ Non-recursive scalar overload on collections ============

  @Test
  @DisplayName("Value.unbox(Value<List<Value<E>>>) is non-recursive — returns the stored Value<E> list")
  void unboxOnWideListReferenceIsNonRecursive() {
    Value<List<Value<String>>> list = Value.listOf("a", "b");
    List<Value<String>> raw = Value.unbox(list);
    assertThat(raw).containsExactly(Value.of("a"), Value.of("b"));
  }

  @Test
  @DisplayName("Value.unbox(Value<Map<K, Value<V>>>) is non-recursive — returns the stored entries")
  void unboxOnWideMapReferenceIsNonRecursive() {
    Value<Map<String, Value<Integer>>> map = Value.mapOf(Map.entry("k", 1));
    Map<String, Value<Integer>> raw = Value.unbox(map);
    assertThat(raw).containsEntry("k", Value.of(1));
  }

  @Test
  @DisplayName("Value.unbox(BoxedValue) returns the wrapped instance")
  void unboxBoxedReturnsInner() {
    record Custom(int n) {}
    Custom inner = new Custom(7);
    Value<Object> wrapped = Value.of((Object) inner);
    assertThat(Value.unbox(wrapped)).isEqualTo(inner);
  }

  // ============ Typed collection overloads — recursive raw projection ============

  @Test
  @DisplayName("Value.unbox(ListValue<E>) recursively unboxes to List<E>")
  void unboxListValueIsRecursive() {
    ListValue<String> list = (ListValue<String>) Value.listOf("a", "b");
    List<String> raw = Value.unbox(list);
    assertThat(raw).containsExactly("a", "b");
  }

  @Test
  @DisplayName("Value.unbox(SetValue<E>) recursively unboxes to Set<E>")
  void unboxSetValueIsRecursive() {
    SetValue<String> set = (SetValue<String>) Value.setOf("a", "b");
    assertThat(Value.unbox(set)).containsExactlyInAnyOrder("a", "b");
  }

  @Test
  @DisplayName("Value.unbox(MapValue<K, V>) recursively unboxes to Map<K, V>")
  void unboxMapValueIsRecursive() {
    MapValue<String, Integer> map = (MapValue<String, Integer>) Value.mapOf(Map.entry("k", 1));
    Map<String, Integer> raw = Value.unbox(map);
    assertThat(raw).isEqualTo(Map.of("k", 1));
  }

  @Test
  @DisplayName("Value.unbox(SortedMapValue<K, V>) recursively unboxes to SortedMap<K, V>")
  void unboxSortedMapValueIsRecursive() {
    SortedMapValue<String, Integer> sorted = (SortedMapValue<String, Integer>) Value.sortedMapOf(
      Map.entry("a", 1),
      Map.entry("b", 2)
    );
    SortedMap<String, Integer> raw = Value.unbox(sorted);
    assertThat(raw).containsExactly(Map.entry("a", 1), Map.entry("b", 2));
  }

  @Test
  @DisplayName("Value.unbox(SortedMapValue) preserves natural key order regardless of insertion order")
  void unboxSortedMapPreservesNaturalKeyOrder() {
    SortedMapValue<String, Integer> sorted = (SortedMapValue<String, Integer>) Value.sortedMapOf(
      Map.entry("c", 3),
      Map.entry("a", 1),
      Map.entry("b", 2)
    );
    SortedMap<String, Integer> raw = Value.unbox(sorted);
    assertThat(raw.keySet()).containsExactly("a", "b", "c");
    assertThat(raw.firstKey()).isEqualTo("a");
    assertThat(raw.lastKey()).isEqualTo("c");
  }

  @Test
  @DisplayName("Value.unbox(SortedMapValue) returns an unmodifiable SortedMap")
  void unboxedSortedMapIsUnmodifiable() {
    SortedMapValue<String, Integer> sorted = (SortedMapValue<String, Integer>) Value.sortedMapOf(Map.entry("a", 1));
    SortedMap<String, Integer> raw = Value.unbox(sorted);
    assertThatThrownBy(() -> raw.put("b", 2)).isInstanceOf(UnsupportedOperationException.class);
  }

  // ============ State markers — unbox is invalid ============

  @ParameterizedTest(name = "Value.unbox({0}) throws ISE")
  @MethodSource("stateMarkers")
  void unboxStateMarkersThrows(Value<?> stateMarker) {
    assertThatThrownBy(() -> Value.unbox(stateMarker))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Cannot unbox state-marker value");
  }

  record UnboxCase<T>(String label, Value<T> wrapped, T expected) {
    @Override
    public String toString() {
      return label;
    }
  }
}
