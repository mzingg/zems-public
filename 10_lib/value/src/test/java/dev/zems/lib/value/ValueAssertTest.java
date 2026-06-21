package dev.zems.lib.value;

import static dev.zems.lib.value.ValueAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("ValueAssert")
class ValueAssertTest {

  @Nested
  @DisplayName("state")
  class State {

    @Test
    void statePassesForMatchingMarker() {
      assertThat(Value.undefined()).isUndefined();
      assertThat(Value.unresolved()).isUnresolved();
      assertThat(Value.nullValue()).isNullValue();
      assertThat(Value.tombstone()).isTombstone();
      assertThat(Value.errorMessage("boom", String.class)).isError();
      assertThat(Value.of("hi")).isPresent();
    }

    @Test
    void isUndefinedFailsForPresentValue() {
      assertThatThrownBy(() -> assertThat(Value.of("hi")).isUndefined())
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("undefined");
    }

    @Test
    void isPresentFailsForStateMarker() {
      assertThatThrownBy(() -> assertThat(Value.undefined()).isPresent())
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("present");
    }

    @Test
    void isNullValueDistinctFromReferenceNull() {
      // isNullValue() asserts the NullValue state — the value is a real object, not a null reference.
      assertThat(Value.nullValue()).isNullValue();
      assertThatThrownBy(() -> assertThat(Value.of("hi")).isNullValue()).isInstanceOf(AssertionError.class);
    }
  }

  @Nested
  @DisplayName("scalar content")
  class ScalarContent {

    @Test
    void contentChecksPass() {
      assertThat(Value.of("hello")).hasStringValue("hello");
      assertThat(Value.of(42)).hasIntValue(42);
      assertThat(Value.of(42L)).hasLongValue(42L);
      assertThat(Value.of(3.5)).hasDoubleValue(3.5);
      assertThat(Value.of(true)).hasBooleanValue(true);
      UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
      assertThat(Value.of(id)).hasUuidValue(id);
      URI uri = URI.create("https://example.com");
      assertThat(Value.of(uri)).hasUrlValue(uri);
    }

    @Test
    void hasStringValueFailsOnWrongContent() {
      assertThatThrownBy(() -> assertThat(Value.of("hello")).hasStringValue("world"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("world");
    }

    @Test
    void hasStringValueFailsOnWrongType() {
      assertThatThrownBy(() -> assertThat(Value.of(42)).hasStringValue("42"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("string");
    }
  }

  @Nested
  @DisplayName("collection content")
  class CollectionContent {

    @Test
    void listChecksPass() {
      var list = Value.listOf(Value.of("a"), Value.of("b"));
      assertThat(list).isList().hasSize(2).isListContaining(Value.of("a"));
    }

    @Test
    void mapChecksPass() {
      var map = Value.mapOf(Map.entry("k", Value.of("v")));
      assertThat(map).isMap().hasSize(1).containsEntry("k", Value.of("v"));
    }

    @Test
    void isListFailsForNonList() {
      assertThatThrownBy(() -> assertThat(Value.of("x")).isList())
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("ListValue");
    }
  }

  @Nested
  @DisplayName("errors")
  class Errors {

    @Test
    void errorChecksPass() {
      Value<String> error = Value.errorMessage("not found", String.class);
      assertThat(error).isError().hasErrorMessage("not found").hasErrorMessageContaining("found");
    }

    @Test
    void hasThrowableInstanceOfPasses() {
      Value<String> error = Value.errorOf(new IllegalStateException("bad"), String.class);
      assertThat(error).hasThrowableInstanceOf(IllegalStateException.class);
    }

    @Test
    void hasExpectedTypeNamePasses() {
      Value<String> error = Value.errorMessage("boom", String.class);
      assertThat(error).hasExpectedTypeName("java.lang.String");
    }

    @Test
    void hasErrorMessageFailsForNonError() {
      assertThatThrownBy(() -> assertThat(Value.of("hi")).hasErrorMessage("x"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("error");
    }
  }

  @Nested
  @DisplayName("navigation")
  class Navigation {

    @Test
    void extractingValueChainsOntoWrappedValue() {
      assertThat(Value.of("hello")).extractingValue().isEqualTo("hello");
    }

    @Test
    void extractingValueFailsForStateMarker() {
      assertThatThrownBy(() -> assertThat(Value.undefined()).extractingValue().isNotNull())
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("state marker");
    }

    @Test
    void extractingThrowableChainsOntoCause() {
      Value<String> error = Value.errorOf(new IllegalArgumentException("nope"), String.class);
      assertThat(error).extractingThrowable().isInstanceOf(IllegalArgumentException.class).hasMessage("nope");
    }
  }
}
