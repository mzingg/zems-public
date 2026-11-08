package dev.zems.lib.value;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;

import dev.zems.lib.common.junit.ContractTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Value numeric predicate (isNumber)")
@ContractTest
class ValueNumericPredicateTest {

  @Test
  @DisplayName("returns true for a value wrapping Integer")
  void trueForInteger() {
    assertThat(Value.of(42).isNumber()).isTrue();
  }

  @Test
  @DisplayName("returns true for a value wrapping Double")
  void trueForDouble() {
    assertThat(Value.of(3.14).isNumber()).isTrue();
  }

  @Test
  @DisplayName("returns false for a value wrapping String — a numeric-looking string is not a number")
  void falseForString() {
    assertThat(Value.of("42").isNumber()).isFalse();
  }

  @Test
  @DisplayName("returns false for state values")
  void falseForStateValues() {
    assertThat(Value.nullValue().isNumber()).isFalse();
  }
}
