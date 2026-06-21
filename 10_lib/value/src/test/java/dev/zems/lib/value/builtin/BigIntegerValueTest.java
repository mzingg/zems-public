package dev.zems.lib.value.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.ErrorValue;
import dev.zems.lib.value.Value;
import java.math.BigInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("BigIntegerValue")
@ContractTest
class BigIntegerValueTest {

  private static final String SAMPLE_STRING = "12345678901234567890";
  private static final BigInteger SAMPLE = new BigInteger(SAMPLE_STRING);

  @Nested
  @DisplayName("of(BigInteger)")
  class OfBigInteger {

    @Test
    void createsWithProvided() {
      assertThat(new BigIntegerValue(SAMPLE).bigInteger()).isEqualTo(SAMPLE);
    }

    @Test
    void throwsForNull() {
      assertThatThrownBy(() -> new BigIntegerValue(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("BigInteger must not be null");
    }
  }

  @Nested
  @DisplayName("of(String)")
  class OfString {

    @Test
    void createsFromString() {
      var result = Value.bigIntegerOf(SAMPLE_STRING);
      assertThat(result).isInstanceOf(BigIntegerValue.class);
      assertThat(((BigIntegerValue) result).bigInteger()).isEqualTo(SAMPLE);
    }

    @Test
    void errorForNullString() {
      var result = Value.bigIntegerOf((String) null);
      assertThat(result).isInstanceOf(ErrorValue.class);
      assertThat(result.error())
        .get()
        .extracting(Throwable::getMessage)
        .isEqualTo("BigInteger string must not be null or empty");
    }

    @Test
    void errorForBlank() {
      var result = Value.bigIntegerOf("  ");
      assertThat(result).isInstanceOf(ErrorValue.class);
      assertThat(result.error())
        .get()
        .extracting(Throwable::getMessage)
        .isEqualTo("BigInteger string must not be null or empty");
    }

    @Test
    void errorForInvalid() {
      var result = Value.bigIntegerOf("not-a-number");
      assertThat(result).isInstanceOf(ErrorValue.class);
      assertThat(result.error()).get().isInstanceOf(NumberFormatException.class);
    }
  }

  @Nested
  @DisplayName("state accessors")
  class StateAccessors {

    @Test
    void isDefinedReturnsTrue() {
      assertThat(new BigIntegerValue(SAMPLE).isDefined()).isTrue();
    }

    @Test
    void isNotNullReturnsTrue() {
      assertThat(new BigIntegerValue(SAMPLE).isNotNull()).isTrue();
    }

    @Test
    void isResolvedReturnsTrue() {
      assertThat(new BigIntegerValue(SAMPLE).isResolved()).isTrue();
    }

    @Test
    void errorReturnsEmpty() {
      assertThat(new BigIntegerValue(SAMPLE).error()).isEmpty();
    }
  }

  @Nested
  @DisplayName("equals and hashCode")
  class EqualsAndHashCode {

    @Test
    void equalValues() {
      assertThat(new BigIntegerValue(SAMPLE)).isEqualTo(new BigIntegerValue(SAMPLE));
      assertThat(new BigIntegerValue(SAMPLE).hashCode()).isEqualTo(new BigIntegerValue(SAMPLE).hashCode());
    }

    @Test
    void differentValues() {
      assertThat(new BigIntegerValue(SAMPLE)).isNotEqualTo(new BigIntegerValue(SAMPLE.add(BigInteger.ONE)));
    }
  }

  @Nested
  @DisplayName("toString")
  class ToStringTest {

    @Test
    void formattedString() {
      assertThat(new BigIntegerValue(SAMPLE).toString()).isEqualTo("BigIntegerValue[bigInteger=" + SAMPLE_STRING + "]");
    }
  }

  @Nested
  @DisplayName("round-trip")
  class RoundTrip {

    @Test
    void parseToStringRoundTrips() {
      var original = new BigIntegerValue(SAMPLE);
      assertThat(Value.bigIntegerOf(original.bigInteger().toString())).isEqualTo(original);
    }
  }
}
