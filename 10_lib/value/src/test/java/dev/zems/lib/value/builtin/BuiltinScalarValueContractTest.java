package dev.zems.lib.value.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.ErrorValue;
import dev.zems.lib.value.Value;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Parameterised contract test that drives 10 structurally identical builtin scalar value types (PeriodValue, YearValue,
 * YearMonthValue, LocalDateValue, LocalDateTimeValue, LocalTimeValue, OffsetDateTimeValue, ZonedDateTimeValue,
 * TimeInstantValue, DurationValue) through one specification of behaviour. Replaces 10 near-clone test files that
 * previously each spelled out the same six concerns: direct construction, null-rejection, string-factory parsing, state
 * predicates, equals/hashCode, toString, and round-trip.
 *
 * <p>
 * Each row in {@link #scalarTypes()} carries the type-specific bindings — sample value, factory references, mutator for
 * "different value" tests, expected error-message stems — and one method below covers each concern across all rows.
 */
@DisplayName("Builtin scalar value contract")
@ContractTest
class BuiltinScalarValueContractTest {

  static Stream<ScalarSpec<?>> scalarTypes() {
    return Stream.of(
      new ScalarSpec<>(
        "PeriodValue",
        PeriodValue.class,
        PeriodValue::new,
        "P1Y2M3D",
        Period.parse("P1Y2M3D"),
        Value::periodOf,
        "Period must not be null",
        "Period string must not be null or empty",
        DateTimeParseException.class,
        "not-a-period",
        p -> p.plusDays(1),
        "period"
      ),
      new ScalarSpec<>(
        "YearValue",
        YearValue.class,
        YearValue::new,
        "2026",
        Year.parse("2026"),
        Value::yearOf,
        "Year must not be null",
        "Year string must not be null or empty",
        DateTimeParseException.class,
        "not-a-year",
        y -> y.plusYears(1),
        "year"
      ),
      new ScalarSpec<>(
        "YearMonthValue",
        YearMonthValue.class,
        YearMonthValue::new,
        "2026-05",
        YearMonth.parse("2026-05"),
        Value::yearMonthOf,
        "YearMonth must not be null",
        "YearMonth string must not be null or empty",
        DateTimeParseException.class,
        "not-a-year-month",
        ym -> ym.plusMonths(1),
        "yearMonth"
      ),
      new ScalarSpec<>(
        "LocalDateValue",
        LocalDateValue.class,
        LocalDateValue::new,
        "2026-05-09",
        LocalDate.parse("2026-05-09"),
        Value::localDateOf,
        "LocalDate must not be null",
        "LocalDate string must not be null or empty",
        DateTimeParseException.class,
        "not-a-date",
        d -> d.plusDays(1),
        "localDate"
      ),
      new ScalarSpec<>(
        "LocalDateTimeValue",
        LocalDateTimeValue.class,
        LocalDateTimeValue::new,
        "2026-05-09T10:15:30",
        LocalDateTime.parse("2026-05-09T10:15:30"),
        Value::localDateTimeOf,
        "LocalDateTime must not be null",
        "LocalDateTime string must not be null or empty",
        DateTimeParseException.class,
        "not-a-date-time",
        dt -> dt.plusMinutes(1),
        "localDateTime"
      ),
      new ScalarSpec<>(
        "LocalTimeValue",
        LocalTimeValue.class,
        LocalTimeValue::new,
        "10:15:30",
        LocalTime.parse("10:15:30"),
        Value::localTimeOf,
        "LocalTime must not be null",
        "LocalTime string must not be null or empty",
        DateTimeParseException.class,
        "not-a-time",
        t -> t.plusSeconds(1),
        "localTime"
      ),
      new ScalarSpec<>(
        "OffsetDateTimeValue",
        OffsetDateTimeValue.class,
        OffsetDateTimeValue::new,
        "2026-05-09T10:15:30+02:00",
        OffsetDateTime.parse("2026-05-09T10:15:30+02:00"),
        Value::offsetDateTimeOf,
        "OffsetDateTime must not be null",
        "OffsetDateTime string must not be null or empty",
        DateTimeParseException.class,
        "not-an-offset",
        odt -> odt.plusMinutes(1),
        "offsetDateTime"
      ),
      new ScalarSpec<>(
        "ZonedDateTimeValue",
        ZonedDateTimeValue.class,
        ZonedDateTimeValue::new,
        "2026-05-09T10:15:30+02:00[Europe/Zurich]",
        ZonedDateTime.parse("2026-05-09T10:15:30+02:00[Europe/Zurich]"),
        Value::zonedDateTimeOf,
        "ZonedDateTime must not be null",
        "ZonedDateTime string must not be null or empty",
        DateTimeParseException.class,
        "not-a-zoned",
        zdt -> zdt.plusMinutes(1),
        "zonedDateTime"
      ),
      new ScalarSpec<>(
        "TimeInstantValue",
        TimeInstantValue.class,
        TimeInstantValue::new,
        "2026-05-09T10:15:30Z",
        Instant.parse("2026-05-09T10:15:30Z"),
        Value::timeInstantOf,
        "Instant must not be null",
        "Instant string must not be null or empty",
        DateTimeParseException.class,
        "not-an-instant",
        i -> i.plusSeconds(1),
        "instant"
      ),
      new ScalarSpec<>(
        "DurationValue",
        DurationValue.class,
        DurationValue::new,
        "PT1H30M",
        Duration.parse("PT1H30M"),
        Value::durationOf,
        "Duration must not be null",
        "Duration string must not be null or empty",
        DateTimeParseException.class,
        "not-a-duration",
        d -> d.plusSeconds(1),
        "duration"
      )
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("scalarTypes")
  @DisplayName("ctor wraps non-null sample")
  <T> void wrapsSample(ScalarSpec<T> spec) {
    Value<T> wrapped = spec.wrap().apply(spec.sample());
    assertThat(wrapped).isInstanceOf(spec.wrapperClass());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("scalarTypes")
  @DisplayName("ctor throws NullPointerException for null")
  <T> void throwsForNull(ScalarSpec<T> spec) {
    assertThatThrownBy(() -> spec.wrap().apply(null))
      .isInstanceOf(NullPointerException.class)
      .hasMessage(spec.nullErrorMessage());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("scalarTypes")
  @DisplayName("string factory parses valid input")
  <T> void parsesString(ScalarSpec<T> spec) {
    Value<T> result = spec.stringFactory().apply(spec.sampleIso());
    assertThat(result).isInstanceOf(spec.wrapperClass());
    assertThat(result).isEqualTo(spec.wrap().apply(spec.sample()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("scalarTypes")
  @DisplayName("string factory returns ErrorValue for null input")
  <T> void errorForNullString(ScalarSpec<T> spec) {
    Value<T> result = spec.stringFactory().apply(null);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get().extracting(Throwable::getMessage).isEqualTo(spec.stringNullErrorMessage());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("scalarTypes")
  @DisplayName("string factory returns ErrorValue for blank input")
  <T> void errorForBlankString(ScalarSpec<T> spec) {
    Value<T> result = spec.stringFactory().apply("  ");
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get().extracting(Throwable::getMessage).isEqualTo(spec.stringNullErrorMessage());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("scalarTypes")
  @DisplayName("string factory returns ErrorValue for invalid input")
  <T> void errorForInvalidString(ScalarSpec<T> spec) {
    Value<T> result = spec.stringFactory().apply(spec.invalidStringSample());
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get().isInstanceOf(spec.invalidStringException());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("scalarTypes")
  @DisplayName("state predicates: isDefined / isNotNull / isResolved / error")
  <T> void statePredicates(ScalarSpec<T> spec) {
    Value<T> value = spec.wrap().apply(spec.sample());
    assertThat(value.isDefined()).isTrue();
    assertThat(value.isNotNull()).isTrue();
    assertThat(value.isResolved()).isTrue();
    assertThat(value.error()).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("scalarTypes")
  @DisplayName("equals and hashCode")
  <T> void equalsAndHashCode(ScalarSpec<T> spec) {
    Value<T> a = spec.wrap().apply(spec.sample());
    Value<T> b = spec.wrap().apply(spec.sample());
    Value<T> different = spec.wrap().apply(spec.mutator().apply(spec.sample()));

    assertThat(a).isEqualTo(b);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    assertThat(a).isNotEqualTo(different);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("scalarTypes")
  @DisplayName("toString")
  <T> void toStringFormat(ScalarSpec<T> spec) {
    String expected = spec.displayName() + "[" + spec.componentName() + "=" + spec.sample() + "]";
    assertThat(spec.wrap().apply(spec.sample()).toString()).isEqualTo(expected);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("scalarTypes")
  @DisplayName("string round-trip via toString → stringFactory")
  <T> void roundTrip(ScalarSpec<T> spec) {
    Value<T> original = spec.wrap().apply(spec.sample());
    assertThat(spec.stringFactory().apply(spec.sample().toString())).isEqualTo(original);
  }

  /**
   * Specification for one scalar value type. {@code wrap} is the typed {@code new XxxValue(sample)} ctor;
   * {@code stringFactory} is {@code Value::<type>Of(String)}; {@code mutator} produces a different sample value to
   * exercise inequality.
   */
  record ScalarSpec<T>(
    String displayName,
    Class<? extends Value<T>> wrapperClass,
    Function<T, Value<T>> wrap,
    String sampleIso,
    T sample,
    Function<String, Value<T>> stringFactory,
    String nullErrorMessage,
    String stringNullErrorMessage,
    Class<? extends Throwable> invalidStringException,
    String invalidStringSample,
    UnaryOperator<T> mutator,
    String componentName
  ) {
    @Override
    public String toString() {
      return displayName;
    }
  }
}
