package dev.zems.lib.value;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;

import dev.zems.lib.common.junit.ContractTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Value scalar predicates (isString / isBoolean / isUrl)")
@ContractTest
class ValueScalarPredicateTest {

  @Nested
  @DisplayName("isString")
  class IsString {

    @Test
    @DisplayName("returns true for NonNullValue wrapping String")
    void trueForString() {
      assertThat(Value.of("hello").isString()).isTrue();
    }

    @Test
    @DisplayName("returns false for NonNullValue wrapping Number")
    void falseForNumber() {
      assertThat(Value.of(42).isString()).isFalse();
    }

    @Test
    @DisplayName("returns false for UrlValue")
    void falseForUrl() {
      assertThat(Value.urlOf("https://example.com").isString()).isFalse();
    }

    @Test
    @DisplayName("returns false for state values")
    void falseForStateValues() {
      assertThat(Value.nullValue().isString()).isFalse();
      assertThat(Value.undefined().isString()).isFalse();
    }
  }

  @Nested
  @DisplayName("isBoolean")
  class IsBoolean {

    @Test
    @DisplayName("returns true for NonNullValue wrapping Boolean")
    void trueForBoolean() {
      assertThat(Value.of(true).isBoolean()).isTrue();
    }

    @Test
    @DisplayName("returns false for NonNullValue wrapping String")
    void falseForString() {
      assertThat(Value.of("true").isBoolean()).isFalse();
    }

    @Test
    @DisplayName("returns false for state values")
    void falseForStateValues() {
      assertThat(Value.undefined().isBoolean()).isFalse();
    }
  }

  @Nested
  @DisplayName("isUrl")
  class IsUrl {

    @Test
    @DisplayName("returns true for UrlValue")
    void trueForUrlValue() {
      assertThat(Value.urlOf("https://example.com").isUrl()).isTrue();
    }

    @Test
    @DisplayName("returns false for NonNullValue wrapping String")
    void falseForString() {
      assertThat(Value.of("https://example.com").isUrl()).isFalse();
    }

    @Test
    @DisplayName("returns false for state values")
    void falseForStateValues() {
      assertThat(Value.nullValue().isUrl()).isFalse();
    }
  }
}
