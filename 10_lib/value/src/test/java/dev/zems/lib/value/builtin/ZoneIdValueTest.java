package dev.zems.lib.value.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.ErrorValue;
import dev.zems.lib.value.Value;
import java.time.DateTimeException;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ZoneIdValue")
@ContractTest
class ZoneIdValueTest {

  private static final String SAMPLE_STRING = "Europe/Zurich";
  private static final ZoneId SAMPLE = ZoneId.of(SAMPLE_STRING);

  @Nested
  @DisplayName("of(ZoneId)")
  class OfZoneId {

    @Test
    void createsWithProvided() {
      assertThat(new ZoneIdValue(SAMPLE).zoneId()).isEqualTo(SAMPLE);
    }

    @Test
    void throwsForNull() {
      assertThatThrownBy(() -> new ZoneIdValue(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("ZoneId must not be null");
    }
  }

  @Nested
  @DisplayName("of(String)")
  class OfString {

    @Test
    void createsFromString() {
      var result = Value.zoneIdOf(SAMPLE_STRING);
      assertThat(result).isInstanceOf(ZoneIdValue.class);
      assertThat(((ZoneIdValue) result).zoneId()).isEqualTo(SAMPLE);
    }

    @Test
    void errorForNullString() {
      var result = Value.zoneIdOf(null);
      assertThat(result).isInstanceOf(ErrorValue.class);
      assertThat(result.error())
        .get()
        .extracting(Throwable::getMessage)
        .isEqualTo("ZoneId string must not be null or empty");
    }

    @Test
    void errorForBlank() {
      var result = Value.zoneIdOf("  ");
      assertThat(result).isInstanceOf(ErrorValue.class);
      assertThat(result.error())
        .get()
        .extracting(Throwable::getMessage)
        .isEqualTo("ZoneId string must not be null or empty");
    }

    @Test
    void errorForInvalid() {
      var result = Value.zoneIdOf("not-a-zone");
      assertThat(result).isInstanceOf(ErrorValue.class);
      assertThat(result.error()).get().isInstanceOf(DateTimeException.class);
    }
  }

  @Nested
  @DisplayName("state accessors")
  class StateAccessors {

    @Test
    void isDefinedReturnsTrue() {
      assertThat(new ZoneIdValue(SAMPLE).isDefined()).isTrue();
    }

    @Test
    void isNotNullReturnsTrue() {
      assertThat(new ZoneIdValue(SAMPLE).isNotNull()).isTrue();
    }

    @Test
    void isResolvedReturnsTrue() {
      assertThat(new ZoneIdValue(SAMPLE).isResolved()).isTrue();
    }

    @Test
    void errorReturnsEmpty() {
      assertThat(new ZoneIdValue(SAMPLE).error()).isEmpty();
    }
  }

  @Nested
  @DisplayName("equals and hashCode")
  class EqualsAndHashCode {

    @Test
    void equalValues() {
      assertThat(new ZoneIdValue(SAMPLE)).isEqualTo(new ZoneIdValue(SAMPLE));
      assertThat(new ZoneIdValue(SAMPLE).hashCode()).isEqualTo(new ZoneIdValue(SAMPLE).hashCode());
    }
  }

  @Nested
  @DisplayName("toString")
  class ToStringTest {

    @Test
    void formattedString() {
      assertThat(new ZoneIdValue(SAMPLE).toString()).isEqualTo("ZoneIdValue[zoneId=" + SAMPLE + "]");
    }
  }

  @Nested
  @DisplayName("round-trip")
  class RoundTrip {

    @Test
    void parseToStringRoundTrips() {
      var original = new ZoneIdValue(SAMPLE);
      assertThat(Value.zoneIdOf(original.zoneId().toString())).isEqualTo(original);
    }
  }
}
