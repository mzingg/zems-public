package dev.zems.lib.value;

import dev.zems.lib.value.builtin.BigDecimalValue;
import dev.zems.lib.value.builtin.BigIntegerValue;
import dev.zems.lib.value.builtin.CharacterValue;
import dev.zems.lib.value.builtin.CurrencyValue;
import dev.zems.lib.value.builtin.DurationValue;
import dev.zems.lib.value.builtin.InetAddressValue;
import dev.zems.lib.value.builtin.ListValue;
import dev.zems.lib.value.builtin.LocalDateTimeValue;
import dev.zems.lib.value.builtin.LocalDateValue;
import dev.zems.lib.value.builtin.LocalTimeValue;
import dev.zems.lib.value.builtin.LocaleValue;
import dev.zems.lib.value.builtin.MapValue;
import dev.zems.lib.value.builtin.OffsetDateTimeValue;
import dev.zems.lib.value.builtin.PeriodValue;
import dev.zems.lib.value.builtin.SetValue;
import dev.zems.lib.value.builtin.SortedMapValue;
import dev.zems.lib.value.builtin.TimeInstantValue;
import dev.zems.lib.value.builtin.UrlValue;
import dev.zems.lib.value.builtin.UuidValue;
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
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.UUID;

/**
 * Typed-accessor facet of {@link Value}: the {@code Optional<JdkType>} getters that return the wrapped JDK value when
 * this is the expected subtype (and empty otherwise), plus collection unwrappers {@link #asList}, {@link #asSet},
 * {@link #asMap}.
 *
 * <p>
 * {@code Value<T>} extends this interface so callers see these methods directly on {@code Value}; this type exists
 * purely to keep the {@link Value} source readable.
 *
 * <p>
 * Note: {@code asNumber()} (polymorphic across the six primitive numeric wrappers) lives on {@link ValueConversions} so
 * {@code asInteger}/{@code asDouble} can call it without an interface dependency. {@code Value} inherits it via
 * {@code ValueConversions}.
 */
public interface ValueAccessors {
  default Optional<Character> asCharacter() {
    return this instanceof CharacterValue(char character) ? Optional.of(character) : Optional.empty();
  }

  default Optional<UUID> asUuid() {
    return this instanceof UuidValue(UUID uuid) ? Optional.of(uuid) : Optional.empty();
  }

  default Optional<URI> asUrl() {
    return this instanceof UrlValue(URI uri) ? Optional.of(uri) : Optional.empty();
  }

  default Optional<Instant> asTimeInstant() {
    return this instanceof TimeInstantValue(Instant instant) ? Optional.of(instant) : Optional.empty();
  }

  default Optional<LocalDate> asLocalDate() {
    return this instanceof LocalDateValue(LocalDate localDate) ? Optional.of(localDate) : Optional.empty();
  }

  default Optional<LocalDateTime> asLocalDateTime() {
    return this instanceof LocalDateTimeValue(LocalDateTime localDateTime)
      ? Optional.of(localDateTime)
      : Optional.empty();
  }

  default Optional<ZonedDateTime> asZonedDateTime() {
    return this instanceof ZonedDateTimeValue(ZonedDateTime zonedDateTime)
      ? Optional.of(zonedDateTime)
      : Optional.empty();
  }

  default Optional<OffsetDateTime> asOffsetDateTime() {
    return this instanceof OffsetDateTimeValue(OffsetDateTime offsetDateTime)
      ? Optional.of(offsetDateTime)
      : Optional.empty();
  }

  default Optional<LocalTime> asLocalTime() {
    return this instanceof LocalTimeValue(LocalTime localTime) ? Optional.of(localTime) : Optional.empty();
  }

  default Optional<Year> asYear() {
    return this instanceof YearValue(Year year) ? Optional.of(year) : Optional.empty();
  }

  default Optional<YearMonth> asYearMonth() {
    return this instanceof YearMonthValue(YearMonth yearMonth) ? Optional.of(yearMonth) : Optional.empty();
  }

  default Optional<ZoneId> asZoneId() {
    return this instanceof ZoneIdValue(ZoneId zoneId) ? Optional.of(zoneId) : Optional.empty();
  }

  default Optional<Duration> asDuration() {
    return this instanceof DurationValue(Duration duration) ? Optional.of(duration) : Optional.empty();
  }

  default Optional<Period> asPeriod() {
    return this instanceof PeriodValue(Period period) ? Optional.of(period) : Optional.empty();
  }

  default Optional<BigInteger> asBigInteger() {
    return this instanceof BigIntegerValue(BigInteger bigInteger) ? Optional.of(bigInteger) : Optional.empty();
  }

  default Optional<BigDecimal> asBigDecimal() {
    return this instanceof BigDecimalValue(BigDecimal bigDecimal) ? Optional.of(bigDecimal) : Optional.empty();
  }

  default Optional<Locale> asLocale() {
    return this instanceof LocaleValue(Locale locale) ? Optional.of(locale) : Optional.empty();
  }

  default Optional<Currency> asCurrency() {
    return this instanceof CurrencyValue(Currency currency) ? Optional.of(currency) : Optional.empty();
  }

  default Optional<InetAddress> asInetAddress() {
    return this instanceof InetAddressValue(InetAddress inetAddress) ? Optional.of(inetAddress) : Optional.empty();
  }

  default Optional<List<? extends Value<?>>> asList() {
    if (this instanceof ListValue<?> lv) {
      List<? extends Value<?>> elements = lv.elements();
      return Optional.of(elements);
    }
    return Optional.empty();
  }

  default Optional<Set<? extends Value<?>>> asSet() {
    if (this instanceof SetValue<?> sv) {
      Set<? extends Value<?>> elements = sv.elements();
      return Optional.of(elements);
    }
    return Optional.empty();
  }

  default Optional<Map<?, ? extends Value<?>>> asMap() {
    if (this instanceof MapValue<?, ?> mv) {
      Map<?, ? extends Value<?>> entries = mv.entries();
      return Optional.of(entries);
    }
    return Optional.empty();
  }

  default Optional<SortedMap<?, ? extends Value<?>>> asSortedMap() {
    if (this instanceof SortedMapValue<?, ?> sm) {
      SortedMap<?, ? extends Value<?>> entries = sm.entries();
      return Optional.of(entries);
    }
    return Optional.empty();
  }
}
