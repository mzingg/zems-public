package dev.zems.lib.value.convert;

import dev.zems.lib.value.TypeName;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.Version;
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
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

/** The lossless conversion table every {@link Converter#standard() standard converter} starts from. */
final class StandardConversions {

  private static final List<Class<?>> CANONICAL_FORM_TYPES = List.of(
    Boolean.class,
    Character.class,
    Integer.class,
    Long.class,
    Short.class,
    Byte.class,
    BigInteger.class,
    Double.class,
    Float.class,
    BigDecimal.class,
    UUID.class,
    URI.class,
    Instant.class,
    LocalDate.class,
    LocalDateTime.class,
    ZonedDateTime.class,
    OffsetDateTime.class,
    LocalTime.class,
    Year.class,
    YearMonth.class,
    ZoneId.class,
    Duration.class,
    Period.class,
    Locale.class,
    Currency.class,
    InetAddress.class,
    TypeName.class,
    Version.class,
    Enum.class
  );

  private static final List<Class<? extends Number>> NUMERIC_TYPES = List.of(
    Byte.class,
    Short.class,
    Integer.class,
    Long.class,
    BigInteger.class,
    Float.class,
    Double.class,
    BigDecimal.class
  );

  private static final List<Conversion<?, ?>> TABLE = build();

  private StandardConversions() {}

  static List<Conversion<?, ?>> table() {
    return TABLE;
  }

  private static List<Conversion<?, ?>> build() {
    List<Conversion<?, ?>> conversions = new ArrayList<>();
    addStringParses(conversions);
    CANONICAL_FORM_TYPES.forEach(type -> conversions.add(stringForm(type)));
    for (Class<? extends Number> source : NUMERIC_TYPES) {
      for (Class<? extends Number> target : NUMERIC_TYPES) {
        if (source != target) {
          conversions.add(numeric(source, target));
        }
      }
    }
    conversions.addAll(JsonNumbers.rows());
    return List.copyOf(conversions);
  }

  private static void addStringParses(List<Conversion<?, ?>> conversions) {
    conversions.add(bySymbol(Boolean.class, "bool"));
    conversions.add(bySymbol(Character.class, "char"));
    conversions.add(bySymbol(Integer.class, "int"));
    conversions.add(bySymbol(Long.class, "long"));
    conversions.add(bySymbol(Short.class, "short"));
    conversions.add(bySymbol(Byte.class, "byte"));
    conversions.add(bySymbol(Double.class, "double"));
    conversions.add(bySymbol(Float.class, "float"));

    conversions.add(parsed(BigInteger.class, Value::bigIntegerOf));
    conversions.add(parsed(BigDecimal.class, Value::bigDecimalOf));
    conversions.add(parsed(UUID.class, Value::uuidOf));
    conversions.add(parsed(URI.class, Value::urlOf));
    conversions.add(parsed(Instant.class, Value::timeInstantOf));
    conversions.add(parsed(LocalDate.class, Value::localDateOf));
    conversions.add(parsed(LocalDateTime.class, Value::localDateTimeOf));
    conversions.add(parsed(ZonedDateTime.class, Value::zonedDateTimeOf));
    conversions.add(parsed(OffsetDateTime.class, Value::offsetDateTimeOf));
    conversions.add(parsed(LocalTime.class, Value::localTimeOf));
    conversions.add(parsed(Year.class, Value::yearOf));
    conversions.add(parsed(YearMonth.class, Value::yearMonthOf));
    conversions.add(parsed(ZoneId.class, Value::zoneIdOf));
    conversions.add(parsed(Duration.class, Value::durationOf));
    conversions.add(parsed(Period.class, Value::periodOf));
    conversions.add(parsed(Locale.class, Value::localeOf));
    conversions.add(parsed(Currency.class, Value::currencyOf));
    conversions.add(parsed(InetAddress.class, Value::inetAddressOf));
    conversions.add(parsed(TypeName.class, Value::typeOf));
  }

  private static <T> Conversion<String, T> parsed(Class<T> targetType, Function<String, Value<T>> parser) {
    return Conversion.of(String.class, targetType, text ->
      ConversionErrors.reframe(String.class, targetType, parser.apply(text))
    );
  }

  private static <T> Conversion<String, T> bySymbol(Class<T> targetType, String symbol) {
    return parsed(targetType, text -> narrow(Value.fromSymbol(symbol, text)));
  }

  private static <S> Conversion<S, String> stringForm(Class<S> sourceType) {
    return Conversion.of(sourceType, String.class, source -> Value.of(canonicalForm(source)));
  }

  private static String canonicalForm(Object source) {
    return switch (source) {
      case Locale locale -> locale.toLanguageTag();
      case Currency currency -> currency.getCurrencyCode();
      case InetAddress address -> address.getHostAddress();
      // name(), never toString(): an enum may override toString()
      case Enum<?> constant -> constant.name();
      // text(), never toString(): the dialect-qualified form is the wire form, not the canonical string
      case Version version -> version.text();
      default -> source.toString();
    };
  }

  private static <S extends Number, T extends Number> Conversion<S, T> numeric(
    Class<S> sourceType,
    Class<T> targetType
  ) {
    return Conversion.of(sourceType, targetType, source -> Numerics.exact(source, targetType));
  }

  @SuppressWarnings("unchecked")
  private static <T> Value<T> narrow(Value<?> value) {
    return (Value<T>) value;
  }
}
