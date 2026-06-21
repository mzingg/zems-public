package dev.zems.lib.value;

import dev.zems.lib.value.builtin.BuiltInValue;
import dev.zems.lib.value.builtin.ListValue;
import dev.zems.lib.value.builtin.MapValue;
import dev.zems.lib.value.builtin.SetValue;
import dev.zems.lib.value.builtin.SortedMapValue;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
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
import java.util.SortedMap;
import java.util.UUID;
import java.util.function.Function;

/**
 * Sealed interface for immutable value types with explicit state representation, and the single canonical entry point
 * for constructing every value in the hierarchy.
 *
 * <p>
 * All implementations are immutable with no identity semantics, aligned with Project Valhalla's value classes vision.
 *
 * <p>
 * <b>Construction:</b> use the static factories on this interface (e.g. {@link #of(String)},
 * {@link #uuidOf(String)}, {@link #nullValue()}). Subclass factories are package-private — code outside this package
 * must go through {@code Value.*}.
 *
 * <p>
 * <b>Reading:</b> use the {@code is...()} type checks, the {@code as...()} typed accessors, or
 * pattern matching on the sealed subtype hierarchy.
 *
 * <p>
 * <b>Interface decomposition:</b> the instance-side default methods are split across five
 * sibling role-interfaces — {@link ValueStateChecks}, {@link ValueErrorHandling}, {@link ValuePredicates},
 * {@link ValueConversions}, and {@link ValueAccessors} — that {@code Value} extends. Callers see every method on
 * {@code Value} directly; the split
 * exists only to keep the source readable. Static factories below are not inherited and remain on {@code Value}
 * itself.
 *
 * <p>
 * <b>Unboxing</b> lives in the {@link ValueUnboxing} utility namespace and is also surfaced as
 * static methods on {@code Value} ({@link #unbox(Value)}, {@link #unbox(ListValue)}, {@link #unbox(SetValue)},
 * {@link #unbox(MapValue)}, {@link #unbox(SortedMapValue)}). There is no instance {@code unbox()} method — callers use
 * {@code Value.unbox(...)} at the call site so the overload that matches the wrapper subtype picks the right return
 * shape.
 *
 * @param <T> the type of value this wrapper holds
 */
@SuppressWarnings("unused")
public sealed interface Value<T>
  extends ValueStateChecks, ValueErrorHandling, ValuePredicates, ValueConversions, ValueAccessors
  permits UnresolvedValue, UndefinedValue, ErrorValue, NullValue, TombstoneValue, BuiltInValue, BoxedValue, CoreValue
{
  /**
   * Returns the raw JDK payload wrapped by {@code value}. Inverse of {@link #of(Object)} for scalars and
   * {@link BoxedValue}; for collection wrappers ({@link ListValue}, {@link SetValue}, {@link MapValue},
   * {@link SortedMapValue}) this is <b>non-recursive</b> — the returned collection still holds {@code Value<E>}
   * elements. Use the dedicated {@link #unbox(ListValue) unbox(ListValue)} / {@link #unbox(SetValue) unbox(SetValue)} /
   * {@link #unbox(MapValue) unbox(MapValue)} / {@link #unbox(SortedMapValue) unbox(SortedMapValue)} overloads to
   * recursively unbox to a fully raw collection.
   *
   * <p>
   * State markers ({@link NullValue}, {@link UndefinedValue}, {@link UnresolvedValue}, {@link ErrorValue},
   * {@link TombstoneValue}) throw {@link IllegalStateException} — the marshalling layer dispatches state markers
   * separately, so this is reached only on misuse.
   */
  static <T> T unbox(Value<T> value) {
    return ValueUnboxing.unbox(value);
  }

  /**
   * Recursively unboxes a {@link ListValue} to a fully-raw {@code List<E>} where each element is itself unboxed via
   * {@link #unbox(Value)}. Per-element state markers cause {@link IllegalStateException}. The returned list is
   * unmodifiable.
   */
  static <E> List<E> unbox(ListValue<E> value) {
    return ValueUnboxing.unboxList(value);
  }

  /**
   * Recursively unboxes a {@link SetValue} to a fully-raw {@code Set<E>} where each element is itself unboxed via
   * {@link #unbox(Value)}. Per-element state markers cause {@link IllegalStateException}. The returned set is
   * unmodifiable.
   */
  static <E> Set<E> unbox(SetValue<E> value) {
    return ValueUnboxing.unboxSet(value);
  }

  /**
   * Recursively unboxes a {@link MapValue} to a fully-raw {@code Map<K, V>} where each entry's value is itself unboxed
   * via {@link #unbox(Value)}. Per-value state markers cause {@link IllegalStateException}. Keys are returned as
   * stored. The returned map is unmodifiable.
   */
  static <K, V> Map<K, V> unbox(MapValue<K, V> value) {
    return ValueUnboxing.unboxMap(value);
  }

  /**
   * Recursively unboxes a {@link SortedMapValue} to a fully-raw {@code SortedMap<K, V>} where each entry's value is
   * itself unboxed via {@link #unbox(Value)}. Per-value state markers cause {@link IllegalStateException}. Keys and
   * comparator are preserved.
   */
  static <K, V> SortedMap<K, V> unbox(SortedMapValue<K, V> value) {
    return ValueUnboxing.unboxSortedMap(value);
  }

  // ============================================================================================
  // FACTORIES — every static below is a one-line delegate to a package-private helper.
  // The helper layout: ValueStates (singletons + errors), ValueFactories (of/randomUuid),
  // ValueParsers (string parsers + cross-type ctors), ValueCollections (listOf/setOf/mapOf/empty*).
  // External callers see one canonical entry point — Value.* — exactly as documented in CLAUDE.md.
  // ============================================================================================

  // -- state singletons --

  static <T> Value<T> nullValue() {
    return ValueStates.nullValue();
  }

  static <T> Value<T> undefined() {
    return ValueStates.undefined();
  }

  static <T> Value<T> unresolved() {
    return ValueStates.unresolved();
  }

  static <T> Value<T> tombstone() {
    return ValueStates.tombstone();
  }

  // -- errors (strict — these ARE the ErrorValue constructors; null/blank input is a bug) --

  static <T> Value<T> errorOf(Throwable cause, TypeDescriptor<T> expectedType) {
    return ValueStates.errorOf(cause, expectedType);
  }

  static <T> Value<T> errorOf(Throwable cause, Class<T> expectedClass) {
    return ValueStates.errorOf(cause, expectedClass);
  }

  static <T> Value<T> errorMessage(String message, TypeDescriptor<T> expectedType) {
    return ValueStates.errorMessage(message, expectedType);
  }

  static <T> Value<T> errorMessage(String message, Class<T> expectedClass) {
    return ValueStates.errorMessage(message, expectedClass);
  }

  // -- of(T): generic dispatch + 23 typed overloads. All exception-less. Null handling splits by intent:
  //    a typed overload asserts a type, so null is a broken promise -> ErrorValue<T> ("String must not be null").
  //    The generic of(T) is the opaque-boxing entry, so null is valid data -> Value.nullValue().
  //    Spell null deliberately with Value.nullValue() / ofNullable. (See CLAUDE.md "Null and error behaviour".)
  //    The generic of(T) rejects raw List/Set/Map — use listOf/setOf/mapOf for collections.

  static <T> Value<T> of(T raw) {
    return ValueFactories.of(raw);
  }

  static Value<String> of(String value) {
    return ValueFactories.of(value);
  }

  static Value<Boolean> of(Boolean value) {
    return ValueFactories.of(value);
  }

  static Value<Character> of(Character value) {
    return ValueFactories.of(value);
  }

  static <N extends Number> Value<N> of(N value) {
    return ValueFactories.of(value);
  }

  static Value<UUID> of(UUID uuid) {
    return ValueFactories.of(uuid);
  }

  static Value<URI> of(URI uri) {
    return ValueFactories.of(uri);
  }

  static Value<Instant> of(Instant instant) {
    return ValueFactories.of(instant);
  }

  static Value<LocalDate> of(LocalDate value) {
    return ValueFactories.of(value);
  }

  static Value<LocalDateTime> of(LocalDateTime value) {
    return ValueFactories.of(value);
  }

  static Value<ZonedDateTime> of(ZonedDateTime value) {
    return ValueFactories.of(value);
  }

  static Value<OffsetDateTime> of(OffsetDateTime value) {
    return ValueFactories.of(value);
  }

  static Value<LocalTime> of(LocalTime value) {
    return ValueFactories.of(value);
  }

  static Value<Year> of(Year value) {
    return ValueFactories.of(value);
  }

  static Value<YearMonth> of(YearMonth value) {
    return ValueFactories.of(value);
  }

  static Value<ZoneId> of(ZoneId value) {
    return ValueFactories.of(value);
  }

  static Value<Duration> of(Duration value) {
    return ValueFactories.of(value);
  }

  static Value<Period> of(Period value) {
    return ValueFactories.of(value);
  }

  static Value<Locale> of(Locale value) {
    return ValueFactories.of(value);
  }

  static Value<Currency> of(Currency value) {
    return ValueFactories.of(value);
  }

  static Value<InetAddress> of(InetAddress value) {
    return ValueFactories.of(value);
  }

  // -- ofNullable: of(...)'s null-tolerant sibling. A null input (or a null mapper result) becomes nullValue();
  //    otherwise the mapper wraps the present value. The one factory that maps a possibly-null reference to null.

  static <T, R> Value<R> ofNullable(T input, Function<T, Value<R>> ifNotNull) {
    return ValueStates.ofNullable(input, ifNotNull);
  }

  // -- string parsers and cross-type constructors. Body in ValueParsers; here only delegates.

  static Value<UUID> uuidOf(String s) {
    return ValueParsers.uuidOf(s);
  }

  static Value<UUID> uuidOf(long mostSigBits, long leastSigBits) {
    return ValueParsers.uuidOf(mostSigBits, leastSigBits);
  }

  static Value<URI> urlOf(String s) {
    return ValueParsers.urlOf(s);
  }

  static Value<Instant> timeInstantOf(String s) {
    return ValueParsers.timeInstantOf(s);
  }

  static Value<Instant> timeInstantOfEpochSecond(long epochSecond) {
    return ValueParsers.timeInstantOfEpochSecond(epochSecond);
  }

  static Value<Instant> timeInstantOfEpochSecond(long epochSecond, long nanoAdjustment) {
    return ValueParsers.timeInstantOfEpochSecond(epochSecond, nanoAdjustment);
  }

  static Value<Instant> timeInstantOfEpochMilli(long epochMilli) {
    return ValueParsers.timeInstantOfEpochMilli(epochMilli);
  }

  static Value<LocalDate> localDateOf(String s) {
    return ValueParsers.localDateOf(s);
  }

  static Value<LocalDate> localDateOf(int year, int month, int day) {
    return ValueParsers.localDateOf(year, month, day);
  }

  static Value<LocalDate> localDateOfEpochDay(long epochDay) {
    return ValueParsers.localDateOfEpochDay(epochDay);
  }

  static Value<LocalDateTime> localDateTimeOf(String s) {
    return ValueParsers.localDateTimeOf(s);
  }

  static Value<LocalDateTime> localDateTimeOf(LocalDate date, LocalTime time) {
    return ValueParsers.localDateTimeOf(date, time);
  }

  static Value<LocalDateTime> localDateTimeOf(int year, int month, int day, int hour, int minute) {
    return ValueParsers.localDateTimeOf(year, month, day, hour, minute);
  }

  static Value<LocalDateTime> localDateTimeOf(int year, int month, int day, int hour, int minute, int second) {
    return ValueParsers.localDateTimeOf(year, month, day, hour, minute, second);
  }

  static Value<ZonedDateTime> zonedDateTimeOf(String s) {
    return ValueParsers.zonedDateTimeOf(s);
  }

  static Value<OffsetDateTime> offsetDateTimeOf(String s) {
    return ValueParsers.offsetDateTimeOf(s);
  }

  static Value<LocalTime> localTimeOf(String s) {
    return ValueParsers.localTimeOf(s);
  }

  static Value<LocalTime> localTimeOf(int hour, int minute) {
    return ValueParsers.localTimeOf(hour, minute);
  }

  static Value<LocalTime> localTimeOf(int hour, int minute, int second) {
    return ValueParsers.localTimeOf(hour, minute, second);
  }

  static Value<LocalTime> localTimeOf(int hour, int minute, int second, int nano) {
    return ValueParsers.localTimeOf(hour, minute, second, nano);
  }

  static Value<Year> yearOf(String s) {
    return ValueParsers.yearOf(s);
  }

  static Value<Year> yearOf(int year) {
    return ValueParsers.yearOf(year);
  }

  static Value<YearMonth> yearMonthOf(String s) {
    return ValueParsers.yearMonthOf(s);
  }

  static Value<YearMonth> yearMonthOf(int year, int month) {
    return ValueParsers.yearMonthOf(year, month);
  }

  static Value<ZoneId> zoneIdOf(String s) {
    return ValueParsers.zoneIdOf(s);
  }

  static Value<Duration> durationOf(String s) {
    return ValueParsers.durationOf(s);
  }

  static Value<Duration> durationOfNanos(long nanos) {
    return ValueParsers.durationOfNanos(nanos);
  }

  static Value<Duration> durationOfMillis(long millis) {
    return ValueParsers.durationOfMillis(millis);
  }

  static Value<Duration> durationOfSeconds(long seconds) {
    return ValueParsers.durationOfSeconds(seconds);
  }

  static Value<Duration> durationOfMinutes(long minutes) {
    return ValueParsers.durationOfMinutes(minutes);
  }

  static Value<Duration> durationOfHours(long hours) {
    return ValueParsers.durationOfHours(hours);
  }

  static Value<Duration> durationOfDays(long days) {
    return ValueParsers.durationOfDays(days);
  }

  static Value<Period> periodOf(String s) {
    return ValueParsers.periodOf(s);
  }

  static Value<Period> periodOf(int years, int months, int days) {
    return ValueParsers.periodOf(years, months, days);
  }

  static Value<Period> periodOfDays(int days) {
    return ValueParsers.periodOfDays(days);
  }

  static Value<Period> periodOfWeeks(int weeks) {
    return ValueParsers.periodOfWeeks(weeks);
  }

  static Value<Period> periodOfMonths(int months) {
    return ValueParsers.periodOfMonths(months);
  }

  static Value<Period> periodOfYears(int years) {
    return ValueParsers.periodOfYears(years);
  }

  static Value<BigInteger> bigIntegerOf(String s) {
    return ValueParsers.bigIntegerOf(s);
  }

  static Value<BigInteger> bigIntegerOf(long value) {
    return ValueParsers.bigIntegerOf(value);
  }

  static Value<BigInteger> bigIntegerOf(byte[] twosComplement) {
    return ValueParsers.bigIntegerOf(twosComplement);
  }

  static Value<BigDecimal> bigDecimalOf(String s) {
    return ValueParsers.bigDecimalOf(s);
  }

  static Value<BigDecimal> bigDecimalOf(long value) {
    return ValueParsers.bigDecimalOf(value);
  }

  static Value<BigDecimal> bigDecimalOf(double value) {
    return ValueParsers.bigDecimalOf(value);
  }

  static Value<BigDecimal> bigDecimalOf(BigInteger value) {
    return ValueParsers.bigDecimalOf(value);
  }

  static Value<BigDecimal> bigDecimalOf(BigInteger unscaled, int scale) {
    return ValueParsers.bigDecimalOf(unscaled, scale);
  }

  static Value<Locale> localeOf(String tag) {
    return ValueParsers.localeOf(tag);
  }

  static Value<Currency> currencyOf(String code) {
    return ValueParsers.currencyOf(code);
  }

  static Value<InetAddress> inetAddressOf(String literal) {
    return ValueParsers.inetAddressOf(literal);
  }

  static Value<InetAddress> inetAddressOf(byte[] addressBytes) {
    return ValueParsers.inetAddressOf(addressBytes);
  }

  /**
   * Parses {@code text} as the concrete built-in type named by the short {@code symbol} (e.g. {@code "bigint"},
   * {@code "instant"}, {@code "ip"}) — the inverse of {@code BuiltinTypeDescriptors.symbolFor(...)}. Lenient: returns an
   * {@link Value#errorMessage(String, Class) ErrorValue} on an unknown symbol or unparseable text, and never throws.
   * Numeric symbols are strict — a value out of the target's range, or a fractional value for an integer target, is an
   * error.
   */
  static Value<?> fromSymbol(String symbol, String text) {
    return ValueParsers.fromSymbol(symbol, text);
  }

  // -- collections (listOf / setOf / mapOf / empty*) — body in ValueCollections.

  @SafeVarargs
  static <E> Value<List<Value<E>>> listOf(E... rawElements) {
    return ValueCollections.listOf(rawElements);
  }

  static <E> Value<List<Value<E>>> listOf(List<Value<E>> wrappedElements) {
    return ValueCollections.listOf(wrappedElements);
  }

  static <T, E> Value<List<Value<E>>> listOf(Iterable<T> source, Function<T, E> rawMapper) {
    return ValueCollections.listOf(source, rawMapper);
  }

  static <E> Value<List<Value<E>>> emptyList() {
    return ValueCollections.emptyList();
  }

  @SafeVarargs
  static <E> Value<Set<Value<E>>> setOf(E... rawElements) {
    return ValueCollections.setOf(rawElements);
  }

  static <E> Value<Set<Value<E>>> setOf(Set<Value<E>> wrappedElements) {
    return ValueCollections.setOf(wrappedElements);
  }

  static <T, E> Value<Set<Value<E>>> setOf(Iterable<T> source, Function<T, E> rawMapper) {
    return ValueCollections.setOf(source, rawMapper);
  }

  static <E> Value<Set<Value<E>>> emptySet() {
    return ValueCollections.emptySet();
  }

  @SafeVarargs
  static <K, V> Value<Map<K, Value<V>>> mapOf(Map.Entry<K, V>... rawEntries) {
    return ValueCollections.mapOf(rawEntries);
  }

  static <K, V> Value<Map<K, Value<V>>> mapOf(Map<K, Value<V>> wrappedEntries) {
    return ValueCollections.mapOf(wrappedEntries);
  }

  static <T, K, V> Value<Map<K, Value<V>>> mapOf(Iterable<T> source, Function<T, K> keyFn, Function<T, V> rawValueFn) {
    return ValueCollections.mapOf(source, keyFn, rawValueFn);
  }

  static <K, V> Value<Map<K, Value<V>>> emptyMap() {
    return ValueCollections.emptyMap();
  }

  @SafeVarargs
  static <K extends Comparable<K>, V> Value<SortedMap<K, Value<V>>> sortedMapOf(Map.Entry<K, V>... rawEntries) {
    return ValueCollections.sortedMapOf(rawEntries);
  }

  static <K, V> Value<SortedMap<K, Value<V>>> sortedMapOf(SortedMap<K, Value<V>> wrappedEntries) {
    return ValueCollections.sortedMapOf(wrappedEntries);
  }

  static <T, K extends Comparable<K>, V> Value<SortedMap<K, Value<V>>> sortedMapOf(
    Iterable<T> source,
    Function<T, K> keyFn,
    Function<T, V> rawValueFn
  ) {
    return ValueCollections.sortedMapOf(source, keyFn, rawValueFn);
  }

  static <K, V> Value<SortedMap<K, Value<V>>> emptySortedMap() {
    return ValueCollections.emptySortedMap();
  }

  // -- Typed collections: stamp an explicit element/value descriptor (typically TypeDescriptor.oneOf(...))
  // so a heterogeneous collection self-describes on the inferred-write path. Bodies in ValueCollections.

  @SafeVarargs
  static <E> Value<List<Value<E>>> listOfTyped(TypeDescriptor<E> elementType, Value<? extends E>... elements) {
    return ValueCollections.listOfTyped(elementType, elements);
  }

  @SafeVarargs
  static <E> Value<Set<Value<E>>> setOfTyped(TypeDescriptor<E> elementType, Value<? extends E>... elements) {
    return ValueCollections.setOfTyped(elementType, elements);
  }

  static <K, V> Value<Map<K, Value<V>>> mapOfTyped(
    TypeDescriptor<V> valueType,
    Map<K, ? extends Value<? extends V>> entries
  ) {
    return ValueCollections.mapOfTyped(valueType, entries);
  }

  static <K extends Comparable<K>, V> Value<SortedMap<K, Value<V>>> sortedMapOfTyped(
    TypeDescriptor<V> valueType,
    Map<K, ? extends Value<? extends V>> entries
  ) {
    return ValueCollections.sortedMapOfTyped(valueType, entries);
  }

  // -- random UUID --

  static Value<UUID> randomUuid() {
    return ValueFactories.randomUuid();
  }

  /**
   * The {@link TypeDescriptor} that describes this value's payload type, or {@code null} when no payload type can be
   * inferred (bare state markers; {@link BoxedValue} of an opaque class with no discoverable descriptor; empty or
   * heterogeneously-typed collections).
   *
   * <p>
   * Used by the inferred-write path ({@code StateWriter.write(Value)}) to recover the descriptor the writer needs. A
   * {@code null} return signals "inference failed — pass an explicit descriptor via {@code write(value, descriptor)}."
   * Built-in scalars return the matching singleton from
   * {@link dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors}; collections synthesize a structural descriptor
   * from their element types; {@link CoreValue} implementors return whatever descriptor round-trips them.
   */
  TypeDescriptor<T> valueType();
}
