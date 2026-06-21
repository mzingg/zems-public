package dev.zems.lib.value.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.ErrorValue;
import dev.zems.lib.value.Value;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("BigDecimalValue")
@ContractTest
class BigDecimalValueTest {

  private static final String SAMPLE_STRING = "3.14159265358979323846";
  private static final BigDecimal SAMPLE = new BigDecimal(SAMPLE_STRING);

  @Nested
  @DisplayName("of(BigDecimal)")
  class OfBigDecimal {

    @Test
    void createsWithProvided() {
      assertThat(new BigDecimalValue(SAMPLE).bigDecimal()).isEqualTo(SAMPLE);
    }

    @Test
    void throwsForNull() {
      assertThatThrownBy(() -> new BigDecimalValue(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("BigDecimal must not be null");
    }
  }

  @Nested
  @DisplayName("of(String)")
  class OfString {

    @Test
    void createsFromString() {
      var result = Value.bigDecimalOf(SAMPLE_STRING);
      assertThat(result).isInstanceOf(BigDecimalValue.class);
      assertThat(((BigDecimalValue) result).bigDecimal()).isEqualTo(SAMPLE);
    }

    @Test
    void preservesScale() {
      assertThat(((BigDecimalValue) Value.bigDecimalOf("3.14")).bigDecimal().scale()).isEqualTo(2);
      assertThat(((BigDecimalValue) Value.bigDecimalOf("3.140")).bigDecimal().scale()).isEqualTo(3);
    }

    @Test
    void errorForNullString() {
      var result = Value.bigDecimalOf((String) null);
      assertThat(result).isInstanceOf(ErrorValue.class);
      assertThat(result.error())
        .get()
        .extracting(Throwable::getMessage)
        .isEqualTo("BigDecimal string must not be null or empty");
    }

    @Test
    void errorForBlank() {
      var result = Value.bigDecimalOf("  ");
      assertThat(result).isInstanceOf(ErrorValue.class);
      assertThat(result.error())
        .get()
        .extracting(Throwable::getMessage)
        .isEqualTo("BigDecimal string must not be null or empty");
    }

    @Test
    void errorForInvalid() {
      var result = Value.bigDecimalOf("not-a-number");
      assertThat(result).isInstanceOf(ErrorValue.class);
      assertThat(result.error()).get().isInstanceOf(NumberFormatException.class);
    }
  }

  @Nested
  @DisplayName("state accessors")
  class StateAccessors {

    @Test
    void isDefinedReturnsTrue() {
      assertThat(new BigDecimalValue(SAMPLE).isDefined()).isTrue();
    }

    @Test
    void isNotNullReturnsTrue() {
      assertThat(new BigDecimalValue(SAMPLE).isNotNull()).isTrue();
    }

    @Test
    void isResolvedReturnsTrue() {
      assertThat(new BigDecimalValue(SAMPLE).isResolved()).isTrue();
    }

    @Test
    void errorReturnsEmpty() {
      assertThat(new BigDecimalValue(SAMPLE).error()).isEmpty();
    }
  }

  @Nested
  @DisplayName("equals and hashCode")
  class EqualsAndHashCode {

    @Test
    void equalValues() {
      assertThat(new BigDecimalValue(SAMPLE)).isEqualTo(new BigDecimalValue(SAMPLE));
      assertThat(new BigDecimalValue(SAMPLE).hashCode()).isEqualTo(new BigDecimalValue(SAMPLE).hashCode());
    }

    @Test
    void differentScalesAreNotEqual() {
      // BigDecimal.equals considers scale: "3.14" != "3.140"
      assertThat(Value.bigDecimalOf("3.14")).isNotEqualTo(Value.bigDecimalOf("3.140"));
    }
  }

  @Nested
  @DisplayName("toString")
  class ToStringTest {

    @Test
    void formattedString() {
      assertThat(new BigDecimalValue(SAMPLE).toString()).isEqualTo("BigDecimalValue[bigDecimal=" + SAMPLE_STRING + "]");
    }
  }

  @Nested
  @DisplayName("round-trip")
  class RoundTrip {

    @Test
    void parseToStringRoundTrips() {
      var original = new BigDecimalValue(SAMPLE);
      assertThat(Value.bigDecimalOf(original.bigDecimal().toString())).isEqualTo(original);
    }
  }
}
