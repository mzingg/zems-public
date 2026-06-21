package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.THROWABLE;

import dev.zems.lib.common._test.ContractTest;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Contract test for {@link ValueParsers} — the package-private parser implementations behind
 * {@code Value.<type>Of(...)} string parsers and cross-type constructors. Each row in {@link #stringParsers()} pins the
 * same five concerns (parses valid input, rejects null, rejects blank, rejects malformed, reports the expected
 * exception type) for one parser.
 */
@ContractTest
@DisplayName("ValueParsers")
class ValueParsersTest {

  static Stream<StringParserCase<?>> stringParsers() {
    return Stream.of(
      new StringParserCase<>(
        "uuidOf",
        Value::uuidOf,
        "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
        "not-a-uuid",
        IllegalArgumentException.class,
        "UUID string must not be null or empty"
      ),
      new StringParserCase<>(
        "urlOf",
        Value::urlOf,
        "https://example.com",
        "::not a uri::",
        URISyntaxException.class,
        "URI string must not be null or empty"
      ),
      new StringParserCase<>(
        "timeInstantOf",
        Value::timeInstantOf,
        "2026-01-01T00:00:00Z",
        "not-an-instant",
        DateTimeParseException.class,
        "Instant string must not be null or empty"
      ),
      new StringParserCase<>(
        "localDateOf",
        Value::localDateOf,
        "2026-05-09",
        "not-a-date",
        DateTimeParseException.class,
        "LocalDate string must not be null or empty"
      ),
      new StringParserCase<>(
        "localDateTimeOf",
        Value::localDateTimeOf,
        "2026-05-09T10:15:30",
        "not-a-date-time",
        DateTimeParseException.class,
        "LocalDateTime string must not be null or empty"
      ),
      new StringParserCase<>(
        "zonedDateTimeOf",
        Value::zonedDateTimeOf,
        "2026-05-09T10:15:30+02:00[Europe/Zurich]",
        "not-a-zoned",
        DateTimeParseException.class,
        "ZonedDateTime string must not be null or empty"
      ),
      new StringParserCase<>(
        "offsetDateTimeOf",
        Value::offsetDateTimeOf,
        "2026-05-09T10:15:30+02:00",
        "not-an-offset",
        DateTimeParseException.class,
        "OffsetDateTime string must not be null or empty"
      ),
      new StringParserCase<>(
        "localTimeOf",
        Value::localTimeOf,
        "10:15:30",
        "not-a-time",
        DateTimeParseException.class,
        "LocalTime string must not be null or empty"
      ),
      new StringParserCase<>(
        "yearOf",
        Value::yearOf,
        "2026",
        "not-a-year",
        DateTimeParseException.class,
        "Year string must not be null or empty"
      ),
      new StringParserCase<>(
        "yearMonthOf",
        Value::yearMonthOf,
        "2026-05",
        "not-a-year-month",
        DateTimeParseException.class,
        "YearMonth string must not be null or empty"
      ),
      new StringParserCase<>(
        "zoneIdOf",
        Value::zoneIdOf,
        "Europe/Zurich",
        "Not/A/Zone",
        DateTimeException.class,
        "ZoneId string must not be null or empty"
      ),
      new StringParserCase<>(
        "durationOf",
        Value::durationOf,
        "PT1H30M",
        "not-a-duration",
        DateTimeParseException.class,
        "Duration string must not be null or empty"
      ),
      new StringParserCase<>(
        "periodOf",
        Value::periodOf,
        "P1Y2M3D",
        "not-a-period",
        DateTimeParseException.class,
        "Period string must not be null or empty"
      ),
      new StringParserCase<>(
        "bigIntegerOf",
        Value::bigIntegerOf,
        "123456789",
        "not-a-number",
        NumberFormatException.class,
        "BigInteger string must not be null or empty"
      ),
      new StringParserCase<>(
        "bigDecimalOf",
        Value::bigDecimalOf,
        "3.14",
        "not-a-decimal",
        NumberFormatException.class,
        "BigDecimal string must not be null or empty"
      ),
      new StringParserCase<>(
        "currencyOf",
        Value::currencyOf,
        "USD",
        "ZZZZ",
        IllegalArgumentException.class,
        "Currency code must not be null or empty"
      ),
      new StringParserCase<>(
        "inetAddressOf",
        Value::inetAddressOf,
        "192.168.1.1",
        "not-an-ip",
        IllegalArgumentException.class,
        "InetAddress literal must not be null or empty"
      )
    );
  }

  @ParameterizedTest(name = "{0} parses valid input")
  @MethodSource("stringParsers")
  <T> void parsesValidInput(StringParserCase<T> spec) {
    Value<T> result = spec.parser().apply(spec.valid());
    assertThat(result).isNotInstanceOf(ErrorValue.class);
    assertThat(result.error()).isEmpty();
  }

  @ParameterizedTest(name = "{0} rejects null with documented message")
  @MethodSource("stringParsers")
  <T> void rejectsNull(StringParserCase<T> spec) {
    Value<T> result = spec.parser().apply(null);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).hasMessage(spec.blankErrorMessage());
  }

  @ParameterizedTest(name = "{0} rejects blank with documented message")
  @MethodSource("stringParsers")
  <T> void rejectsBlank(StringParserCase<T> spec) {
    Value<T> result = spec.parser().apply("   ");
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).hasMessage(spec.blankErrorMessage());
  }

  @ParameterizedTest(name = "{0} wraps parse failure as ErrorValue with original cause type")
  @MethodSource("stringParsers")
  <T> void wrapsParseFailure(StringParserCase<T> spec) {
    Value<T> result = spec.parser().apply(spec.invalid());
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).isInstanceOf(spec.exceptionType());
  }

  @ParameterizedTest(name = "localeOf({0}) is rejected (\"und\" sentinel)")
  @ValueSource(strings = { "!!", "%%%" })
  @DisplayName("localeOf rejects tags that resolve to \"und\"")
  void localeRejectsUndefined(String tag) {
    Value<Locale> result = Value.localeOf(tag);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).hasMessageContaining("Invalid Locale");
  }

  // ============ Locale parser is special: returns "und" sentinel for unrecognised tag ============

  @Test
  @DisplayName("localeOf accepts valid BCP 47 tag")
  void localeAcceptsValidTag() {
    assertThat(Value.localeOf("en-US")).isNotInstanceOf(ErrorValue.class);
  }

  @Test
  @DisplayName("localeOf rejects null with documented message")
  void localeRejectsNull() {
    Value<Locale> result = Value.localeOf(null);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).hasMessage("Locale tag must not be null or empty");
  }

  @Test
  @DisplayName("uuidOf(long, long) builds a UUID from bit components")
  void uuidOfLongLongBuilds() {
    UUID expected = new UUID(1L, 2L);
    Value<UUID> result = Value.uuidOf(1L, 2L);
    assertThat(result).isNotInstanceOf(ErrorValue.class);
    assertThat(result.asUuid()).hasValue(expected);
  }

  // ============ Cross-type constructors (no null-input variant; some always succeed) ============

  @Test
  @DisplayName("timeInstantOfEpochMilli accepts any long")
  void timeInstantOfEpochMilliAcceptsAnyLong() {
    assertThat(Value.timeInstantOfEpochMilli(0L)).isNotInstanceOf(ErrorValue.class);
    assertThat(Value.timeInstantOfEpochMilli(Long.MAX_VALUE / 2)).isNotInstanceOf(ErrorValue.class);
  }

  @Test
  @DisplayName("timeInstantOfEpochSecond rejects out-of-range values")
  void timeInstantOfEpochSecondOutOfRange() {
    Value<Instant> result = Value.timeInstantOfEpochSecond(Long.MAX_VALUE);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).isInstanceOf(DateTimeException.class);
  }

  @Test
  @DisplayName("timeInstantOfEpochSecond(long, long) rejects nano-overflow combinations")
  void timeInstantOfEpochSecondWithNanoOverflow() {
    Value<Instant> result = Value.timeInstantOfEpochSecond(Long.MAX_VALUE, Long.MAX_VALUE);
    assertThat(result).isInstanceOf(ErrorValue.class);
  }

  @Test
  @DisplayName("localDateOf(int, int, int) parses valid components")
  void localDateOfIntComponents() {
    assertThat(Value.localDateOf(2026, 5, 9).asLocalDate()).hasValue(LocalDate.of(2026, 5, 9));
  }

  @Test
  @DisplayName("localDateOf(int, int, int) rejects invalid components")
  void localDateOfInvalidComponents() {
    Value<LocalDate> result = Value.localDateOf(2026, 13, 9);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).isInstanceOf(DateTimeException.class);
  }

  @Test
  @DisplayName("localDateOfEpochDay accepts and rejects out-of-range")
  void localDateOfEpochDayBoundary() {
    assertThat(Value.localDateOfEpochDay(0L).asLocalDate()).hasValue(LocalDate.ofEpochDay(0L));
    assertThat(Value.localDateOfEpochDay(Long.MAX_VALUE)).isInstanceOf(ErrorValue.class);
  }

  @Test
  @DisplayName("localTimeOf component overloads parse valid input")
  void localTimeOfComponents() {
    assertThat(Value.localTimeOf(10, 15).asLocalTime()).hasValue(LocalTime.of(10, 15));
    assertThat(Value.localTimeOf(10, 15, 30).asLocalTime()).hasValue(LocalTime.of(10, 15, 30));
    assertThat(Value.localTimeOf(10, 15, 30, 500).asLocalTime()).hasValue(LocalTime.of(10, 15, 30, 500));
  }

  @Test
  @DisplayName("localTimeOf component overloads reject invalid input")
  void localTimeOfInvalidComponents() {
    assertThat(Value.localTimeOf(99, 0)).isInstanceOf(ErrorValue.class);
    assertThat(Value.localTimeOf(0, 99, 0)).isInstanceOf(ErrorValue.class);
    assertThat(Value.localTimeOf(0, 0, 99, 0)).isInstanceOf(ErrorValue.class);
  }

  @Test
  @DisplayName("localDateTimeOf component overloads parse valid input")
  void localDateTimeOfComponents() {
    LocalDate d = LocalDate.of(2026, 5, 9);
    LocalTime t = LocalTime.of(10, 15);
    LocalDateTime expected = LocalDateTime.of(d, t);
    assertThat(Value.localDateTimeOf(d, t).asLocalDateTime()).hasValue(expected);
    assertThat(Value.localDateTimeOf(2026, 5, 9, 10, 15).asLocalDateTime()).hasValue(expected);
    assertThat(Value.localDateTimeOf(2026, 5, 9, 10, 15, 0).asLocalDateTime()).hasValue(expected);
  }

  @Test
  @DisplayName("localDateTimeOf(LocalDate, LocalTime) rejects null components")
  void localDateTimeOfNullComponents() {
    Value<LocalDateTime> result = Value.localDateTimeOf(null, LocalTime.MIDNIGHT);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).hasMessage("LocalDate and LocalTime must not be null");

    Value<LocalDateTime> result2 = Value.localDateTimeOf(LocalDate.now(), null);
    assertThat(result2).isInstanceOf(ErrorValue.class);
  }

  @Test
  @DisplayName("localDateTimeOf component overloads reject invalid input")
  void localDateTimeOfInvalidComponents() {
    assertThat(Value.localDateTimeOf(2026, 13, 9, 10, 15)).isInstanceOf(ErrorValue.class);
    assertThat(Value.localDateTimeOf(2026, 13, 9, 10, 15, 30)).isInstanceOf(ErrorValue.class);
  }

  @Test
  @DisplayName("yearOf(int) accepts and rejects out-of-range")
  void yearOfInt() {
    assertThat(Value.yearOf(2026).asYear()).hasValue(Year.of(2026));
    assertThat(Value.yearOf(Year.MAX_VALUE + 1)).isInstanceOf(ErrorValue.class);
  }

  @Test
  @DisplayName("yearMonthOf(int, int) accepts and rejects invalid month")
  void yearMonthOfInts() {
    assertThat(Value.yearMonthOf(2026, 5).asYearMonth()).hasValue(YearMonth.of(2026, 5));
    assertThat(Value.yearMonthOf(2026, 13)).isInstanceOf(ErrorValue.class);
  }

  @Test
  @DisplayName("durationOf{Nanos,Millis,Seconds} accept any long")
  void durationOfTotal() {
    assertThat(Value.durationOfNanos(10L).asDuration()).hasValue(Duration.ofNanos(10L));
    assertThat(Value.durationOfMillis(10L).asDuration()).hasValue(Duration.ofMillis(10L));
    assertThat(Value.durationOfSeconds(10L).asDuration()).hasValue(Duration.ofSeconds(10L));
  }

  @Test
  @DisplayName("durationOf{Minutes,Hours,Days} reject overflow")
  void durationOfOverflow() {
    assertThat(Value.durationOfMinutes(Long.MAX_VALUE)).isInstanceOf(ErrorValue.class);
    assertThat(Value.durationOfHours(Long.MAX_VALUE)).isInstanceOf(ErrorValue.class);
    assertThat(Value.durationOfDays(Long.MAX_VALUE)).isInstanceOf(ErrorValue.class);
  }

  @Test
  @DisplayName("durationOf{Minutes,Hours,Days} accept valid input")
  void durationOfBuckets() {
    assertThat(Value.durationOfMinutes(10L).asDuration()).hasValue(Duration.ofMinutes(10L));
    assertThat(Value.durationOfHours(2L).asDuration()).hasValue(Duration.ofHours(2L));
    assertThat(Value.durationOfDays(1L).asDuration()).hasValue(Duration.ofDays(1L));
  }

  @Test
  @DisplayName("periodOf{int,int,int}/Days/Weeks/Months/Years accept any int")
  void periodOfBuckets() {
    assertThat(Value.periodOf(1, 2, 3).asPeriod()).hasValue(Period.of(1, 2, 3));
    assertThat(Value.periodOfDays(7).asPeriod()).hasValue(Period.ofDays(7));
    assertThat(Value.periodOfWeeks(2).asPeriod()).hasValue(Period.ofWeeks(2));
    assertThat(Value.periodOfMonths(3).asPeriod()).hasValue(Period.ofMonths(3));
    assertThat(Value.periodOfYears(1).asPeriod()).hasValue(Period.ofYears(1));
  }

  @Test
  @DisplayName("bigIntegerOf(long) wraps any long")
  void bigIntegerOfLong() {
    assertThat(Value.bigIntegerOf(123L).asBigInteger()).hasValue(BigInteger.valueOf(123L));
  }

  @Test
  @DisplayName("bigIntegerOf(byte[]) accepts non-null and rejects null")
  void bigIntegerOfBytes() {
    assertThat(Value.bigIntegerOf(new byte[] { 1, 2, 3 }).asBigInteger()).hasValue(
      new BigInteger(new byte[] { 1, 2, 3 })
    );
    Value<BigInteger> nullResult = Value.bigIntegerOf((byte[]) null);
    assertThat(nullResult).isInstanceOf(ErrorValue.class);
  }

  @Test
  @DisplayName("bigIntegerOf(byte[]) rejects empty array (NumberFormatException)")
  void bigIntegerOfEmptyBytes() {
    Value<BigInteger> result = Value.bigIntegerOf(new byte[0]);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).isInstanceOf(NumberFormatException.class);
  }

  @Test
  @DisplayName("bigDecimalOf(long) and bigDecimalOf(double) wrap any value")
  void bigDecimalOfNumeric() {
    assertThat(Value.bigDecimalOf(123L).asBigDecimal()).hasValue(BigDecimal.valueOf(123L));
    assertThat(Value.bigDecimalOf(3.14).asBigDecimal()).hasValue(BigDecimal.valueOf(3.14));
  }

  @Test
  @DisplayName("bigDecimalOf(double) rejects NaN / Infinity")
  void bigDecimalOfNonFiniteDouble() {
    assertThat(Value.bigDecimalOf(Double.NaN)).isInstanceOf(ErrorValue.class);
    assertThat(Value.bigDecimalOf(Double.POSITIVE_INFINITY)).isInstanceOf(ErrorValue.class);
    assertThat(Value.bigDecimalOf(Double.NEGATIVE_INFINITY)).isInstanceOf(ErrorValue.class);
  }

  @Test
  @DisplayName("bigDecimalOf(BigInteger) and bigDecimalOf(BigInteger, scale) reject null")
  void bigDecimalOfBigIntegerNull() {
    assertThat(Value.bigDecimalOf((BigInteger) null)).isInstanceOf(ErrorValue.class);
    assertThat(Value.bigDecimalOf((BigInteger) null, 2)).isInstanceOf(ErrorValue.class);
  }

  @Test
  @DisplayName("bigDecimalOf(BigInteger, scale) builds a scaled BigDecimal")
  void bigDecimalOfBigIntegerScale() {
    var unscaled = new BigInteger("12345");
    Value<BigDecimal> result = Value.bigDecimalOf(unscaled, 2);
    assertThat(result.asBigDecimal()).hasValue(new BigDecimal(unscaled, 2));
  }

  @Test
  @DisplayName("inetAddressOf(byte[]) accepts and rejects")
  void inetAddressOfBytes() {
    assertThat(Value.inetAddressOf(new byte[] { (byte) 192, (byte) 168, 1, 1 }).asInetAddress()).isPresent();
    assertThat(Value.inetAddressOf((byte[]) null)).isInstanceOf(ErrorValue.class);
    // wrong length triggers UnknownHostException
    assertThat(Value.inetAddressOf(new byte[] { 1, 2, 3 })).isInstanceOf(ErrorValue.class);
  }

  /**
   * One row per string parser. {@code parser} is the {@code Value.<type>Of(String)} reference; {@code valid} is a
   * parseable input; {@code invalid} is malformed input; {@code exceptionType} is the throwable wrapped on malformed
   * input; {@code blankErrorMessage} is the documented "must not be null or blank" message.
   */
  record StringParserCase<T>(
    String label,
    Function<String, Value<T>> parser,
    String valid,
    String invalid,
    Class<? extends Throwable> exceptionType,
    String blankErrorMessage
  ) {
    @Override
    public String toString() {
      return label;
    }
  }
}
