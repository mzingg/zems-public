package dev.zems.lib.value;

import dev.zems.lib.value.builtin.BigDecimalValue;
import dev.zems.lib.value.builtin.BigIntegerValue;
import dev.zems.lib.value.builtin.CharacterValue;
import dev.zems.lib.value.builtin.CurrencyValue;
import dev.zems.lib.value.builtin.DoubleValue;
import dev.zems.lib.value.builtin.DurationValue;
import dev.zems.lib.value.builtin.FloatValue;
import dev.zems.lib.value.builtin.InetAddressValue;
import dev.zems.lib.value.builtin.IntegerValue;
import dev.zems.lib.value.builtin.LocalDateTimeValue;
import dev.zems.lib.value.builtin.LocalDateValue;
import dev.zems.lib.value.builtin.LocalTimeValue;
import dev.zems.lib.value.builtin.LocaleValue;
import dev.zems.lib.value.builtin.LongValue;
import dev.zems.lib.value.builtin.OffsetDateTimeValue;
import dev.zems.lib.value.builtin.PeriodValue;
import dev.zems.lib.value.builtin.StringValue;
import dev.zems.lib.value.builtin.TimeInstantValue;
import dev.zems.lib.value.builtin.UrlValue;
import dev.zems.lib.value.builtin.UuidValue;
import dev.zems.lib.value.builtin.YearMonthValue;
import dev.zems.lib.value.builtin.YearValue;
import dev.zems.lib.value.builtin.ZoneIdValue;
import dev.zems.lib.value.builtin.ZonedDateTimeValue;
import dev.zems.lib.value.cache.ValueCache;
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

/**
 * Package-private factory implementations for {@code Value.of(...)} — the generic dispatch over arbitrary {@code T}
 * plus the 23 typed {@code of(JdkType)} overloads. Reachable through {@code Value.*} delegates only.
 */
final class ValueFactories {

  private ValueFactories() {}

  // Canonical precondition message for a null typed-factory argument (see CLAUDE.md "Null and error behaviour").
  private static String mustNotBeNull(String name) {
    return name + " must not be null";
  }

  @SuppressWarnings("unchecked")
  static <T> Value<T> of(T raw) {
    if (raw == null) {
      return Value.nullValue();
    }
    if (raw instanceof Value<?> v) {
      return (Value<T>) v;
    }
    return (Value<T>) switch (raw) {
      case String s -> of(s);
      case Boolean b -> of(b);
      case Character c -> of(c);
      case Number n -> of(n);
      case UUID u -> of(u);
      case URI u -> of(u);
      case Instant i -> of(i);
      case LocalDate d -> of(d);
      case LocalDateTime d -> of(d);
      case ZonedDateTime d -> of(d);
      case OffsetDateTime d -> of(d);
      case LocalTime t -> of(t);
      case Year y -> of(y);
      case YearMonth y -> of(y);
      case ZoneId z -> of(z);
      case Duration d -> of(d);
      case Period p -> of(p);
      case Locale l -> of(l);
      case Currency c -> of(c);
      case InetAddress a -> of(a);
      case List<?> _ -> throw new IllegalArgumentException(
        "Value.of(...) does not accept List - use Value.listOf(...) instead"
      );
      case Set<?> _ -> throw new IllegalArgumentException(
        "Value.of(...) does not accept Set - use Value.setOf(...) instead"
      );
      // SortedMap is a sub-interface of Map; check it first so the diagnostic points at the right factory.
      case SortedMap<?, ?> _ -> throw new IllegalArgumentException(
        "Value.of(...) does not accept SortedMap - use Value.sortedMapOf(...) instead"
      );
      case Map<?, ?> _ -> throw new IllegalArgumentException(
        "Value.of(...) does not accept Map - use Value.mapOf(...) instead"
      );
      default -> new BoxedValue<>(raw);
    };
  }

  // -- Primitives --

  static Value<String> of(String value) {
    if (value == null) {
      return Value.errorMessage(mustNotBeNull("String"), String.class);
    }
    var hit = ValueCache.INSTANCE.cached(value);
    return hit != null ? hit : new StringValue(value);
  }

  static Value<Boolean> of(Boolean value) {
    if (value == null) {
      return Value.errorMessage(mustNotBeNull("Boolean"), Boolean.class);
    }
    return ValueCache.INSTANCE.cached(value);
  }

  static Value<Character> of(Character value) {
    if (value == null) {
      return Value.errorMessage(mustNotBeNull("Character"), Character.class);
    }
    return new CharacterValue(value);
  }

  @SuppressWarnings("unchecked")
  static <N extends Number> Value<N> of(N value) {
    if (value == null) {
      return Value.errorMessage(mustNotBeNull("Number"), (Class<N>) Number.class);
    }
    return (Value<N>) switch (value) {
      case Integer i -> {
        var hit = ValueCache.INSTANCE.cached(i);
        yield hit != null ? hit : new IntegerValue(i);
      }
      case Long l -> {
        var hit = ValueCache.INSTANCE.cached(l);
        yield hit != null ? hit : new LongValue(l);
      }
      case Double d -> new DoubleValue(d);
      case Float f -> new FloatValue(f);
      case Short s -> ValueCache.INSTANCE.cached(s);
      case Byte b -> ValueCache.INSTANCE.cached(b);
      case BigInteger bi -> new BigIntegerValue(bi);
      case BigDecimal bd -> new BigDecimalValue(bd);
      default -> Value.errorMessage("Unsupported Number type: " + value.getClass().getName(), (Class<N>) Number.class);
    };
  }

  // -- BuiltIn JDK reference types --

  static Value<UUID> of(UUID uuid) {
    if (uuid == null) {
      return Value.errorMessage(mustNotBeNull("UUID"), UUID.class);
    }
    return new UuidValue(uuid);
  }

  static Value<URI> of(URI uri) {
    if (uri == null) {
      return Value.errorMessage(mustNotBeNull("URI"), URI.class);
    }
    return new UrlValue(uri);
  }

  static Value<Instant> of(Instant instant) {
    if (instant == null) {
      return Value.errorMessage(mustNotBeNull("Instant"), Instant.class);
    }
    return new TimeInstantValue(instant);
  }

  static Value<LocalDate> of(LocalDate value) {
    if (value == null) {
      return Value.errorMessage(mustNotBeNull("LocalDate"), LocalDate.class);
    }
    return new LocalDateValue(value);
  }

  static Value<LocalDateTime> of(LocalDateTime value) {
    if (value == null) {
      return Value.errorMessage(mustNotBeNull("LocalDateTime"), LocalDateTime.class);
    }
    return new LocalDateTimeValue(value);
  }

  static Value<ZonedDateTime> of(ZonedDateTime value) {
    if (value == null) {
      return Value.errorMessage(mustNotBeNull("ZonedDateTime"), ZonedDateTime.class);
    }
    return new ZonedDateTimeValue(value);
  }

  static Value<OffsetDateTime> of(OffsetDateTime value) {
    if (value == null) {
      return Value.errorMessage(mustNotBeNull("OffsetDateTime"), OffsetDateTime.class);
    }
    return new OffsetDateTimeValue(value);
  }

  static Value<LocalTime> of(LocalTime value) {
    if (value == null) {
      return Value.errorMessage(mustNotBeNull("LocalTime"), LocalTime.class);
    }
    return new LocalTimeValue(value);
  }

  static Value<Year> of(Year value) {
    if (value == null) {
      return Value.errorMessage(mustNotBeNull("Year"), Year.class);
    }
    return new YearValue(value);
  }

  static Value<YearMonth> of(YearMonth value) {
    if (value == null) {
      return Value.errorMessage(mustNotBeNull("YearMonth"), YearMonth.class);
    }
    return new YearMonthValue(value);
  }

  static Value<ZoneId> of(ZoneId value) {
    if (value == null) {
      return Value.errorMessage(mustNotBeNull("ZoneId"), ZoneId.class);
    }
    return new ZoneIdValue(value);
  }

  static Value<Duration> of(Duration value) {
    if (value == null) {
      return Value.errorMessage(mustNotBeNull("Duration"), Duration.class);
    }
    return new DurationValue(value);
  }

  static Value<Period> of(Period value) {
    if (value == null) {
      return Value.errorMessage(mustNotBeNull("Period"), Period.class);
    }
    return new PeriodValue(value);
  }

  static Value<Locale> of(Locale value) {
    if (value == null) {
      return Value.errorMessage(mustNotBeNull("Locale"), Locale.class);
    }
    if (LocaleValue.isUndetermined(value)) {
      return Value.errorMessage("Locale must not be the undetermined locale", Locale.class);
    }
    return new LocaleValue(value);
  }

  static Value<Currency> of(Currency value) {
    if (value == null) {
      return Value.errorMessage(mustNotBeNull("Currency"), Currency.class);
    }
    return new CurrencyValue(value);
  }

  static Value<InetAddress> of(InetAddress value) {
    if (value == null) {
      return Value.errorMessage(mustNotBeNull("InetAddress"), InetAddress.class);
    }
    return new InetAddressValue(value);
  }

  static Value<UUID> randomUuid() {
    return new UuidValue(UUID.randomUUID());
  }
}
