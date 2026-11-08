package dev.zems.lib.value.builtin;

import dev.zems.lib.value.Value;

/**
 * Sealed sub-hierarchy of the wrappers for JDK types, plus {@link TypeValue}, {@link VersionValue},
 * {@link MeasurableValue} and {@link EnumValue}.
 *
 * <p>
 * Construct through the {@link Value} factories, never through {@code new XxxValue(...)}: the canonical constructors
 * are public only because records require it, and calling one bypasses cache interning without any runtime check.
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
    TypeValue,
    VersionValue,
    EnumValue,
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
    SortedMapValue,
    MeasurableValue {}
