package dev.zems.lib.value.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.ErrorValue;
import dev.zems.lib.value.Value;
import java.util.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CurrencyValue")
@ContractTest
class CurrencyValueTest {

  private static final String SAMPLE_CODE = "USD";
  private static final Currency SAMPLE = Currency.getInstance(SAMPLE_CODE);

  @Nested
  @DisplayName("of(Currency)")
  class OfCurrency {

    @Test
    void createsWithProvided() {
      assertThat(new CurrencyValue(SAMPLE).currency()).isEqualTo(SAMPLE);
    }

    @Test
    void throwsForNull() {
      assertThatThrownBy(() -> new CurrencyValue(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Currency must not be null");
    }
  }

  @Nested
  @DisplayName("of(String)")
  class OfString {

    @Test
    void createsFromCode() {
      var result = Value.currencyOf(SAMPLE_CODE);
      assertThat(result).isInstanceOf(CurrencyValue.class);
      assertThat(((CurrencyValue) result).currency()).isEqualTo(SAMPLE);
    }

    @Test
    void errorForNullString() {
      var result = Value.currencyOf(null);
      assertThat(result).isInstanceOf(ErrorValue.class);
      assertThat(result.error())
        .get()
        .extracting(Throwable::getMessage)
        .isEqualTo("Currency code must not be null or empty");
    }

    @Test
    void errorForBlank() {
      var result = Value.currencyOf("  ");
      assertThat(result).isInstanceOf(ErrorValue.class);
      assertThat(result.error())
        .get()
        .extracting(Throwable::getMessage)
        .isEqualTo("Currency code must not be null or empty");
    }

    @Test
    void errorForInvalid() {
      var result = Value.currencyOf("XYZ123");
      assertThat(result).isInstanceOf(ErrorValue.class);
      assertThat(result.error()).get().isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("state accessors")
  class StateAccessors {

    @Test
    void isDefinedReturnsTrue() {
      assertThat(new CurrencyValue(SAMPLE).isDefined()).isTrue();
    }

    @Test
    void isNotNullReturnsTrue() {
      assertThat(new CurrencyValue(SAMPLE).isNotNull()).isTrue();
    }

    @Test
    void isResolvedReturnsTrue() {
      assertThat(new CurrencyValue(SAMPLE).isResolved()).isTrue();
    }

    @Test
    void errorReturnsEmpty() {
      assertThat(new CurrencyValue(SAMPLE).error()).isEmpty();
    }
  }

  @Nested
  @DisplayName("equals and hashCode")
  class EqualsAndHashCode {

    @Test
    void equalValues() {
      assertThat(new CurrencyValue(SAMPLE)).isEqualTo(new CurrencyValue(SAMPLE));
      assertThat(new CurrencyValue(SAMPLE).hashCode()).isEqualTo(new CurrencyValue(SAMPLE).hashCode());
    }

    @Test
    void differentValues() {
      assertThat(Value.currencyOf("USD")).isNotEqualTo(Value.currencyOf("EUR"));
    }
  }

  @Nested
  @DisplayName("toString")
  class ToStringTest {

    @Test
    void formattedString() {
      assertThat(new CurrencyValue(SAMPLE).toString()).isEqualTo("CurrencyValue[currency=" + SAMPLE_CODE + "]");
    }
  }

  @Nested
  @DisplayName("round-trip")
  class RoundTrip {

    @Test
    void parseFromCodeRoundTrips() {
      var original = new CurrencyValue(SAMPLE);
      assertThat(Value.currencyOf(original.currency().getCurrencyCode())).isEqualTo(original);
    }
  }
}
