package dev.zems.lib.value;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.THROWABLE;
import static org.junit.jupiter.api.Timeout.ThreadMode.SEPARATE_THREAD;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.builtin.CharacterValue;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ContractTest
@DisplayName("Value.fromSymbol")
class ValueFromSymbolTest {

  @Test
  void parsesStringBoolAndChar() {
    assertThat(Value.fromSymbol("string", "hello")).hasStringValue("hello");
    assertThat(Value.fromSymbol("bool", "true")).hasBooleanValue(true);
    assertThat(Value.fromSymbol("char", "x")).isInstanceOf(CharacterValue.class);
  }

  @Test
  void parsesStrictNumericTypes() {
    assertThat(Value.fromSymbol("int", "42")).hasIntValue(42);
    assertThat(Value.fromSymbol("long", "3000000000")).extractingValue().isEqualTo(3000000000L);
    assertThat(Value.fromSymbol("bigint", "8080").asBigInteger()).hasValue(new BigInteger("8080"));
    assertThat(Value.fromSymbol("double", "0.5")).hasDoubleValue(0.5);
    assertThat(Value.fromSymbol("bigdec", "0.1234567890123456789").asBigDecimal()).hasValue(
      new BigDecimal("0.1234567890123456789")
    );
  }

  @Test
  void bigintAcceptsWholeValuedScientificNotation() {
    assertThat(Value.fromSymbol("bigint", "1e9").asBigInteger()).hasValue(new BigInteger("1000000000"));
  }

  @Test
  void underscoreSeparatorsAreAcceptedInNumericText() {
    assertThat(Value.fromSymbol("long", "1_000_000")).extractingValue().isEqualTo(1_000_000L);
  }

  @Test
  @DisplayName("a parsed JDK type converts back to its canonical string form")
  void parsesStringConstructedJdkTypes() {
    assertThat(Value.fromSymbol("instant", "2026-06-04T10:00:00Z").convertTo(String.class)).hasStringValue(
      "2026-06-04T10:00:00Z"
    );
    assertThat(Value.fromSymbol("uuid", "00000000-0000-0000-0000-000000000001").convertTo(String.class)).hasStringValue(
      "00000000-0000-0000-0000-000000000001"
    );
    assertThat(Value.fromSymbol("ip", "192.168.1.1").convertTo(String.class)).hasStringValue("192.168.1.1");
    assertThat(Value.fromSymbol("url", "http://example.com").convertTo(String.class)).hasStringValue(
      "http://example.com"
    );
  }

  @Test
  void parsesTypeNameSymbol() {
    var result = Value.fromSymbol("type", "com.example.Person");

    assertThat(result.isTypeName()).isTrue();
    assertThat(result.asTypeName()).hasValue("com.example.Person");
  }

  @Test
  void malformedTypeNameSymbolIsAnError() {
    var result = Value.fromSymbol("type", "not a name!");

    assertThat(result.error()).get(THROWABLE).hasMessage("Invalid type name: not a name!");
  }

  @Test
  void parsesEachVersionSymbolUnderItsDialect() {
    assertThat(Value.fromSymbol("semver", "1.0.0-rc.1").asVersion()).hasValue(
      Version.parse("1.0.0-rc.1", VersionDialect.SEMVER)
    );
    assertThat(Value.fromSymbol("jdk", "25+7").asVersion()).hasValue(Version.parse("25+7", VersionDialect.JDK));
    assertThat(Value.fromSymbol("jpms", "1.0").asVersion()).hasValue(Version.parse("1.0", VersionDialect.MODULE));
  }

  @Test
  void versionSymbolParsesTheDialectQualifiedForm() {
    assertThat(Value.fromSymbol("version", "jdk:25+7").asVersion()).hasValue(Version.parse("25+7", VersionDialect.JDK));
  }

  @Test
  void invalidVersionTextUnderASymbolIsAnErrorNamingTheDialect() {
    assertThat(Value.fromSymbol("jdk", "1.0")).hasErrorMessage(
      "Invalid JDK version '1.0': Invalid version string: '1.0'"
    );
    assertThat(Value.fromSymbol("version", "1.0.0")).hasErrorMessage(
      "Invalid qualified version '1.0.0': expected <symbol>:<version> with symbol semver, jdk or jpms"
    );
  }

  @Test
  void strictIntegerOverflowIsAnError() {
    var result = Value.fromSymbol("int", "3000000000");

    assertThat(result.error()).get(THROWABLE).hasMessageContaining("overflows int");
  }

  static Stream<Arguments> integralTargets() {
    return Stream.of(
      Arguments.of("int", Integer.class),
      Arguments.of("long", Long.class),
      Arguments.of("short", Short.class),
      Arguments.of("byte", Byte.class)
    );
  }

  @ParameterizedTest(name = "{0} refuses an exponent far past its range")
  @MethodSource("integralTargets")
  void integralTargetRefusesAHugeExponent(String symbol, Class<?> expectedClass) {
    var result = Value.fromSymbol(symbol, "1e999999999");

    assertThat(result).isEqualTo(Value.errorMessage("1e999999999 overflows " + symbol, expectedClass));
  }

  @Test
  @Timeout(value = 5, threadMode = SEPARATE_THREAD)
  @DisplayName("an int target refuses a large exponent before expanding it")
  void intTargetRefusesALargeExponentAtOnce() {
    var result = Value.fromSymbol("int", "1e100000000");

    assertThat(result).isEqualTo(Value.errorMessage("1e100000000 overflows int", Integer.class));
  }

  @Test
  @DisplayName("an int target still refuses a ten-digit value past its range")
  void intTargetRefusesTenDigitsPastItsRange() {
    assertThat(Value.fromSymbol("int", "2147483648")).isEqualTo(
      Value.errorMessage("2147483648 overflows int", Integer.class)
    );
  }

  @Test
  @DisplayName("zero with a huge exponent stays zero")
  void zeroWithAHugeExponentIsZero() {
    assertThat(Value.fromSymbol("int", "0e999999999")).hasIntValue(0);
    assertThat(Value.fromSymbol("bigint", "0e5000").asBigInteger()).hasValue(BigInteger.ZERO);
  }

  @Test
  @DisplayName("a bigint of 1000 digits answers")
  void bigintOfAThousandDigitsAnswers() {
    assertThat(Value.fromSymbol("bigint", "1e999").asBigInteger()).hasValue(BigInteger.TEN.pow(999));
  }

  @Test
  @DisplayName("a bigint past 1000 digits is refused")
  void bigintPastAThousandDigitsIsRefused() {
    assertThat(Value.fromSymbol("bigint", "1e1000")).isEqualTo(
      Value.errorMessage("1e1000 has more than 1000 digits", BigInteger.class)
    );
    assertThat(Value.fromSymbol("bigint", "1e999999999")).isEqualTo(
      Value.errorMessage("1e999999999 has more than 1000 digits", BigInteger.class)
    );
  }

  @Test
  void fractionalValueForIntegerTargetIsAnError() {
    var result = Value.fromSymbol("int", "0.5");

    assertThat(result.error()).get(THROWABLE).hasMessageContaining("is not an integer");
  }

  @Test
  void doubleOverflowIsAnError() {
    var result = Value.fromSymbol("double", "1e400");

    assertThat(result.error()).get(THROWABLE).hasMessageContaining("overflows double");
  }

  @Test
  void invalidTextForAStringConstructedTypeIsAnError() {
    var result = Value.fromSymbol("instant", "not-a-timestamp");

    assertThat(result.error()).isPresent();
  }

  @Test
  void unknownSymbolIsAnError() {
    var result = Value.fromSymbol("nope", "x");

    assertThat(result.error()).get(THROWABLE).hasMessageContaining("Unknown type symbol");
  }
}
