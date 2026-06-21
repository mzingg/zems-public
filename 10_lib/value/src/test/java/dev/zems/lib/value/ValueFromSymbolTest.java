package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.THROWABLE;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.builtin.CharacterValue;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("Value.fromSymbol")
class ValueFromSymbolTest {

  @Test
  void parsesStringBoolAndChar() {
    assertThat(Value.fromSymbol("string", "hello").asString()).hasValue("hello");
    assertThat(Value.fromSymbol("bool", "true").asBoolean()).hasValue(true);
    assertThat(Value.fromSymbol("char", "x")).isInstanceOf(CharacterValue.class);
  }

  @Test
  void parsesStrictNumericTypes() {
    assertThat(Value.fromSymbol("int", "42").asInteger()).hasValue(42);
    assertThat(Value.fromSymbol("long", "3000000000").asNumber()).hasValue(3000000000L);
    assertThat(Value.fromSymbol("bigint", "8080").asBigInteger()).hasValue(new BigInteger("8080"));
    assertThat(Value.fromSymbol("double", "0.5").asDouble()).hasValue(0.5);
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
    assertThat(Value.fromSymbol("long", "1_000_000").asNumber()).hasValue(1_000_000L);
  }

  @Test
  void parsesStringConstructedJdkTypes() {
    // asString() returns each type's canonical round-trippable form.
    assertThat(Value.fromSymbol("instant", "2026-06-04T10:00:00Z").asString()).hasValue("2026-06-04T10:00:00Z");
    assertThat(Value.fromSymbol("uuid", "00000000-0000-0000-0000-000000000001").asString()).hasValue(
      "00000000-0000-0000-0000-000000000001"
    );
    assertThat(Value.fromSymbol("ip", "192.168.1.1").asString()).hasValue("192.168.1.1");
    assertThat(Value.fromSymbol("url", "http://example.com").asString()).hasValue("http://example.com");
  }

  @Test
  void strictIntegerOverflowIsAnError() {
    var result = Value.fromSymbol("int", "3000000000");

    assertThat(result.error()).get(THROWABLE).hasMessageContaining("overflows int");
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
