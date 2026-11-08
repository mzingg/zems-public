package dev.zems.lib.value.json.engine.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.zems.lib.common.junit.ContractTest;
import java.math.BigInteger;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ContractTest
@DisplayName("NumberForm — the value of a JSON number text, free of leading and trailing zeros")
class NumberFormTest {

  private static final Duration AT_ONCE = Duration.ofSeconds(1);

  static Stream<FormCase> forms() {
    return Stream.of(
      new FormCase("7", new NumberForm(false, "7", BigInteger.ZERO, 1)),
      new FormCase("-42", new NumberForm(true, "42", BigInteger.ZERO, 2)),
      new FormCase("1200", new NumberForm(false, "12", BigInteger.TWO, 4)),
      new FormCase("123.0", new NumberForm(false, "123", BigInteger.ZERO, 4)),
      new FormCase("1.23e2", new NumberForm(false, "123", BigInteger.ZERO, 3)),
      new FormCase("1.23E+2", new NumberForm(false, "123", BigInteger.ZERO, 3)),
      new FormCase("345.6", new NumberForm(false, "3456", BigInteger.valueOf(-1), 4)),
      new FormCase("-0.5e-3", new NumberForm(true, "5", BigInteger.valueOf(-4), 2)),
      new FormCase("0.0012", new NumberForm(false, "12", BigInteger.valueOf(-4), 5)),
      new FormCase("120e-1", new NumberForm(false, "12", BigInteger.ZERO, 3)),
      new FormCase("0", new NumberForm(false, "", BigInteger.ZERO, 1)),
      new FormCase("-0.00", new NumberForm(false, "", BigInteger.ZERO, 3)),
      new FormCase("0e999999999999", new NumberForm(false, "", BigInteger.ZERO, 1))
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("forms")
  @DisplayName("reads sign, significant digits, exponent and the count of written digits")
  void readsTheValueOfTheText(FormCase formCase) {
    assertThat(NumberForm.of(formCase.text())).isEqualTo(formCase.expected());
  }

  @Test
  @DisplayName("an exponent beyond any primitive range is read exactly, and at once")
  void aHugeExponentIsReadExactly() {
    assertTimeoutPreemptively(AT_ONCE, () -> {
      assertThat(NumberForm.of("1e99999999999999999999").exponent()).isEqualTo(new BigInteger("99999999999999999999"));
      assertThat(NumberForm.of("10e-99999999999999999999").exponent()).isEqualTo(
        new BigInteger("-99999999999999999998")
      );
    });
  }

  @Test
  @DisplayName("isZero answers for every zero however written, and only for zeros")
  void isZeroAnswersForEveryZero() {
    assertThat(NumberForm.of("-0.0e5").isZero()).isTrue();
    assertThat(NumberForm.of("0.0001").isZero()).isFalse();
  }

  @Test
  @DisplayName("isSameNumber and numberHash ignore how the number was written, not what it is")
  void sameNumberIgnoresTheWrittenForm() {
    NumberForm hundred = NumberForm.of("100");
    NumberForm hundredAsExponent = NumberForm.of("1.000e2");

    assertThat(hundred.isSameNumber(hundredAsExponent)).isTrue();
    assertThat(hundred.numberHash()).isEqualTo(hundredAsExponent.numberHash());
    assertThat(hundred.isSameNumber(NumberForm.of("-100"))).isFalse();
    assertThat(hundred.isSameNumber(NumberForm.of("1000"))).isFalse();
    assertThat(NumberForm.of("1e99999999999999999999").isSameNumber(NumberForm.of("1e99999999999999999998"))).isFalse();
  }

  record FormCase(String text, NumberForm expected) {
    @Override
    public String toString() {
      return text;
    }
  }
}
