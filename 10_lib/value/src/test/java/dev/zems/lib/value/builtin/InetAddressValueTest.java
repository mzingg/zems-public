package dev.zems.lib.value.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.ErrorValue;
import dev.zems.lib.value.Value;
import java.net.InetAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("InetAddressValue")
@ContractTest
class InetAddressValueTest {

  private static final String SAMPLE_IPV4 = "192.168.1.1";
  private static final String SAMPLE_IPV6 = "2001:db8::1";
  private static final InetAddress SAMPLE = InetAddress.ofLiteral(SAMPLE_IPV4);

  @Nested
  @DisplayName("of(InetAddress)")
  class OfInetAddress {

    @Test
    void createsWithProvided() {
      assertThat(new InetAddressValue(SAMPLE).inetAddress()).isEqualTo(SAMPLE);
    }

    @Test
    void throwsForNull() {
      assertThatThrownBy(() -> new InetAddressValue(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("InetAddress must not be null");
    }
  }

  @Nested
  @DisplayName("of(String)")
  class OfString {

    @Test
    void createsFromIpv4() {
      var result = Value.inetAddressOf(SAMPLE_IPV4);
      assertThat(result).isInstanceOf(InetAddressValue.class);
      assertThat(((InetAddressValue) result).inetAddress().getHostAddress()).isEqualTo(SAMPLE_IPV4);
    }

    @Test
    void createsFromIpv6() {
      var result = Value.inetAddressOf(SAMPLE_IPV6);
      assertThat(result).isInstanceOf(InetAddressValue.class);
      assertThat(((InetAddressValue) result).inetAddress()).isEqualTo(InetAddress.ofLiteral(SAMPLE_IPV6));
    }

    @Test
    void errorForNullString() {
      var result = Value.inetAddressOf((String) null);
      assertThat(result).isInstanceOf(ErrorValue.class);
      assertThat(result.error())
        .get()
        .extracting(Throwable::getMessage)
        .isEqualTo("InetAddress literal must not be null or empty");
    }

    @Test
    void errorForBlank() {
      var result = Value.inetAddressOf("  ");
      assertThat(result).isInstanceOf(ErrorValue.class);
      assertThat(result.error())
        .get()
        .extracting(Throwable::getMessage)
        .isEqualTo("InetAddress literal must not be null or empty");
    }

    @Test
    void errorForInvalid() {
      var result = Value.inetAddressOf("not-an-address");
      assertThat(result).isInstanceOf(ErrorValue.class);
      assertThat(result.error()).get().isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("state accessors")
  class StateAccessors {

    @Test
    void isDefinedReturnsTrue() {
      assertThat(new InetAddressValue(SAMPLE).isDefined()).isTrue();
    }

    @Test
    void isNotNullReturnsTrue() {
      assertThat(new InetAddressValue(SAMPLE).isNotNull()).isTrue();
    }

    @Test
    void isResolvedReturnsTrue() {
      assertThat(new InetAddressValue(SAMPLE).isResolved()).isTrue();
    }

    @Test
    void errorReturnsEmpty() {
      assertThat(new InetAddressValue(SAMPLE).error()).isEmpty();
    }
  }

  @Nested
  @DisplayName("equals and hashCode")
  class EqualsAndHashCode {

    @Test
    void equalValues() {
      assertThat(new InetAddressValue(SAMPLE)).isEqualTo(new InetAddressValue(SAMPLE));
      assertThat(new InetAddressValue(SAMPLE).hashCode()).isEqualTo(new InetAddressValue(SAMPLE).hashCode());
    }

    @Test
    void differentValues() {
      assertThat(Value.inetAddressOf("192.168.1.1")).isNotEqualTo(Value.inetAddressOf("10.0.0.1"));
    }
  }

  @Nested
  @DisplayName("toString")
  class ToStringTest {

    @Test
    void formattedString() {
      assertThat(new InetAddressValue(SAMPLE).toString()).isEqualTo("InetAddressValue[inetAddress=" + SAMPLE + "]");
    }
  }

  @Nested
  @DisplayName("round-trip")
  class RoundTrip {

    @Test
    void parseFromHostAddressRoundTrips() {
      var original = new InetAddressValue(SAMPLE);
      assertThat(Value.inetAddressOf(original.inetAddress().getHostAddress())).isEqualTo(original);
    }
  }
}
