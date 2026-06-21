package dev.zems.lib.value;

import dev.zems.lib.value.builtin.BigDecimalValue;
import dev.zems.lib.value.builtin.BigIntegerValue;
import dev.zems.lib.value.builtin.CurrencyValue;
import dev.zems.lib.value.builtin.DurationValue;
import dev.zems.lib.value.builtin.InetAddressValue;
import dev.zems.lib.value.builtin.LocalDateTimeValue;
import dev.zems.lib.value.builtin.LocalDateValue;
import dev.zems.lib.value.builtin.LocalTimeValue;
import dev.zems.lib.value.builtin.LocaleValue;
import dev.zems.lib.value.builtin.OffsetDateTimeValue;
import dev.zems.lib.value.builtin.PeriodValue;
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
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.DateTimeException;
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
import java.time.format.DateTimeParseException;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;

/**
 * Package-private parser and cross-type-constructor implementations for {@code Value.<type>Of(...)} factories — string
 * parsers and convenience constructors that wrap arbitrary input into the matching built-in {@link Value} subtype,
 * returning an {@link ErrorValue} on null/blank/invalid input. Reachable through {@code Value.*} delegates only.
 */
final class ValueParsers {

  private ValueParsers() {}

  private static final String NOT_A_NUMBER = "Not a number: ";
  private static final String NOT_AN_INTEGER = " is not an integer (use !double or !bigdec)";

  // Canonical precondition messages for null / null-or-blank parser input (see CLAUDE.md "Null and error behaviour").
  private static String mustNotBeNull(String name) {
    return name + " must not be null";
  }

  private static String mustNotBeNullOrBlank(String name) {
    return name + " must not be null or empty";
  }

  // -- UUID --

  static Value<UUID> uuidOf(String s) {
    if (s == null || s.isBlank()) {
      return Value.errorMessage(mustNotBeNullOrBlank("UUID string"), UUID.class);
    }
    try {
      return new UuidValue(UUID.fromString(s));
    } catch (IllegalArgumentException e) {
      return Value.errorOf(e, UUID.class);
    }
  }

  static Value<UUID> uuidOf(long mostSigBits, long leastSigBits) {
    return new UuidValue(new UUID(mostSigBits, leastSigBits));
  }

  // -- URL --

  static Value<URI> urlOf(String s) {
    if (s == null || s.isBlank()) {
      return Value.errorMessage(mustNotBeNullOrBlank("URI string"), URI.class);
    }
    try {
      return new UrlValue(new URI(s));
    } catch (URISyntaxException e) {
      return Value.errorOf(e, URI.class);
    }
  }

  // -- Instant --

  static Value<Instant> timeInstantOf(String s) {
    if (s == null || s.isBlank()) {
      return Value.errorMessage(mustNotBeNullOrBlank("Instant string"), Instant.class);
    }
    try {
      return new TimeInstantValue(Instant.parse(s));
    } catch (DateTimeParseException e) {
      return Value.errorOf(e, Instant.class);
    }
  }

  static Value<Instant> timeInstantOfEpochSecond(long epochSecond) {
    try {
      return new TimeInstantValue(Instant.ofEpochSecond(epochSecond));
    } catch (DateTimeException e) {
      return Value.errorOf(e, Instant.class);
    }
  }

  static Value<Instant> timeInstantOfEpochSecond(long epochSecond, long nanoAdjustment) {
    try {
      return new TimeInstantValue(Instant.ofEpochSecond(epochSecond, nanoAdjustment));
    } catch (DateTimeException | ArithmeticException e) {
      return Value.errorOf(e, Instant.class);
    }
  }

  static Value<Instant> timeInstantOfEpochMilli(long epochMilli) {
    return new TimeInstantValue(Instant.ofEpochMilli(epochMilli));
  }

  // -- LocalDate --

  static Value<LocalDate> localDateOf(String s) {
    if (s == null || s.isBlank()) {
      return Value.errorMessage(mustNotBeNullOrBlank("LocalDate string"), LocalDate.class);
    }
    try {
      return new LocalDateValue(LocalDate.parse(s));
    } catch (DateTimeParseException e) {
      return Value.errorOf(e, LocalDate.class);
    }
  }

  static Value<LocalDate> localDateOf(int year, int month, int day) {
    try {
      return new LocalDateValue(LocalDate.of(year, month, day));
    } catch (DateTimeException e) {
      return Value.errorOf(e, LocalDate.class);
    }
  }

  static Value<LocalDate> localDateOfEpochDay(long epochDay) {
    try {
      return new LocalDateValue(LocalDate.ofEpochDay(epochDay));
    } catch (DateTimeException e) {
      return Value.errorOf(e, LocalDate.class);
    }
  }

  // -- LocalDateTime --

  static Value<LocalDateTime> localDateTimeOf(String s) {
    if (s == null || s.isBlank()) {
      return Value.errorMessage(mustNotBeNullOrBlank("LocalDateTime string"), LocalDateTime.class);
    }
    try {
      return new LocalDateTimeValue(LocalDateTime.parse(s));
    } catch (DateTimeParseException e) {
      return Value.errorOf(e, LocalDateTime.class);
    }
  }

  static Value<LocalDateTime> localDateTimeOf(LocalDate date, LocalTime time) {
    if (date == null || time == null) {
      return Value.errorMessage("LocalDate and LocalTime must not be null", LocalDateTime.class);
    }
    return new LocalDateTimeValue(LocalDateTime.of(date, time));
  }

  static Value<LocalDateTime> localDateTimeOf(int year, int month, int day, int hour, int minute) {
    try {
      return new LocalDateTimeValue(LocalDateTime.of(year, month, day, hour, minute));
    } catch (DateTimeException e) {
      return Value.errorOf(e, LocalDateTime.class);
    }
  }

  static Value<LocalDateTime> localDateTimeOf(int year, int month, int day, int hour, int minute, int second) {
    try {
      return new LocalDateTimeValue(LocalDateTime.of(year, month, day, hour, minute, second));
    } catch (DateTimeException e) {
      return Value.errorOf(e, LocalDateTime.class);
    }
  }

  // -- ZonedDateTime --

  static Value<ZonedDateTime> zonedDateTimeOf(String s) {
    if (s == null || s.isBlank()) {
      return Value.errorMessage(mustNotBeNullOrBlank("ZonedDateTime string"), ZonedDateTime.class);
    }
    try {
      return new ZonedDateTimeValue(ZonedDateTime.parse(s));
    } catch (DateTimeParseException e) {
      return Value.errorOf(e, ZonedDateTime.class);
    }
  }

  // -- OffsetDateTime --

  static Value<OffsetDateTime> offsetDateTimeOf(String s) {
    if (s == null || s.isBlank()) {
      return Value.errorMessage(mustNotBeNullOrBlank("OffsetDateTime string"), OffsetDateTime.class);
    }
    try {
      return new OffsetDateTimeValue(OffsetDateTime.parse(s));
    } catch (DateTimeParseException e) {
      return Value.errorOf(e, OffsetDateTime.class);
    }
  }

  // -- LocalTime --

  static Value<LocalTime> localTimeOf(String s) {
    if (s == null || s.isBlank()) {
      return Value.errorMessage(mustNotBeNullOrBlank("LocalTime string"), LocalTime.class);
    }
    try {
      return new LocalTimeValue(LocalTime.parse(s));
    } catch (DateTimeParseException e) {
      return Value.errorOf(e, LocalTime.class);
    }
  }

  static Value<LocalTime> localTimeOf(int hour, int minute) {
    try {
      return new LocalTimeValue(LocalTime.of(hour, minute));
    } catch (DateTimeException e) {
      return Value.errorOf(e, LocalTime.class);
    }
  }

  static Value<LocalTime> localTimeOf(int hour, int minute, int second) {
    try {
      return new LocalTimeValue(LocalTime.of(hour, minute, second));
    } catch (DateTimeException e) {
      return Value.errorOf(e, LocalTime.class);
    }
  }

  static Value<LocalTime> localTimeOf(int hour, int minute, int second, int nano) {
    try {
      return new LocalTimeValue(LocalTime.of(hour, minute, second, nano));
    } catch (DateTimeException e) {
      return Value.errorOf(e, LocalTime.class);
    }
  }

  // -- Year --

  static Value<Year> yearOf(String s) {
    if (s == null || s.isBlank()) {
      return Value.errorMessage(mustNotBeNullOrBlank("Year string"), Year.class);
    }
    try {
      return new YearValue(Year.parse(s));
    } catch (DateTimeParseException e) {
      return Value.errorOf(e, Year.class);
    }
  }

  static Value<Year> yearOf(int year) {
    try {
      return new YearValue(Year.of(year));
    } catch (DateTimeException e) {
      return Value.errorOf(e, Year.class);
    }
  }

  // -- YearMonth --

  static Value<YearMonth> yearMonthOf(String s) {
    if (s == null || s.isBlank()) {
      return Value.errorMessage(mustNotBeNullOrBlank("YearMonth string"), YearMonth.class);
    }
    try {
      return new YearMonthValue(YearMonth.parse(s));
    } catch (DateTimeParseException e) {
      return Value.errorOf(e, YearMonth.class);
    }
  }

  static Value<YearMonth> yearMonthOf(int year, int month) {
    try {
      return new YearMonthValue(YearMonth.of(year, month));
    } catch (DateTimeException e) {
      return Value.errorOf(e, YearMonth.class);
    }
  }

  // -- ZoneId --

  static Value<ZoneId> zoneIdOf(String s) {
    if (s == null || s.isBlank()) {
      return Value.errorMessage(mustNotBeNullOrBlank("ZoneId string"), ZoneId.class);
    }
    try {
      return new ZoneIdValue(ZoneId.of(s));
    } catch (DateTimeException e) {
      return Value.errorOf(e, ZoneId.class);
    }
  }

  // -- Duration --

  static Value<Duration> durationOf(String s) {
    if (s == null || s.isBlank()) {
      return Value.errorMessage(mustNotBeNullOrBlank("Duration string"), Duration.class);
    }
    try {
      return new DurationValue(Duration.parse(s));
    } catch (DateTimeParseException e) {
      return Value.errorOf(e, Duration.class);
    }
  }

  static Value<Duration> durationOfNanos(long nanos) {
    return new DurationValue(Duration.ofNanos(nanos));
  }

  static Value<Duration> durationOfMillis(long millis) {
    return new DurationValue(Duration.ofMillis(millis));
  }

  static Value<Duration> durationOfSeconds(long seconds) {
    return new DurationValue(Duration.ofSeconds(seconds));
  }

  static Value<Duration> durationOfMinutes(long minutes) {
    try {
      return new DurationValue(Duration.ofMinutes(minutes));
    } catch (ArithmeticException e) {
      return Value.errorOf(e, Duration.class);
    }
  }

  static Value<Duration> durationOfHours(long hours) {
    try {
      return new DurationValue(Duration.ofHours(hours));
    } catch (ArithmeticException e) {
      return Value.errorOf(e, Duration.class);
    }
  }

  static Value<Duration> durationOfDays(long days) {
    try {
      return new DurationValue(Duration.ofDays(days));
    } catch (ArithmeticException e) {
      return Value.errorOf(e, Duration.class);
    }
  }

  // -- Period --

  static Value<Period> periodOf(String s) {
    if (s == null || s.isBlank()) {
      return Value.errorMessage(mustNotBeNullOrBlank("Period string"), Period.class);
    }
    try {
      return new PeriodValue(Period.parse(s));
    } catch (DateTimeParseException e) {
      return Value.errorOf(e, Period.class);
    }
  }

  static Value<Period> periodOf(int years, int months, int days) {
    return new PeriodValue(Period.of(years, months, days));
  }

  static Value<Period> periodOfDays(int days) {
    return new PeriodValue(Period.ofDays(days));
  }

  static Value<Period> periodOfWeeks(int weeks) {
    return new PeriodValue(Period.ofWeeks(weeks));
  }

  static Value<Period> periodOfMonths(int months) {
    return new PeriodValue(Period.ofMonths(months));
  }

  static Value<Period> periodOfYears(int years) {
    return new PeriodValue(Period.ofYears(years));
  }

  // -- BigInteger --

  static Value<BigInteger> bigIntegerOf(String s) {
    if (s == null || s.isBlank()) {
      return Value.errorMessage(mustNotBeNullOrBlank("BigInteger string"), BigInteger.class);
    }
    try {
      return new BigIntegerValue(new BigInteger(s));
    } catch (NumberFormatException e) {
      return Value.errorOf(e, BigInteger.class);
    }
  }

  static Value<BigInteger> bigIntegerOf(long value) {
    return new BigIntegerValue(BigInteger.valueOf(value));
  }

  static Value<BigInteger> bigIntegerOf(byte[] twosComplement) {
    if (twosComplement == null) {
      return Value.errorMessage(mustNotBeNull("byte[]"), BigInteger.class);
    }
    try {
      return new BigIntegerValue(new BigInteger(twosComplement));
    } catch (NumberFormatException e) {
      return Value.errorOf(e, BigInteger.class);
    }
  }

  // -- BigDecimal --

  static Value<BigDecimal> bigDecimalOf(String s) {
    if (s == null || s.isBlank()) {
      return Value.errorMessage(mustNotBeNullOrBlank("BigDecimal string"), BigDecimal.class);
    }
    try {
      return new BigDecimalValue(new BigDecimal(s));
    } catch (NumberFormatException e) {
      return Value.errorOf(e, BigDecimal.class);
    }
  }

  static Value<BigDecimal> bigDecimalOf(long value) {
    return new BigDecimalValue(BigDecimal.valueOf(value));
  }

  static Value<BigDecimal> bigDecimalOf(double value) {
    try {
      return new BigDecimalValue(BigDecimal.valueOf(value));
    } catch (NumberFormatException e) {
      return Value.errorOf(e, BigDecimal.class);
    }
  }

  static Value<BigDecimal> bigDecimalOf(BigInteger value) {
    if (value == null) {
      return Value.errorMessage(mustNotBeNull("BigInteger"), BigDecimal.class);
    }
    return new BigDecimalValue(new BigDecimal(value));
  }

  static Value<BigDecimal> bigDecimalOf(BigInteger unscaled, int scale) {
    if (unscaled == null) {
      return Value.errorMessage(mustNotBeNull("BigInteger"), BigDecimal.class);
    }
    return new BigDecimalValue(new BigDecimal(unscaled, scale));
  }

  // -- Locale --

  static Value<Locale> localeOf(String tag) {
    if (tag == null || tag.isBlank()) {
      return Value.errorMessage(mustNotBeNullOrBlank("Locale tag"), Locale.class);
    }
    Locale parsed = Locale.forLanguageTag(tag);
    if (LocaleValue.isUndetermined(parsed)) {
      return Value.errorMessage("Invalid Locale: " + tag, Locale.class);
    }
    return new LocaleValue(parsed);
  }

  // -- Currency --

  static Value<Currency> currencyOf(String code) {
    if (code == null || code.isBlank()) {
      return Value.errorMessage(mustNotBeNullOrBlank("Currency code"), Currency.class);
    }
    try {
      return new CurrencyValue(Currency.getInstance(code));
    } catch (IllegalArgumentException e) {
      return Value.errorOf(e, Currency.class);
    }
  }

  // -- InetAddress --

  static Value<InetAddress> inetAddressOf(String literal) {
    if (literal == null || literal.isBlank()) {
      return Value.errorMessage(mustNotBeNullOrBlank("InetAddress literal"), InetAddress.class);
    }
    try {
      return new InetAddressValue(InetAddress.ofLiteral(literal));
    } catch (IllegalArgumentException e) {
      return Value.errorOf(e, InetAddress.class);
    }
  }

  static Value<InetAddress> inetAddressOf(byte[] addressBytes) {
    if (addressBytes == null) {
      return Value.errorMessage(mustNotBeNull("address bytes"), InetAddress.class);
    }
    try {
      return new InetAddressValue(InetAddress.getByAddress(addressBytes));
    } catch (UnknownHostException e) {
      return Value.errorOf(e, InetAddress.class);
    }
  }

  // -- Type-symbol parsing (Value.fromSymbol) --

  /**
   * Parses {@code text} as the concrete built-in type named by the short {@code symbol} (e.g. {@code "bigint"},
   * {@code "instant"}). Lenient — returns an {@link ErrorValue} on an unknown symbol or unparseable text. Numeric
   * symbols are strict: a value out of the target's range, or a fractional value for an integer target, yields an
   * ErrorValue with a descriptive message. The tree-spec pseudo-symbols {@code "number"} and {@code "ref"} are resolved
   * by the tree parser, not here.
   */
  static Value<?> fromSymbol(String symbol, String text) {
    if (symbol == null || symbol.isBlank()) {
      return Value.errorMessage("Type symbol must not be null or blank", String.class);
    }
    if (text == null) {
      return Value.errorMessage(mustNotBeNull("value text"), String.class);
    }
    return switch (symbol) {
      case "string" -> Value.of(text);
      case "bool" -> boolOf(text);
      case "char" -> charOf(text);
      case "int" -> numberAsInteger(text);
      case "long" -> numberAsLong(text);
      case "short" -> numberAsShort(text);
      case "byte" -> numberAsByte(text);
      case "bigint" -> numberAsBigInteger(text);
      case "double" -> numberAsDouble(text);
      case "float" -> numberAsFloat(text);
      case "bigdec" -> numberAsBigDecimal(text);
      case "uuid" -> uuidOf(text);
      case "url" -> urlOf(text);
      case "instant" -> timeInstantOf(text);
      case "date" -> localDateOf(text);
      case "datetime" -> localDateTimeOf(text);
      case "zdatetime" -> zonedDateTimeOf(text);
      case "odatetime" -> offsetDateTimeOf(text);
      case "time" -> localTimeOf(text);
      case "year" -> yearOf(text);
      case "yearmonth" -> yearMonthOf(text);
      case "zone" -> zoneIdOf(text);
      case "duration" -> durationOf(text);
      case "period" -> periodOf(text);
      case "locale" -> localeOf(text);
      case "currency" -> currencyOf(text);
      case "ip" -> inetAddressOf(text);
      default -> Value.errorMessage("Unknown type symbol: " + symbol, String.class);
    };
  }

  private static Value<Boolean> boolOf(String s) {
    if (s.equalsIgnoreCase("true")) {
      return Value.of(true);
    }
    if (s.equalsIgnoreCase("false")) {
      return Value.of(false);
    }
    return Value.errorMessage("Not a boolean: " + s, Boolean.class);
  }

  private static Value<Character> charOf(String s) {
    if (s.length() != 1) {
      return Value.errorMessage("Not a single character: " + s, Character.class);
    }
    return Value.of(s.charAt(0));
  }

  // Parses a numeric literal (underscore group separators stripped) to an exact BigDecimal, or null if unparseable.
  private static BigDecimal numericLiteral(String s) {
    if (s.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(s.replace("_", ""));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static boolean isWholeValued(BigDecimal d) {
    return d.stripTrailingZeros().scale() <= 0;
  }

  private static Value<Integer> numberAsInteger(String s) {
    BigDecimal d = numericLiteral(s);
    if (d == null) {
      return Value.errorMessage(NOT_A_NUMBER + s, Integer.class);
    }
    if (!isWholeValued(d)) {
      return Value.errorMessage(s + NOT_AN_INTEGER, Integer.class);
    }
    BigInteger i = d.toBigIntegerExact();
    if (i.bitLength() > 31) {
      return Value.errorMessage(s + " overflows int", Integer.class);
    }
    return Value.of(i.intValue());
  }

  private static Value<Long> numberAsLong(String s) {
    BigDecimal d = numericLiteral(s);
    if (d == null) {
      return Value.errorMessage(NOT_A_NUMBER + s, Long.class);
    }
    if (!isWholeValued(d)) {
      return Value.errorMessage(s + NOT_AN_INTEGER, Long.class);
    }
    BigInteger i = d.toBigIntegerExact();
    if (i.bitLength() > 63) {
      return Value.errorMessage(s + " overflows long", Long.class);
    }
    return Value.of(i.longValue());
  }

  private static Value<Short> numberAsShort(String s) {
    BigDecimal d = numericLiteral(s);
    if (d == null) {
      return Value.errorMessage(NOT_A_NUMBER + s, Short.class);
    }
    if (!isWholeValued(d)) {
      return Value.errorMessage(s + NOT_AN_INTEGER, Short.class);
    }
    BigInteger i = d.toBigIntegerExact();
    if (i.bitLength() > 15) {
      return Value.errorMessage(s + " overflows short", Short.class);
    }
    return Value.of(i.shortValue());
  }

  private static Value<Byte> numberAsByte(String s) {
    BigDecimal d = numericLiteral(s);
    if (d == null) {
      return Value.errorMessage(NOT_A_NUMBER + s, Byte.class);
    }
    if (!isWholeValued(d)) {
      return Value.errorMessage(s + NOT_AN_INTEGER, Byte.class);
    }
    BigInteger i = d.toBigIntegerExact();
    if (i.bitLength() > 7) {
      return Value.errorMessage(s + " overflows byte", Byte.class);
    }
    return Value.of(i.byteValue());
  }

  private static Value<BigInteger> numberAsBigInteger(String s) {
    BigDecimal d = numericLiteral(s);
    if (d == null) {
      return Value.errorMessage(NOT_A_NUMBER + s, BigInteger.class);
    }
    if (!isWholeValued(d)) {
      return Value.errorMessage(s + " is not an integer (use !bigdec)", BigInteger.class);
    }
    return Value.of(d.toBigIntegerExact());
  }

  private static Value<Double> numberAsDouble(String s) {
    BigDecimal d = numericLiteral(s);
    if (d == null) {
      return Value.errorMessage(NOT_A_NUMBER + s, Double.class);
    }
    double v = d.doubleValue();
    if (!Double.isFinite(v)) {
      return Value.errorMessage(s + " overflows double", Double.class);
    }
    return Value.of(v);
  }

  private static Value<Float> numberAsFloat(String s) {
    BigDecimal d = numericLiteral(s);
    if (d == null) {
      return Value.errorMessage(NOT_A_NUMBER + s, Float.class);
    }
    float v = d.floatValue();
    if (!Float.isFinite(v)) {
      return Value.errorMessage(s + " overflows float", Float.class);
    }
    return Value.of(v);
  }

  private static Value<BigDecimal> numberAsBigDecimal(String s) {
    BigDecimal d = numericLiteral(s);
    if (d == null) {
      return Value.errorMessage(NOT_A_NUMBER + s, BigDecimal.class);
    }
    return Value.of(d);
  }
}
