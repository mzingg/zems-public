package dev.zems.lib.value.convert;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.Value;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

@ContractTest
@DisplayName("Conversions — the shipped conversions that carry a policy")
class ConversionsTest {

  private static final Duration AT_ONCE = Duration.ofSeconds(1);

  static Stream<MatrixCase> roundingMatrix() {
    return Stream.of(
      new MatrixCase(Conversions.doubleToByte(Rounding.DOWN), Double.class, Byte.class, 3.7d, Value.of((byte) 3)),
      new MatrixCase(Conversions.doubleToShort(Rounding.DOWN), Double.class, Short.class, 3.7d, Value.of((short) 3)),
      new MatrixCase(Conversions.doubleToInt(Rounding.DOWN), Double.class, Integer.class, 3.7d, Value.of(3)),
      new MatrixCase(Conversions.doubleToLong(Rounding.DOWN), Double.class, Long.class, 3.7d, Value.of(3L)),
      new MatrixCase(
        Conversions.doubleToBigInteger(Rounding.DOWN),
        Double.class,
        BigInteger.class,
        3.7d,
        Value.of(BigInteger.valueOf(3))
      ),
      new MatrixCase(Conversions.floatToByte(Rounding.DOWN), Float.class, Byte.class, 3.7f, Value.of((byte) 3)),
      new MatrixCase(Conversions.floatToShort(Rounding.DOWN), Float.class, Short.class, 3.7f, Value.of((short) 3)),
      new MatrixCase(Conversions.floatToInt(Rounding.DOWN), Float.class, Integer.class, 3.7f, Value.of(3)),
      new MatrixCase(Conversions.floatToLong(Rounding.DOWN), Float.class, Long.class, 3.7f, Value.of(3L)),
      new MatrixCase(
        Conversions.floatToBigInteger(Rounding.DOWN),
        Float.class,
        BigInteger.class,
        3.7f,
        Value.of(BigInteger.valueOf(3))
      ),
      new MatrixCase(
        Conversions.bigDecimalToByte(Rounding.DOWN),
        BigDecimal.class,
        Byte.class,
        new BigDecimal("3.7"),
        Value.of((byte) 3)
      ),
      new MatrixCase(
        Conversions.bigDecimalToShort(Rounding.DOWN),
        BigDecimal.class,
        Short.class,
        new BigDecimal("3.7"),
        Value.of((short) 3)
      ),
      new MatrixCase(
        Conversions.bigDecimalToInt(Rounding.DOWN),
        BigDecimal.class,
        Integer.class,
        new BigDecimal("3.7"),
        Value.of(3)
      ),
      new MatrixCase(
        Conversions.bigDecimalToLong(Rounding.DOWN),
        BigDecimal.class,
        Long.class,
        new BigDecimal("3.7"),
        Value.of(3L)
      ),
      new MatrixCase(
        Conversions.bigDecimalToBigInteger(Rounding.DOWN),
        BigDecimal.class,
        BigInteger.class,
        new BigDecimal("3.7"),
        Value.of(BigInteger.valueOf(3))
      )
    );
  }

  static Stream<RoundingCase> roundingPolicies() {
    return Stream.of(
      new RoundingCase(Rounding.DOWN, 3.7d, 3),
      new RoundingCase(Rounding.DOWN, -3.7d, -3),
      new RoundingCase(Rounding.UP, 3.2d, 4),
      new RoundingCase(Rounding.UP, -3.2d, -4),
      new RoundingCase(Rounding.FLOOR, 3.7d, 3),
      new RoundingCase(Rounding.FLOOR, -3.2d, -4),
      new RoundingCase(Rounding.CEILING, 3.2d, 4),
      new RoundingCase(Rounding.CEILING, -3.7d, -3),
      new RoundingCase(Rounding.HALF_UP, 2.5d, 3),
      new RoundingCase(Rounding.HALF_DOWN, 2.5d, 2),
      new RoundingCase(Rounding.HALF_EVEN, 2.5d, 2),
      new RoundingCase(Rounding.HALF_EVEN, 3.5d, 4)
    );
  }

  static Stream<EpochCase> epochCases() {
    return Stream.of(
      new EpochCase(EpochUnit.SECONDS, 1_700_000_000L, "2023-11-14T22:13:20Z"),
      new EpochCase(EpochUnit.MILLIS, 1_700_000_000_123L, "2023-11-14T22:13:20.123Z"),
      new EpochCase(EpochUnit.MICROS, 1_700_000_000_123_456L, "2023-11-14T22:13:20.123456Z"),
      new EpochCase(EpochUnit.NANOS, 1_700_000_000_123_456_789L, "2023-11-14T22:13:20.123456789Z"),
      new EpochCase(EpochUnit.MICROS, -1L, "1969-12-31T23:59:59.999999Z"),
      new EpochCase(EpochUnit.NANOS, -1L, "1969-12-31T23:59:59.999999999Z")
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("roundingMatrix")
  @DisplayName("each factory in the matrix names its own pair and produces that target's own type")
  void theRoundingMatrixCoversEveryFractionalToIntegralPair(MatrixCase matrixCase) {
    assertThat(matrixCase.conversion().sourceType()).isEqualTo(matrixCase.sourceType());
    assertThat(matrixCase.conversion().targetType()).isEqualTo(matrixCase.targetType());
    assertThat(runOn(matrixCase.conversion(), matrixCase.input())).isEqualTo(matrixCase.expected());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("roundingPolicies")
  @DisplayName("the rounding a conversion was built with is what decides the fraction")
  void theRoundingPolicyDecidesTheFraction(RoundingCase roundingCase) {
    Conversion<Double, Integer> conversion = Conversions.doubleToInt(roundingCase.rounding());

    assertThat(conversion.convert(roundingCase.input())).isEqualTo(Value.of(roundingCase.expected()));
  }

  @Test
  @DisplayName("rounding resolves the fraction but does not widen the target — an overflow after it still refuses")
  void roundingDoesNotWidenTheTargetRange() {
    Conversion<Double, Byte> conversion = Conversions.doubleToByte(Rounding.DOWN);

    assertThat(conversion.convert(1000.7d)).hasErrorMessage("Cannot convert Double to Byte: 1000.7 overflows Byte");
    assertThat(conversion.convert(127.7d)).isEqualTo(Value.of((byte) 127));
  }

  @ParameterizedTest
  @EnumSource(Rounding.class)
  @DisplayName("no rounding turns NaN or an infinity into a whole number")
  void nonFiniteSourcesAreRefusedUnderEveryRounding(Rounding rounding) {
    Conversion<Double, Integer> conversion = Conversions.doubleToInt(rounding);

    assertThat(conversion.convert(Double.NaN)).hasErrorMessage(
      "Cannot convert Double to Integer: NaN is not a whole number"
    );
    assertThat(conversion.convert(Double.POSITIVE_INFINITY)).hasErrorMessage(
      "Cannot convert Double to Integer: Infinity is not a whole number"
    );
    assertThat(conversion.convert(Double.NEGATIVE_INFINITY)).hasErrorMessage(
      "Cannot convert Double to Integer: -Infinity is not a whole number"
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("epochCases")
  @DisplayName("the epoch unit a conversion was built with says what the number counts")
  void theEpochUnitDecidesWhatTheNumberCounts(EpochCase epochCase) {
    Conversion<Long, Instant> conversion = Conversions.epochToInstant(epochCase.unit());

    assertThat(conversion.convert(epochCase.count())).isEqualTo(Value.of(Instant.parse(epochCase.expected())));
  }

  @Test
  @DisplayName("a count that lands outside the instant range is refused with the reason the factory gave")
  void countsOutsideTheInstantRangeAreRefused() {
    Conversion<Long, Instant> conversion = Conversions.epochToInstant(EpochUnit.SECONDS);

    assertThat(conversion.convert(Long.MAX_VALUE)).hasErrorMessage(
      "Cannot convert Long to Instant: Instant exceeds minimum or maximum instant"
    );
  }

  @Test
  @DisplayName("rounding a decimal with a huge exponent answers at once: a tiny one rounds to 0, a huge one overflows")
  void roundingAHugeExponentAnswersAtOnce() {
    Conversion<BigDecimal, Integer> conversion = Conversions.bigDecimalToInt(Rounding.HALF_UP);

    assertTimeoutPreemptively(AT_ONCE, () ->
      assertThat(conversion.convert(new BigDecimal("1E-100000000"))).isEqualTo(Value.of(0))
    );
    assertTimeoutPreemptively(AT_ONCE, () ->
      assertThat(conversion.convert(new BigDecimal("1E+100000000"))).isEqualTo(
        Value.errorMessage("Cannot convert BigDecimal to Integer: 1E+100000000 overflows Integer", Integer.class)
      )
    );
  }

  @Test
  @DisplayName("rounding a decimal at the int edge of the scale overflows, never wraps to 0")
  void roundingAtTheScaleEdgeOverflows() {
    Conversion<BigDecimal, Integer> conversion = Conversions.bigDecimalToInt(Rounding.HALF_UP);
    BigDecimal atTheScaleEdge = new BigDecimal(BigInteger.ONE, Integer.MIN_VALUE);

    assertTimeoutPreemptively(AT_ONCE, () ->
      assertThat(conversion.convert(atTheScaleEdge)).isEqualTo(
        Value.errorMessage("Cannot convert BigDecimal to Integer: 1E+2147483648 overflows Integer", Integer.class)
      )
    );
  }

  @Test
  @DisplayName("a policy is required — a factory without one is a programming bug, caught where it is built")
  void aPolicyIsRequiredAtConstructionTime() {
    assertThatThrownBy(() -> Conversions.doubleToInt(null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Rounding must not be null");
    assertThatThrownBy(() -> Conversions.bigDecimalToBigInteger(null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Rounding must not be null");
    assertThatThrownBy(() -> Conversions.epochToInstant(null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Epoch unit must not be null");
  }

  @SuppressWarnings("unchecked") // the case table pairs each conversion with an input of its own source type
  private static Value<?> runOn(Conversion<?, ?> conversion, Number input) {
    return ((Conversion<Number, ?>) conversion).convert(input);
  }

  record MatrixCase(
    Conversion<?, ?> conversion,
    Class<?> sourceType,
    Class<?> targetType,
    Number input,
    Value<?> expected
  ) {
    @Override
    public String toString() {
      return sourceType.getSimpleName() + " to " + targetType.getSimpleName();
    }
  }

  record RoundingCase(Rounding rounding, double input, int expected) {
    @Override
    public String toString() {
      return rounding + " of " + input;
    }
  }

  record EpochCase(EpochUnit unit, long count, String expected) {
    @Override
    public String toString() {
      return count + " " + unit;
    }
  }
}
