package dev.zems.lib.value;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
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
    void isUnsupportedPassesForAnUnsupportedMarker() {
      assertThat(Value.unsupported("no conversion exists", String.class)).isUnsupported();
      assertThat(Value.errorMessage("boom", String.class)).isSupported();
      assertThat(Value.of("hi")).isSupported();
    }

    @Test
    void isUnsupportedFailsForAnError() {
      assertThatThrownBy(() -> assertThat(Value.errorMessage("boom", String.class)).isUnsupported())
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("unsupported");
    }

    @Test
    void isSupportedFailsForAnUnsupportedMarker() {
      assertThatThrownBy(() -> assertThat(Value.unsupported("no conversion exists", String.class)).isSupported())
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("supported");
    }

    @Test
    void isNullValueDistinctFromReferenceNull() {
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
      assertThat(Value.typeOf("com.example.Person")).hasTypeNameValue("com.example.Person");
    }

    @Test
    void hasTypeNameValueFailsForAPlainString() {
      assertThatThrownBy(() -> assertThat(Value.of("com.example.Person")).hasTypeNameValue("com.example.Person"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("a type name");
    }

    @Test
    void hasEnumValuePassesForTheSameConstant() {
      assertThat(Value.of(Status.ACTIVE)).hasEnumValue(Status.ACTIVE);
    }

    @Test
    void hasEnumValueFailsForAPlainString() {
      assertThatThrownBy(() -> assertThat(Value.of("ACTIVE")).hasEnumValue(Status.ACTIVE))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("an enum constant");
    }

    @Test
    void hasEnumValueFailsOnAnotherConstant() {
      assertThatThrownBy(() -> assertThat(Value.of(Status.ACTIVE)).hasEnumValue(Status.ARCHIVED))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("enum value")
        .hasMessageContaining("ARCHIVED");
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

    @Test
    @DisplayName("hasIntValue refuses a fractional double instead of truncating it")
    void hasIntValueRefusesFractionalDouble() {
      assertThatThrownBy(() -> assertThat(Value.of(3.9)).hasIntValue(3))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("3.9 is not a whole number");
    }

    @Test
    @DisplayName("hasIntValue refuses a long outside int range instead of narrowing it")
    void hasIntValueRefusesOutOfRangeLong() {
      assertThatThrownBy(() -> assertThat(Value.of(5_000_000_000L)).hasIntValue(705032704))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("5000000000 overflows Integer");
    }

    @Test
    @DisplayName("the numeric assertions still accept an exact cross-type read")
    void numericAssertionsAcceptExactConversions() {
      assertThat(Value.of(42L)).hasIntValue(42);
      assertThat(Value.of(42)).hasLongValue(42L);
      assertThat(Value.of(42)).hasDoubleValue(42.0);
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
    void hasUnsupportedReasonPasses() {
      Value<String> unsupported = Value.unsupported(
        "Cannot convert UUID to String: no conversion exists",
        String.class
      );

      assertThat(unsupported).hasUnsupportedReason("Cannot convert UUID to String: no conversion exists");
    }

    @Test
    void hasUnsupportedReasonFailsOnWrongReason() {
      Value<String> unsupported = Value.unsupported("no conversion exists", String.class);

      assertThatThrownBy(() -> assertThat(unsupported).hasUnsupportedReason("units are incompatible"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("units are incompatible");
    }

    @Test
    void hasExpectedTypeNameReadsAnUnsupportedMarkersDescriptor() {
      Value<String> unsupported = Value.unsupported("no conversion exists", String.class);

      assertThat(unsupported).hasExpectedTypeName("java.lang.String");
    }

    @Test
    void isErrorFailsForAnUnsupportedMarker() {
      assertThatThrownBy(() -> assertThat(Value.unsupported("no conversion exists", String.class)).isError())
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("ErrorValue");
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

  enum Status {
    ACTIVE,
    ARCHIVED,
  }
}
