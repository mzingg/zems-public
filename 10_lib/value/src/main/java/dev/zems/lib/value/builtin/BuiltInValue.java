package dev.zems.lib.value.builtin;

import dev.zems.lib.value.Value;

/**
 * Sealed sub-hierarchy for value types that wrap a JDK class.
 *
 * <p>
 * Two flavours coexist:
 * <ul>
 * <li>JDK reference types with a canonical, lossless string form (ISO-8601 dates, BCP 47 locales,
 * ISO 4217 currencies, RFC 4122 UUIDs, decimal-formatted big numbers, etc.) — share the
 * {@code of(JdkType)} / {@code of(String)} / {@code parse(String)} factory shape, deeply
 * immutable instances, and round-trip via the matching descriptor in
 * {@link dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors}.
 * <li>Primitive wrappers ({@link StringValue}, {@link BooleanValue}, {@link IntegerValue},
 * {@link LongValue}, {@link DoubleValue}, {@link FloatValue}, {@link ShortValue},
 * {@link ByteValue}, {@link ListValue}, {@link MapValue}) for fundamental Java types that don't
 * need parsing.
 * </ul>
 *
 * <p>
 * <b>Direct invocation of the canonical constructor is unsupported.</b> Records require a public
 * canonical constructor, but production code must construct values via {@link Value} factories
 * (e.g. {@link Value#of(Object)}, {@link Value#listOf(Object...)}). Calling
 * {@code new XxxValue(...)} directly bypasses {@code ValueCache} interning and may produce
 * wrappers that violate library invariants — this is not detected at runtime. Tests in the same
 * package may call directly when exercising the wrapper itself.
 *
 * @param <T> the JDK type this wrapper holds
 */
public sealed interface BuiltInValue<T>
  extends Value<T>
  permits
    TimeInstantValue,
    LocalDateValue,
    LocalDateTimeValue,
    ZonedDateTimeValue,
    OffsetDateTimeValue,
    LocalTimeValue,
    YearValue,
    YearMonthValue,
    ZoneIdValue,
    DurationValue,
    PeriodValue,
    BigIntegerValue,
    BigDecimalValue,
    UuidValue,
    UrlValue,
    LocaleValue,
    CurrencyValue,
    InetAddressValue,
    StringValue,
    BooleanValue,
    CharacterValue,
    IntegerValue,
    LongValue,
    DoubleValue,
    FloatValue,
    ShortValue,
    ByteValue,
    ListValue,
    SetValue,
    MapValue,
    SortedMapValue {}
