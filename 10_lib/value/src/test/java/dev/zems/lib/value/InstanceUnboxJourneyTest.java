package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.JourneyTest;
import dev.zems.lib.value.builtin.ListValue;
import dev.zems.lib.value.builtin.MapValue;
import dev.zems.lib.value.builtin.SetValue;
import dev.zems.lib.value.builtin.SortedMapValue;
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
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Instance unbox() — every value answers its payload, collections unbox their elements by name")
class InstanceUnboxJourneyTest {

  static Stream<UnboxCase<?>> scalarCases() {
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
      new UnboxCase<>("InetAddress", Value.of(InetAddress.ofLiteral("127.0.0.1")), InetAddress.ofLiteral("127.0.0.1")),
      new UnboxCase<>("TypeName", Value.typeOf("com.example.Person"), new TypeName("com.example.Person")),
      new UnboxCase<>("Enum", Value.of(Status.ACTIVE), Status.ACTIVE)
    );
  }

  static Stream<MarkerCase> stateMarkers() {
    return Stream.of(
      new MarkerCase("NullValue", Value.nullValue()),
      new MarkerCase("UndefinedValue", Value.undefined()),
      new MarkerCase("UnresolvedValue", Value.unresolved()),
      new MarkerCase("ErrorValue", Value.errorMessage("err", String.class)),
      new MarkerCase("UnsupportedValue", Value.unsupported("no conversion exists", String.class)),
      new MarkerCase("TombstoneValue", TombstoneValue.instance())
    );
  }

  @ParameterizedTest(name = "{0} answers its JDK payload")
  @MethodSource("scalarCases")
  @JourneyTest(speakingId = "instance-unbox-on-value", acceptance = "unbox")
  @DisplayName("a scalar, type or enum built-in answers its JDK payload")
  <T> void builtInAnswersItsPayload(UnboxCase<T> spec) {
    T payload = spec.wrapped().unbox();

    assertThat(payload).isEqualTo(spec.expected());
  }

  @Test
  @JourneyTest(speakingId = "instance-unbox-on-value", acceptance = "unbox")
  @DisplayName("a collection wrapper answers its backing collection, elements still boxed")
  void collectionWrapperAnswersItsBackingCollection() {
    ListValue<String> list = (ListValue<String>) Value.listOf(List.of(Value.of("a"), Value.of("b")));
    SetValue<String> set = (SetValue<String>) Value.setOf(Set.of(Value.of("a")));
    MapValue<String, Integer> map = (MapValue<String, Integer>) Value.mapOf(Map.of("k", Value.of(1)));
    SortedMapValue<String, Integer> sorted = (SortedMapValue<String, Integer>) Value.sortedMapOf(
      new TreeMap<>(Map.of("k", Value.of(1)))
    );

    List<Value<String>> listPayload = list.unbox();
    Set<Value<String>> setPayload = set.unbox();
    Map<String, Value<Integer>> mapPayload = map.unbox();
    SortedMap<String, Value<Integer>> sortedPayload = sorted.unbox();

    assertThat(listPayload).isSameAs(list.elements()).containsExactly(Value.of("a"), Value.of("b"));
    assertThat(setPayload).isSameAs(set.elements()).containsExactly(Value.of("a"));
    assertThat(mapPayload)
      .isSameAs(map.entries())
      .containsExactly(Map.entry("k", Value.of(1)));
    assertThat(sortedPayload)
      .isSameAs(sorted.entries())
      .containsExactly(Map.entry("k", Value.of(1)));
  }

  @Test
  @JourneyTest(speakingId = "instance-unbox-on-value", acceptance = "unbox")
  @DisplayName("a boxed value answers the very inner object it wraps")
  void boxedValueAnswersItsInnerObject() {
    record Custom(int n) {}
    Custom inner = new Custom(7);
    Value<Custom> boxed = Value.of(inner);

    Custom payload = boxed.unbox();

    assertThat(payload).isSameAs(inner);
  }

  @Test
  @JourneyTest(speakingId = "instance-unbox-on-value", acceptance = "unbox")
  @DisplayName("a CoreValue answers itself")
  void coreValueAnswersItself() {
    Tag tag = new Tag("blog");
    Value<Tag> value = tag;

    Tag payload = value.unbox();

    assertThat(payload).isSameAs(tag);
  }

  @Test
  @JourneyTest(speakingId = "instance-unbox-on-value", acceptance = "elements")
  @DisplayName("unboxElements on a list and a set answers the unboxed elements")
  void unboxElementsAnswersUnboxedListAndSet() {
    ListValue<String> list = (ListValue<String>) Value.listOf(List.of(Value.of("b"), Value.of("a"), Value.of("b")));
    SetValue<Integer> set = (SetValue<Integer>) Value.setOf(Set.of(Value.of(1), Value.of(2)));

    List<String> listElements = list.unboxElements();
    Set<Integer> setElements = set.unboxElements();

    assertThat(listElements).containsExactly("b", "a", "b");
    assertThat(setElements).containsExactlyInAnyOrder(1, 2);
  }

  @Test
  @JourneyTest(speakingId = "instance-unbox-on-value", acceptance = "elements")
  @DisplayName("unboxEntries on a map and a sorted map answers the unboxed entry values, keys unchanged")
  void unboxEntriesAnswersUnboxedMapAndSortedMap() {
    var mapSource = new LinkedHashMap<String, Value<Integer>>();
    mapSource.put("b", Value.of(1));
    mapSource.put("a", Value.of(2));
    MapValue<String, Integer> map = (MapValue<String, Integer>) Value.mapOf(mapSource);
    SortedMapValue<String, Integer> sorted = (SortedMapValue<String, Integer>) Value.sortedMapOf(
      new TreeMap<>(Map.of("b", Value.of(1), "a", Value.of(2)))
    );

    Map<String, Integer> mapEntries = map.unboxEntries();
    SortedMap<String, Integer> sortedEntries = sorted.unboxEntries();

    assertThat(mapEntries).containsExactly(Map.entry("b", 1), Map.entry("a", 2));
    assertThat(sortedEntries).containsExactly(Map.entry("a", 2), Map.entry("b", 1));
  }

  @Test
  @JourneyTest(speakingId = "instance-unbox-on-value", acceptance = "elements")
  @DisplayName("unboxElements goes one level deep: a list of lists answers the inner lists' boxed elements")
  void unboxElementsGoesOneLevelDeep() {
    ListValue<List<Value<String>>> nested = (ListValue<List<Value<String>>>) Value.listOf(
      List.of(Value.listOf("a", "b"))
    );

    List<List<Value<String>>> elements = nested.unboxElements();

    assertThat(elements).containsExactly(List.of(Value.of("a"), Value.of("b")));
  }

  @ParameterizedTest(name = "{0}.unbox() throws")
  @MethodSource("stateMarkers")
  @JourneyTest(speakingId = "instance-unbox-on-value", acceptance = "marker")
  @DisplayName("unbox() on a state marker throws ISE naming the marker type")
  void unboxOnStateMarkerThrows(MarkerCase spec) {
    assertThatThrownBy(() -> spec.marker().unbox())
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Cannot unbox state-marker value " + spec.name());
  }

  @Test
  @JourneyTest(speakingId = "instance-unbox-on-value", acceptance = "marker")
  @DisplayName("unboxElements throws ISE naming the marker when a list or set holds a state marker")
  void unboxElementsThrowsOnMarkerElement() {
    ListValue<String> list = (ListValue<String>) Value.listOf(List.of(Value.of("a"), Value.nullValue()));
    var setSource = new LinkedHashSet<Value<String>>();
    setSource.add(Value.of("a"));
    setSource.add(Value.nullValue());
    SetValue<String> set = (SetValue<String>) Value.setOf(setSource);

    assertThatThrownBy(list::unboxElements)
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Cannot unbox state-marker value NullValue");
    assertThatThrownBy(set::unboxElements)
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Cannot unbox state-marker value NullValue");
  }

  @Test
  @JourneyTest(speakingId = "instance-unbox-on-value", acceptance = "marker")
  @DisplayName("unboxEntries throws ISE naming the marker when a map or sorted map holds a state-marker value")
  void unboxEntriesThrowsOnMarkerEntryValue() {
    var mapSource = new LinkedHashMap<String, Value<Integer>>();
    mapSource.put("a", Value.of(1));
    mapSource.put("b", Value.undefined());
    MapValue<String, Integer> map = (MapValue<String, Integer>) Value.mapOf(mapSource);
    SortedMapValue<String, Integer> sorted = (SortedMapValue<String, Integer>) Value.sortedMapOf(
      new TreeMap<>(mapSource)
    );

    assertThatThrownBy(map::unboxEntries)
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Cannot unbox state-marker value UndefinedValue");
    assertThatThrownBy(sorted::unboxEntries)
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Cannot unbox state-marker value UndefinedValue");
  }

  @Test
  @JourneyTest(speakingId = "instance-unbox-on-value", acceptance = "marker")
  @DisplayName("unbox() on a collection holding a state marker answers the backing collection without throwing")
  void unboxOnCollectionWithMarkerDoesNotThrow() {
    ListValue<String> list = (ListValue<String>) Value.listOf(List.of(Value.of("a"), Value.nullValue()));

    List<Value<String>> payload = list.unbox();

    assertThat(payload).isSameAs(list.elements()).containsExactly(Value.of("a"), Value.nullValue());
  }

  @Test
  @JourneyTest(speakingId = "instance-unbox-on-value", acceptance = "sametype")
  @DisplayName("unbox() answers the same object whether the list is held as a ListValue or as a wide Value")
  void unboxDoesNotDependOnDeclaredType() {
    ListValue<String> typed = (ListValue<String>) Value.listOf("a", "b");
    Value<List<Value<String>>> wide = typed;

    List<Value<String>> fromWide = wide.unbox();
    List<Value<String>> fromTyped = typed.unbox();

    assertThat(fromWide).isSameAs(fromTyped).isSameAs(typed.elements());
  }

  record UnboxCase<T>(String label, Value<T> wrapped, T expected) {
    @Override
    public String toString() {
      return label;
    }
  }

  record MarkerCase(String name, Value<?> marker) {
    @Override
    public String toString() {
      return name;
    }
  }

  record Tag(String name) implements CoreValue<Tag> {
    @Override
    public TypeDescriptor<Tag> valueType() {
      return null;
    }
  }

  enum Status {
    ACTIVE,
  }
}
