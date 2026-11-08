package dev.zems.lib.value;

import dev.zems.lib.value.builtin.BuiltInValue;
import dev.zems.lib.value.convert.Converter;
import dev.zems.lib.value.json.JsonDocumentValue;
import dev.zems.lib.value.json.engine.JsonValue;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.UUID;
import java.util.function.Function;

/**
 * Sealed interface for immutable value types with explicit state representation, and the single entry point for
 * constructing every value in the hierarchy.
 *
 * <p>
 * Construct through the static factories here; the subtype constructors are not supported entry points. The factories
 * and parsers never throw on data: null, blank or invalid input answers an {@link ErrorValue}, and the few that throw
 * on a caller bug carry a {@code @throws} tag. A factory may answer a shared cached instance, so compare values with
 * {@code equals}, never {@code ==}. Read through the {@code is...()} checks, the {@code as...()} accessors, or pattern
 * matching on the sealed hierarchy; the accessors are same-type only, and a cross-type read goes through
 * {@link #convertTo(Class)}.
 */
@SuppressWarnings("unused") // IDE inspection: most factory overloads have no caller inside this repository
public sealed interface Value<T>
  extends ValueStateChecks, ValueErrorHandling, ValuePredicates, ValueAccessors, ValueTransforms<T>
  permits
    UnresolvedValue,
    UndefinedValue,
    ErrorValue,
    NullValue,
    TombstoneValue,
    UnsupportedValue,
    BuiltInValue,
    BoxedValue,
    CoreValue,
    JsonDocumentValue
{
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

  /**
   * An {@link ErrorValue} carrying {@code cause}.
   *
   * @throws IllegalArgumentException if {@code cause} or {@code expectedType} is {@code null}
   */
  static <T> Value<T> errorOf(Throwable cause, TypeDescriptor<T> expectedType) {
    return ValueStates.errorOf(cause, expectedType);
  }

  /**
   * An {@link ErrorValue} carrying {@code cause}, its expected-type descriptor named after {@code expectedClass}.
   *
   * @throws IllegalArgumentException if {@code cause} is {@code null}, or {@code expectedClass} is {@code null}, an
   *     array, a raw {@code List} or {@code Map}, or a {@code Value} type that is not a {@link CoreValue}
   */
  static <T> Value<T> errorOf(Throwable cause, Class<T> expectedClass) {
    return ValueStates.errorOf(cause, expectedClass);
  }

  /**
   * An {@link ErrorValue} carrying an {@link IllegalStateException} with {@code message}.
   *
   * @throws IllegalArgumentException if {@code message} is blank or {@code expectedType} is {@code null}
   */
  static <T> Value<T> errorMessage(String message, TypeDescriptor<T> expectedType) {
    return ValueStates.errorMessage(message, expectedType);
  }

  /**
   * An {@link ErrorValue} carrying an {@link IllegalStateException} with {@code message}, its expected-type descriptor
   * named after {@code expectedClass}.
   *
   * @throws IllegalArgumentException if {@code message} is blank, or {@code expectedClass} is {@code null}, an array, a
   *     raw {@code List} or {@code Map}, or a {@code Value} type that is not a {@link CoreValue}
   */
  static <T> Value<T> errorMessage(String message, Class<T> expectedClass) {
    return ValueStates.errorMessage(message, expectedClass);
  }

  /**
   * An {@link UnsupportedValue}: the request cannot be served because of the type pair or the operation, not because of
   * the data. The reason names what was asked and why it cannot be served.
   *
   * @throws IllegalArgumentException if {@code reason} is blank or {@code expectedType} is {@code null}
   */
  static <T> Value<T> unsupported(String reason, TypeDescriptor<T> expectedType) {
    return ValueStates.unsupported(reason, expectedType);
  }

  /**
   * An {@link UnsupportedValue} whose expected-type descriptor is named after {@code expectedClass}.
   *
   * @throws IllegalArgumentException if {@code reason} is blank, or {@code expectedClass} is {@code null}, an array, a
   *     raw {@code List} or {@code Map}, or a {@code Value} type that is not a {@link CoreValue}
   */
  static <T> Value<T> unsupported(String reason, Class<T> expectedClass) {
    return ValueStates.unsupported(reason, expectedClass);
  }

  /**
   * Wraps {@code raw} in the value type its runtime class selects, or in a {@link BoxedValue} when none does; a
   * {@code null} answers {@link #nullValue()} and a {@code Value} answers itself.
   *
   * @throws IllegalArgumentException for a raw {@code List}, {@code Set} or {@code Map}; use {@code listOf},
   *     {@code setOf}, {@code mapOf} or {@code sortedMapOf}
   */
  static <T> Value<T> of(T raw) {
    return ValueFactories.of(raw);
  }

  /**
   * Wraps the string. A {@code null} answers an {@link ErrorValue} here and in every other typed {@code of}, unlike
   * {@link #of(Object)}.
   */
  static Value<String> of(String value) {
    return ValueFactories.of(value);
  }

  static Value<Boolean> of(Boolean value) {
    return ValueFactories.of(value);
  }

  static Value<Character> of(Character value) {
    return ValueFactories.of(value);
  }

  /**
   * Wraps the number; a {@code Number} class other than the six primitive wrappers, {@link BigInteger} and
   * {@link BigDecimal}, such as {@code AtomicInteger}, answers an {@link ErrorValue}.
   */
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

  /** Wraps the locale; the undetermined locale ({@link Locale#ROOT}, tag {@code und}) answers an {@link ErrorValue}. */
  static Value<Locale> of(Locale value) {
    return ValueFactories.of(value);
  }

  static Value<Currency> of(Currency value) {
    return ValueFactories.of(value);
  }

  static Value<InetAddress> of(InetAddress value) {
    return ValueFactories.of(value);
  }

  static Value<TypeName> of(TypeName value) {
    return ValueFactories.of(value);
  }

  static Value<Version> of(Version value) {
    return ValueFactories.of(value);
  }

  static Value<Quantity> of(Quantity value) {
    return ValueFactories.of(value);
  }

  /** A quantity of {@code magnitude} in {@code unit}; a null unit answers an {@link ErrorValue}. */
  static Value<Quantity> of(long magnitude, Unit unit) {
    return ValueFactories.of(magnitude, unit);
  }

  /** A quantity of {@code magnitude} in {@code unit}; a null argument answers an {@link ErrorValue}. */
  static Value<Quantity> of(BigInteger magnitude, Unit unit) {
    return ValueFactories.of(magnitude, unit);
  }

  /** A quantity of {@code magnitude}, scale kept, in {@code unit}; a null argument answers an {@link ErrorValue}. */
  static Value<Quantity> of(BigDecimal magnitude, Unit unit) {
    return ValueFactories.of(magnitude, unit);
  }

  /** An amount of {@code magnitude} in {@code currency}; a null currency answers an {@link ErrorValue}. */
  static Value<Quantity> of(long magnitude, Currency currency) {
    return ValueFactories.of(magnitude, currency);
  }

  /** An amount of {@code magnitude} in {@code currency}; a null argument answers an {@link ErrorValue}. */
  static Value<Quantity> of(BigInteger magnitude, Currency currency) {
    return ValueFactories.of(magnitude, currency);
  }

  /** An amount of {@code magnitude}, scale kept, in {@code currency}; a null argument answers an {@link ErrorValue}. */
  static Value<Quantity> of(BigDecimal magnitude, Currency currency) {
    return ValueFactories.of(magnitude, currency);
  }

  /**
   * Wraps a possibly-null reference, always reading {@code null} as {@link #nullValue()}. Unlike {@code of}, which
   * picks its overload from the static type and answers an {@code ErrorValue} for a typed null, this never takes the
   * typed reading.
   *
   * @throws IllegalArgumentException for a raw {@code List}, {@code Set} or {@code Map}, as {@link #of(Object)} does
   */
  static <T> Value<T> ofNullable(T input) {
    return ValueStates.ofNullable(input);
  }

  /**
   * Answers {@code ifNotNull} applied to a non-null {@code input}, or {@link #nullValue()} for a null input or a null
   * result.
   *
   * @throws IllegalArgumentException if {@code ifNotNull} is {@code null}
   */
  static <T, R> Value<R> ofNullable(T input, Function<T, Value<R>> ifNotNull) {
    return ValueStates.ofNullable(input, ifNotNull);
  }

  static Value<UUID> uuidOf(String s) {
    return ValueParsers.uuidOf(s);
  }

  static Value<UUID> uuidOf(long mostSigBits, long leastSigBits) {
    return ValueParsers.uuidOf(mostSigBits, leastSigBits);
  }

  /** Parses an RFC 2396 URI; a relative reference such as {@code foo/bar} is accepted too. */
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

  /** Reads big-endian two's-complement bytes; a null or empty array answers an {@link ErrorValue}. */
  static Value<BigInteger> bigIntegerOf(byte[] twosComplement) {
    return ValueParsers.bigIntegerOf(twosComplement);
  }

  static Value<BigDecimal> bigDecimalOf(String s) {
    return ValueParsers.bigDecimalOf(s);
  }

  static Value<BigDecimal> bigDecimalOf(long value) {
    return ValueParsers.bigDecimalOf(value);
  }

  /**
   * Converts through {@link Double#toString(double)}, so {@code 0.1} stays {@code 0.1}; NaN and the infinities answer
   * an {@link ErrorValue}.
   */
  static Value<BigDecimal> bigDecimalOf(double value) {
    return ValueParsers.bigDecimalOf(value);
  }

  static Value<BigDecimal> bigDecimalOf(BigInteger value) {
    return ValueParsers.bigDecimalOf(value);
  }

  static Value<BigDecimal> bigDecimalOf(BigInteger unscaled, int scale) {
    return ValueParsers.bigDecimalOf(unscaled, scale);
  }

  /**
   * Parses a BCP 47 language tag as {@link Locale#forLanguageTag(String)} does, which keeps the well-formed start of a
   * malformed tag ({@code xx-!!} reads as {@code xx}); a tag with no well-formed language, such as {@code en_US},
   * answers an {@link ErrorValue}.
   */
  static Value<Locale> localeOf(String tag) {
    return ValueParsers.localeOf(tag);
  }

  /** Parses an ISO 4217 code such as {@code EUR}; a symbol such as {@code €} answers an {@link ErrorValue}. */
  static Value<Currency> currencyOf(String code) {
    return ValueParsers.currencyOf(code);
  }

  /** Parses an IPv4 or IPv6 literal without any name lookup; a host name answers an {@link ErrorValue}. */
  static Value<InetAddress> inetAddressOf(String literal) {
    return ValueParsers.inetAddressOf(literal);
  }

  /** An address of 4 (IPv4) or 16 (IPv6) bytes; any other length answers an {@link ErrorValue}. */
  static Value<InetAddress> inetAddressOf(byte[] addressBytes) {
    return ValueParsers.inetAddressOf(addressBytes);
  }

  /**
   * The name of {@code type}; a primitive class names itself ({@code int}) and an array class answers an
   * {@link ErrorValue}.
   */
  static Value<TypeName> typeOf(Class<?> type) {
    return ValueFactories.typeOf(type);
  }

  /**
   * Checks {@code name} as a qualified Java name and never loads a class; an invalid name answers an
   * {@link ErrorValue}.
   */
  static Value<TypeName> typeOf(String name) {
    return ValueParsers.typeOf(name);
  }

  /** Parses {@code text} as a version under {@code dialect}; a null dialect or invalid text answers an {@link ErrorValue}. */
  static Value<Version> versionOf(String text, VersionDialect dialect) {
    return ValueParsers.versionOf(text, dialect);
  }

  /** Parses the dialect-qualified form {@code <symbol>:<version>}, such as {@code semver:1.0.0}. */
  static Value<Version> qualifiedVersionOf(String qualified) {
    return ValueParsers.qualifiedVersionOf(qualified);
  }

  /**
   * Parses the canonical quantity form, such as {@code 9.99 EUR} or {@code 10 m/s}; null, blank or invalid text, and
   * an unknown unit symbol, answer an {@link ErrorValue} naming the text.
   */
  static Value<Quantity> measurableOf(String text) {
    return ValueParsers.measurableOf(text);
  }

  /**
   * Parses {@code text} as the built-in type named by the short {@code symbol} ({@code "bigint"}, {@code "instant"},
   * {@code "ip"}, ...). Never throws: an unknown symbol or unparseable text answers an {@link ErrorValue}, as does a
   * number outside the target's range, a fraction for an integer target, or a {@code bigint} whose exponent takes it
   * past 1 000 digits.
   */
  static Value<?> fromSymbol(String symbol, String text) {
    return ValueParsers.fromSymbol(symbol, text);
  }

  /**
   * Wraps each element as {@link #of(Object)} does, so a {@code null} element is {@link #nullValue()}; a null array
   * answers an {@link ErrorValue}.
   *
   * @throws IllegalArgumentException for an element that is a raw {@code List}, {@code Set} or {@code Map}
   */
  @SafeVarargs
  static <E> Value<List<Value<E>>> listOf(E... rawElements) {
    return ValueCollections.listOf(rawElements);
  }

  /** Copies the list; a null list or a null element answers an {@link ErrorValue}. */
  static <E> Value<List<Value<E>>> listOf(List<Value<E>> wrappedElements) {
    return ValueCollections.listOf(wrappedElements);
  }

  /**
   * Maps each item through {@code rawMapper} and wraps the result as {@link #of(Object)} does; a null source or mapper
   * answers an {@link ErrorValue}, and an exception from the mapper propagates.
   *
   * @throws IllegalArgumentException for a mapped value that is a raw {@code List}, {@code Set} or {@code Map}
   */
  static <T, E> Value<List<Value<E>>> listOf(Iterable<T> source, Function<T, E> rawMapper) {
    return ValueCollections.listOf(source, rawMapper);
  }

  static <E> Value<List<Value<E>>> emptyList() {
    return ValueCollections.emptyList();
  }

  /**
   * Wraps each element as {@link #of(Object)} does; equal elements collapse and the set keeps no element order. A null
   * array answers an {@link ErrorValue}.
   *
   * @throws IllegalArgumentException for an element that is a raw {@code List}, {@code Set} or {@code Map}
   */
  @SafeVarargs
  static <E> Value<Set<Value<E>>> setOf(E... rawElements) {
    return ValueCollections.setOf(rawElements);
  }

  /** Copies the set, keeping no element order; a null set or a null element answers an {@link ErrorValue}. */
  static <E> Value<Set<Value<E>>> setOf(Set<Value<E>> wrappedElements) {
    return ValueCollections.setOf(wrappedElements);
  }

  /**
   * Maps each item through {@code rawMapper} and wraps the result as {@link #of(Object)} does; a null source or mapper
   * answers an {@link ErrorValue}, and an exception from the mapper propagates. The set keeps no element order.
   *
   * @throws IllegalArgumentException for a mapped value that is a raw {@code List}, {@code Set} or {@code Map}
   */
  static <T, E> Value<Set<Value<E>>> setOf(Iterable<T> source, Function<T, E> rawMapper) {
    return ValueCollections.setOf(source, rawMapper);
  }

  static <E> Value<Set<Value<E>>> emptySet() {
    return ValueCollections.emptySet();
  }

  /**
   * Wraps each entry value as {@link #of(Object)} does and keeps the entries' order; a later entry with an equal key
   * replaces the earlier one. A null array, entry or key answers an {@link ErrorValue}.
   *
   * @throws IllegalArgumentException for a value that is a raw {@code List}, {@code Set} or {@code Map}
   */
  @SafeVarargs
  static <K, V> Value<Map<K, Value<V>>> mapOf(Map.Entry<K, V>... rawEntries) {
    return ValueCollections.mapOf(rawEntries);
  }

  /** Copies the map in its iteration order; a null map, key or value answers an {@link ErrorValue}. */
  static <K, V> Value<Map<K, Value<V>>> mapOf(Map<K, Value<V>> wrappedEntries) {
    return ValueCollections.mapOf(wrappedEntries);
  }

  /**
   * Keys and wraps each item as {@code mapOf(Map.Entry...)} does, in source order; a null argument or key answers an
   * {@link ErrorValue}, and an exception from a function propagates.
   *
   * @throws IllegalArgumentException for a value that is a raw {@code List}, {@code Set} or {@code Map}
   */
  static <T, K, V> Value<Map<K, Value<V>>> mapOf(Iterable<T> source, Function<T, K> keyFn, Function<T, V> rawValueFn) {
    return ValueCollections.mapOf(source, keyFn, rawValueFn);
  }

  static <K, V> Value<Map<K, Value<V>>> emptyMap() {
    return ValueCollections.emptyMap();
  }

  /**
   * Wraps each entry value as {@link #of(Object)} does, in natural key order; a later entry with an equal key replaces
   * the earlier one. A null array, entry or key answers an {@link ErrorValue}.
   *
   * @throws IllegalArgumentException for a value that is a raw {@code List}, {@code Set} or {@code Map}
   */
  @SafeVarargs
  static <K extends Comparable<K>, V> Value<SortedMap<K, Value<V>>> sortedMapOf(Map.Entry<K, V>... rawEntries) {
    return ValueCollections.sortedMapOf(rawEntries);
  }

  /**
   * Copies the entries into natural key order, dropping the source's comparator; a null map, key or value answers an
   * {@link ErrorValue}.
   *
   * @throws ClassCastException for a key that is not {@link Comparable}
   */
  static <K, V> Value<SortedMap<K, Value<V>>> sortedMapOf(SortedMap<K, Value<V>> wrappedEntries) {
    return ValueCollections.sortedMapOf(wrappedEntries);
  }

  /**
   * Keys and wraps each item as {@code sortedMapOf(Map.Entry...)} does; a null argument or key answers an
   * {@link ErrorValue}, and an exception from a function propagates.
   *
   * @throws IllegalArgumentException for a value that is a raw {@code List}, {@code Set} or {@code Map}
   */
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

  /**
   * A list whose element descriptor is {@code elementType}, so mixed element types write without an explicit
   * descriptor; a null descriptor, array or element answers an {@link ErrorValue}.
   */
  @SafeVarargs
  static <E> Value<List<Value<E>>> listOfTyped(TypeDescriptor<E> elementType, Value<? extends E>... elements) {
    return ValueCollections.listOfTyped(elementType, elements);
  }

  /**
   * A set whose element descriptor is {@code elementType}, so mixed element types write without an explicit descriptor;
   * a null descriptor, array or element answers an {@link ErrorValue}. The set keeps no element order.
   */
  @SafeVarargs
  static <E> Value<Set<Value<E>>> setOfTyped(TypeDescriptor<E> elementType, Value<? extends E>... elements) {
    return ValueCollections.setOfTyped(elementType, elements);
  }

  /**
   * A map in the entries' iteration order whose value descriptor is {@code valueType}; a null descriptor, map, key or
   * value answers an {@link ErrorValue}.
   */
  static <K, V> Value<Map<K, Value<V>>> mapOfTyped(
    TypeDescriptor<V> valueType,
    Map<K, ? extends Value<? extends V>> entries
  ) {
    return ValueCollections.mapOfTyped(valueType, entries);
  }

  /**
   * A map in natural key order whose value descriptor is {@code valueType}; a null descriptor, map, key or value
   * answers an {@link ErrorValue}.
   */
  static <K extends Comparable<K>, V> Value<SortedMap<K, Value<V>>> sortedMapOfTyped(
    TypeDescriptor<V> valueType,
    Map<K, ? extends Value<? extends V>> entries
  ) {
    return ValueCollections.sortedMapOfTyped(valueType, entries);
  }

  /**
   * Parses a strict RFC 8259 JSON text into a JSON document; a JSON {@code null} root answers {@link #nullValue()}.
   * Text that is not exactly one JSON value, or a null text, answers an {@link ErrorValue} naming the error, its line
   * and column.
   */
  static Value<JsonValue> parseJson(String text) {
    return ValueJson.parse(text);
  }

  /**
   * Builds a JSON object holding the members in the map's iteration order; {@link #nullValue()} is a JSON
   * {@code null} member. A null map, a null name, a null member, another state marker or a {@link BoxedValue} answers an
   * {@link ErrorValue}.
   */
  static Value<JsonValue> jsonOf(Map<String, ? extends Value<JsonValue>> members) {
    return ValueJson.object(members);
  }

  /**
   * Builds a JSON array holding the elements in list order; {@link #nullValue()} is a JSON {@code null} element. A
   * null list, a null element, another state marker or a {@link BoxedValue} answers an {@link ErrorValue}.
   */
  static Value<JsonValue> jsonOf(List<? extends Value<JsonValue>> elements) {
    return ValueJson.array(elements);
  }

  /**
   * Builds a JSON string holding the text unchanged, never parsed: {@code jsonOf("1815")} is the string
   * {@code "1815"}. A null text answers an {@link ErrorValue}.
   */
  static Value<JsonValue> jsonOf(String text) {
    return ValueJson.string(text);
  }

  /** Builds a JSON number whose text is {@link Long#toString(long)} of the number. */
  static Value<JsonValue> jsonOf(long number) {
    return ValueJson.number(number);
  }

  /**
   * Builds a JSON number whose text is {@link Double#toString(double)} of the number; NaN and the infinities answer an
   * {@link ErrorValue}.
   */
  static Value<JsonValue> jsonOf(double number) {
    return ValueJson.number(number);
  }

  /** Builds a JSON number whose text is the number's decimal digits; a null number answers an {@link ErrorValue}. */
  static Value<JsonValue> jsonOf(BigInteger number) {
    return ValueJson.number(number);
  }

  /**
   * Builds a JSON number whose text is {@link BigDecimal#toString()} of the number, so its scale is kept; a null
   * number answers an {@link ErrorValue}.
   */
  static Value<JsonValue> jsonOf(BigDecimal number) {
    return ValueJson.number(number);
  }

  static Value<JsonValue> jsonOf(boolean value) {
    return ValueJson.bool(value);
  }

  /** Builds a JSON string holding the one character, never its code as a number. */
  static Value<JsonValue> jsonOf(char character) {
    return ValueJson.string(character);
  }

  static Value<UUID> randomUuid() {
    return ValueFactories.randomUuid();
  }

  /**
   * Answers a navigator over this value. A JSON document steps through its members and elements; every other value
   * answers one whose exit is the value itself and whose steps answer an {@link UnsupportedValue}, while a state
   * marker passes every step unchanged.
   */
  default Navigator navigate() {
    return new IdentityNavigator(this);
  }

  /**
   * Converts this value to {@code target} through {@link Converter#standard()}. Beside the scalar conversions, a
   * record converts into a structure type that declares a {@code public static final SLOTS} {@link SlotShape}, and
   * such a structure converts into a record, each component filled from the same-named slot. A type pair no standard
   * conversion covers, or a record component structural conversion cannot carry, answers an {@link UnsupportedValue};
   * a payload this conversion refuses, because it would lose content or a slot is missing or cannot fill its
   * component, answers an {@link ErrorValue}. Both name source type, target type and reason.
   *
   * @throws IllegalArgumentException if {@code target} is {@code null}
   */
  default <R> Value<R> convertTo(Class<R> target) {
    return Converter.standard().convert(this, target);
  }

  /**
   * The {@link TypeDescriptor} of this value's payload, or {@code null} when none can be inferred: a state marker, a
   * {@link BoxedValue} of a class with no discoverable descriptor, an empty collection, or a collection with mixed
   * element types. A {@code null} means a write needs an explicit descriptor.
   */
  TypeDescriptor<T> valueType();

  /**
   * Returns the payload: the JDK value of a built-in, the backing collection of a collection wrapper (elements still
   * boxed), the inner object of a {@link BoxedValue}, or the {@link CoreValue} itself.
   *
   * @throws IllegalStateException on a state marker
   */
  T unbox();
}
