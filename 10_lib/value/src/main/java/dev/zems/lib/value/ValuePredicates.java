package dev.zems.lib.value;

import dev.zems.lib.value.builtin.BigDecimalValue;
import dev.zems.lib.value.builtin.BigIntegerValue;
import dev.zems.lib.value.builtin.BooleanValue;
import dev.zems.lib.value.builtin.BuiltInValue;
import dev.zems.lib.value.builtin.ByteValue;
import dev.zems.lib.value.builtin.CharacterValue;
import dev.zems.lib.value.builtin.CurrencyValue;
import dev.zems.lib.value.builtin.DoubleValue;
import dev.zems.lib.value.builtin.DurationValue;
import dev.zems.lib.value.builtin.EnumValue;
import dev.zems.lib.value.builtin.FloatValue;
import dev.zems.lib.value.builtin.InetAddressValue;
import dev.zems.lib.value.builtin.IntegerValue;
import dev.zems.lib.value.builtin.ListValue;
import dev.zems.lib.value.builtin.LocalDateTimeValue;
import dev.zems.lib.value.builtin.LocalDateValue;
import dev.zems.lib.value.builtin.LocalTimeValue;
import dev.zems.lib.value.builtin.LocaleValue;
import dev.zems.lib.value.builtin.LongValue;
import dev.zems.lib.value.builtin.MapValue;
import dev.zems.lib.value.builtin.MeasurableValue;
import dev.zems.lib.value.builtin.OffsetDateTimeValue;
import dev.zems.lib.value.builtin.PeriodValue;
import dev.zems.lib.value.builtin.SetValue;
import dev.zems.lib.value.builtin.ShortValue;
import dev.zems.lib.value.builtin.SortedMapValue;
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

/** Type-check facet of {@link Value}: the {@code is...()} predicates over the built-in subtypes. */
public interface ValuePredicates {
  private static boolean notWrapsType(Value<?> v, Class<?> type) {
    if (v.isList() || v.isSet() || v.isMap() || v.isSortedMap()) {
      return true;
    }
    return !(v instanceof BuiltInValue<?> b) || !type.isInstance(b.unbox());
  }

  default boolean isString() {
    return this instanceof StringValue;
  }

  default boolean isBoolean() {
    return this instanceof BooleanValue;
  }

  default boolean isCharacter() {
    return this instanceof CharacterValue;
  }

  /** True for the six primitive number wrappers; a {@code BigInteger} or {@code BigDecimal} answers {@code false}. */
  default boolean isNumber() {
    return (
      this instanceof IntegerValue ||
      this instanceof LongValue ||
      this instanceof DoubleValue ||
      this instanceof FloatValue ||
      this instanceof ShortValue ||
      this instanceof ByteValue
    );
  }

  default boolean isList() {
    return this instanceof ListValue<?>;
  }

  default boolean isSet() {
    return this instanceof SetValue<?>;
  }

  default boolean isMap() {
    return this instanceof MapValue<?, ?>;
  }

  default boolean isSortedMap() {
    return this instanceof SortedMapValue<?, ?>;
  }

  default boolean isUrl() {
    return this instanceof UrlValue;
  }

  default boolean isUuid() {
    return this instanceof UuidValue;
  }

  default boolean isTimeInstant() {
    return this instanceof TimeInstantValue;
  }

  default boolean isLocalDate() {
    return this instanceof LocalDateValue;
  }

  default boolean isLocalDateTime() {
    return this instanceof LocalDateTimeValue;
  }

  default boolean isZonedDateTime() {
    return this instanceof ZonedDateTimeValue;
  }

  default boolean isOffsetDateTime() {
    return this instanceof OffsetDateTimeValue;
  }

  default boolean isLocalTime() {
    return this instanceof LocalTimeValue;
  }

  default boolean isYear() {
    return this instanceof YearValue;
  }

  default boolean isYearMonth() {
    return this instanceof YearMonthValue;
  }

  default boolean isZoneId() {
    return this instanceof ZoneIdValue;
  }

  default boolean isDuration() {
    return this instanceof DurationValue;
  }

  default boolean isPeriod() {
    return this instanceof PeriodValue;
  }

  default boolean isBigInteger() {
    return this instanceof BigIntegerValue;
  }

  default boolean isBigDecimal() {
    return this instanceof BigDecimalValue;
  }

  default boolean isLocale() {
    return this instanceof LocaleValue;
  }

  default boolean isCurrency() {
    return this instanceof CurrencyValue;
  }

  default boolean isInetAddress() {
    return this instanceof InetAddressValue;
  }

  default boolean isTypeName() {
    return this instanceof TypeValue;
  }

  default boolean isVersion() {
    return this instanceof VersionValue;
  }

  default boolean isQuantity() {
    return this instanceof MeasurableValue;
  }

  default boolean isEnum() {
    return this instanceof EnumValue<?>;
  }

  /**
   * True for a {@link ListValue} whose elements are all built-in scalars wrapping {@code elementType}. An empty list
   * answers {@code true} for every type, and a collection element never matches.
   */
  default boolean isListOf(Class<?> elementType) {
    if (!(this instanceof ListValue<?> lv)) {
      return false;
    }
    for (var element : lv.elements()) {
      if (notWrapsType(element, elementType)) {
        return false;
      }
    }
    return true;
  }

  /**
   * True for a {@link SetValue} whose elements are all built-in scalars wrapping {@code elementType}. An empty set
   * answers {@code true} for every type, and a collection element never matches.
   */
  default boolean isSetOf(Class<?> elementType) {
    if (!(this instanceof SetValue<?> sv)) {
      return false;
    }
    for (var element : sv.elements()) {
      if (notWrapsType(element, elementType)) {
        return false;
      }
    }
    return true;
  }

  /**
   * True for a {@link MapValue} whose keys are all instances of {@code keyType} and whose values are all built-in
   * scalars wrapping {@code valueType}. An empty map answers {@code true} for every pair of types.
   */
  default boolean isMapOf(Class<?> keyType, Class<?> valueType) {
    if (!(this instanceof MapValue<?, ?> mv)) {
      return false;
    }
    for (var entry : mv.entries().entrySet()) {
      if (!keyType.isInstance(entry.getKey())) {
        return false;
      }
      if (notWrapsType(entry.getValue(), valueType)) {
        return false;
      }
    }
    return true;
  }

  /**
   * True for a {@link SortedMapValue} whose keys are all instances of {@code keyType} and whose values are all
   * built-in scalars wrapping {@code valueType}. An empty map answers {@code true} for every pair of types.
   */
  default boolean isSortedMapOf(Class<?> keyType, Class<?> valueType) {
    if (!(this instanceof SortedMapValue<?, ?> sm)) {
      return false;
    }
    for (var entry : sm.entries().entrySet()) {
      if (!keyType.isInstance(entry.getKey())) {
        return false;
      }
      if (notWrapsType(entry.getValue(), valueType)) {
        return false;
      }
    }
    return true;
  }
}
