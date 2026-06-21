package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ErrorValue")
@ContractTest
class ErrorValueTest {

  @Nested
  @DisplayName("havingThrown")
  class HavingThrown {

    @Test
    @DisplayName("creates ErrorValue with the given throwable and expected type")
    void createsErrorValueWithGivenThrowable() {
      var exception = new RuntimeException("test error");
      var errorValue = Value.errorOf(exception, String.class);

      assertThat(errorValue).isInstanceOf(ErrorValue.class);
      assertThat(((ErrorValue<?>) errorValue).throwable()).isSameAs(exception);
      assertThat(((ErrorValue<?>) errorValue).expectedType().qualifiedName()).isEqualTo("java.lang.String");
      assertThat(errorValue.error()).containsSame(exception);
    }

    @Test
    @DisplayName("preserves collection element type via TypeDescriptor")
    void preservesCollectionElementType() {
      var listDesc = TypeDescriptor.ofList("List<String>", TypeDescriptor.of("java.lang.String", String.class));
      var errorValue = Value.errorOf(new RuntimeException("boom"), listDesc);

      assertThat(((ErrorValue<?>) errorValue).expectedType()).isSameAs(listDesc);
      assertThat(((ErrorValue<?>) errorValue).expectedType().qualifiedName()).isEqualTo("List<java.lang.String>");
    }

    @Test
    @DisplayName("throws IllegalArgumentException for null throwable")
    void throwsForNullThrowable() {
      assertThatThrownBy(() -> Value.errorOf(null, String.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Error cannot be null");
    }

    @Test
    @DisplayName("throws IllegalArgumentException for null expected type")
    void throwsForNullExpectedType() {
      assertThatThrownBy(() -> Value.errorOf(new RuntimeException("x"), (Class<String>) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expected type cannot be null");
    }

    @Test
    @DisplayName("throws IllegalArgumentException for null TypeDescriptor")
    void throwsForNullTypeDescriptor() {
      assertThatThrownBy(() -> Value.errorOf(new RuntimeException("x"), (TypeDescriptor<String>) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expected type cannot be null");
    }
  }

  @Nested
  @DisplayName("message")
  class Message {

    @Test
    @DisplayName("creates ErrorValue with IllegalStateException containing the message")
    void createsErrorValueWithMessage() {
      var errorValue = Value.errorMessage("something went wrong", String.class);

      assertThat(errorValue.error())
        .get(InstanceOfAssertFactories.THROWABLE)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("something went wrong");
      assertThat(((ErrorValue<?>) errorValue).expectedType().qualifiedName()).isEqualTo("java.lang.String");
    }

    @Test
    @DisplayName("throws IllegalArgumentException for null message")
    void throwsForNull() {
      assertThatThrownBy(() -> Value.errorMessage(null, String.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Error cannot be null or empty");
    }

    @Test
    @DisplayName("throws IllegalArgumentException for blank string")
    void throwsForBlank() {
      assertThatThrownBy(() -> Value.errorMessage(" ", String.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Error cannot be null or empty");
    }

    @Test
    @DisplayName("throws IllegalArgumentException for null expected type")
    void throwsForNullExpectedType() {
      assertThatThrownBy(() -> Value.errorMessage("x", (Class<String>) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expected type cannot be null");
    }
  }

  @Nested
  @DisplayName("state accessors")
  class StateAccessors {

    @Test
    @DisplayName("isDefined returns true")
    void isDefinedReturnsTrue() {
      assertThat(Value.errorMessage("an error", String.class).isDefined()).isTrue();
    }

    @Test
    @DisplayName("isNotNull returns true")
    void isNotNullReturnsTrue() {
      assertThat(Value.errorMessage("an error", String.class).isNotNull()).isTrue();
    }

    @Test
    @DisplayName("isResolved returns true")
    void isResolvedReturnsTrue() {
      assertThat(Value.errorMessage("an error", String.class).isResolved()).isTrue();
    }

    @Test
    @DisplayName("error returns non-empty Optional")
    void errorReturnsNonEmpty() {
      assertThat(Value.errorMessage("an error", String.class).error()).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("toString")
  class ToStringTest {

    @Test
    @DisplayName("returns class name with throwable and expected-type descriptor")
    void returnsClassNameWithErrorMessage() {
      var errorValue = Value.errorMessage("something went wrong", String.class);

      assertThat(errorValue.toString())
        .startsWith("ErrorValue[throwable=java.lang.IllegalStateException: something went wrong, expectedType=")
        .contains("java.lang.String");
    }
  }

  @Nested
  @DisplayName("equals and hashCode")
  class EqualsAndHashCode {

    @Test
    @DisplayName("equal when same throwable class, message, and expected type")
    void equalWhenSameClassAndMessage() {
      var error1 = Value.errorOf(new RuntimeException("test"), String.class);
      var error2 = Value.errorOf(new RuntimeException("test"), String.class);

      assertThat(error1).isEqualTo(error2);
      assertThat(error1.hashCode()).isEqualTo(error2.hashCode());
    }

    @Test
    @DisplayName("not equal when different throwable class")
    void notEqualWhenDifferentClass() {
      var error1 = Value.errorOf(new RuntimeException("test"), String.class);
      var error2 = Value.errorOf(new IllegalStateException("test"), String.class);

      assertThat(error1).isNotEqualTo(error2);
    }

    @Test
    @DisplayName("not equal when different message")
    void notEqualWhenDifferentMessage() {
      var error1 = Value.errorOf(new RuntimeException("error A"), String.class);
      var error2 = Value.errorOf(new RuntimeException("error B"), String.class);

      assertThat(error1).isNotEqualTo(error2);
    }

    @SuppressWarnings("AssertBetweenInconvertibleTypes")
    @Test
    @DisplayName("not equal when different expected type")
    void notEqualWhenDifferentExpectedType() {
      var error1 = Value.errorOf(new RuntimeException("test"), String.class);
      var error2 = Value.errorOf(new RuntimeException("test"), Integer.class);

      assertThat(error1).isNotEqualTo(error2);
    }

    @Test
    @DisplayName("equal when both have null message")
    void equalWhenBothHaveNullMessage() {
      var error1 = Value.errorOf(new RuntimeException((String) null), String.class);
      var error2 = Value.errorOf(new RuntimeException((String) null), String.class);

      assertThat(error1).isEqualTo(error2);
      assertThat(error1.hashCode()).isEqualTo(error2.hashCode());
    }

    @Test
    @DisplayName("ErrorValues created via message() are equal for same message and expected type")
    void messageFactoryProducesEqualValues() {
      var error1 = Value.errorMessage("same error", String.class);
      var error2 = Value.errorMessage("same error", String.class);

      assertThat(error1).isEqualTo(error2);
    }

    @Test
    @DisplayName("ScalarTypeDescriptor and Class-based factory produce equal ErrorValues")
    void classAndDescriptorFactoriesAreEquivalent() {
      var fromClass = Value.errorOf(new RuntimeException("x"), String.class);
      var fromDescriptor = Value.errorOf(
        new RuntimeException("x"),
        TypeDescriptor.of("java.lang.String", String.class)
      );

      assertThat(fromClass).isEqualTo(fromDescriptor);
    }
  }
}
