package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.ContractTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Value numeric accessors (isNumber / asInteger / asDouble)")
@ContractTest
class ValueNumericAccessorTest {

  @Nested
  @DisplayName("isNumber")
  class IsNumber {

    @Test
    @DisplayName("returns true for NonNullValue wrapping Integer")
    void trueForInteger() {
      assertThat(Value.of(42).isNumber()).isTrue();
    }

    @Test
    @DisplayName("returns true for NonNullValue wrapping Double")
    void trueForDouble() {
      assertThat(Value.of(3.14).isNumber()).isTrue();
    }

    @Test
    @DisplayName("returns false for NonNullValue wrapping String")
    void falseForString() {
      assertThat(Value.of("42").isNumber()).isFalse();
    }

    @Test
    @DisplayName("returns false for state values")
    void falseForStateValues() {
      assertThat(Value.nullValue().isNumber()).isFalse();
    }
  }

  @Nested
  @DisplayName("asInteger")
  class AsInteger {

    @Test
    @DisplayName("returns intValue from Number")
    void directNumber() {
      assertThat(Value.of(42).asInteger()).contains(42);
    }

    @Test
    @DisplayName("converts double to int via intValue")
    void fromDouble() {
      assertThat(Value.of(3.9).asInteger()).contains(3);
    }

    @Test
    @DisplayName("parses String to Integer")
    void fromString() {
      assertThat(Value.of("123").asInteger()).contains(123);
    }

    @Test
    @DisplayName("returns empty for non-numeric String")
    void emptyForNonNumericString() {
      assertThat(Value.of("abc").asInteger()).isEmpty();
    }

    @Test
    @DisplayName("returns empty for state values")
    void emptyForStateValues() {
      assertThat(Value.nullValue().asInteger()).isEmpty();
    }

    @Test
    @DisplayName("returns empty for Boolean")
    void emptyForBoolean() {
      assertThat(Value.of(true).asInteger()).isEmpty();
    }
  }

  @Nested
  @DisplayName("asDouble")
  class AsDouble {

    @Test
    @DisplayName("returns doubleValue from Number")
    void directNumber() {
      assertThat(Value.of(3.14).asDouble()).contains(3.14);
    }

    @Test
    @DisplayName("converts int to double")
    void fromInt() {
      assertThat(Value.of(42).asDouble()).contains(42.0);
    }

    @Test
    @DisplayName("parses String to Double")
    void fromString() {
      assertThat(Value.of("2.5").asDouble()).contains(2.5);
    }

    @Test
    @DisplayName("returns empty for non-numeric String")
    void emptyForNonNumericString() {
      assertThat(Value.of("abc").asDouble()).isEmpty();
    }

    @Test
    @DisplayName("returns empty for Boolean")
    void emptyForBoolean() {
      assertThat(Value.of(true).asDouble()).isEmpty();
    }

    @Test
    @DisplayName("returns empty for state values")
    void emptyForStateValues() {
      assertThat(Value.undefined().asDouble()).isEmpty();
    }
  }
}
