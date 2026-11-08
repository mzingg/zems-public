package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("UnsupportedValue")
@ContractTest
class UnsupportedValueTest {

  @Nested
  @DisplayName("factory guards")
  class FactoryGuards {

    @Test
    @DisplayName("throws IllegalArgumentException for a null reason")
    void throwsForNullReason() {
      assertThatThrownBy(() -> Value.unsupported(null, Integer.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Reason cannot be null or empty");
    }

    @Test
    @DisplayName("throws IllegalArgumentException for a blank reason")
    void throwsForBlankReason() {
      assertThatThrownBy(() -> Value.unsupported("  ", Integer.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Reason cannot be null or empty");
    }

    @Test
    @DisplayName("throws IllegalArgumentException for a null expected class")
    void throwsForNullExpectedClass() {
      assertThatThrownBy(() -> Value.unsupported("no conversion exists", (Class<Integer>) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expected type cannot be null");
    }

    @Test
    @DisplayName("throws IllegalArgumentException for a null TypeDescriptor")
    void throwsForNullDescriptor() {
      assertThatThrownBy(() -> Value.unsupported("no conversion exists", (TypeDescriptor<Integer>) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expected type cannot be null");
    }
  }

  @Nested
  @DisplayName("expected type")
  class ExpectedType {

    @Test
    @DisplayName("the Class overload names the descriptor after the class")
    void classOverloadNamesDescriptorAfterTheClass() {
      Value<Integer> marker = Value.unsupported("no conversion exists", Integer.class);

      assertThat(((UnsupportedValue<Integer>) marker).expectedType().qualifiedName()).isEqualTo("java.lang.Integer");
    }

    @Test
    @DisplayName("valueType() answers the expected-type descriptor")
    void valueTypeAnswersTheExpectedType() {
      TypeDescriptor<Integer> descriptor = TypeDescriptor.of("java.lang.Integer", Integer.class);
      Value<Integer> marker = Value.unsupported("no conversion exists", descriptor);

      assertThat(marker.valueType()).isSameAs(descriptor);
    }

    @Test
    @DisplayName("a collection element type survives through the TypeDescriptor overload")
    void collectionElementTypeSurvives() {
      TypeDescriptor<List<Value<String>>> listDescriptor = TypeDescriptor.ofList(
        "List<String>",
        TypeDescriptor.of("java.lang.String", String.class)
      );
      Value<List<Value<String>>> marker = Value.unsupported("no conversion exists", listDescriptor);

      assertThat(((UnsupportedValue<List<Value<String>>>) marker).expectedType().qualifiedName()).isEqualTo(
        "List<java.lang.String>"
      );
    }
  }

  @Nested
  @DisplayName("equals and hashCode")
  class EqualsAndHashCode {

    @Test
    @DisplayName("equal when the reason and the expected type match")
    void equalWhenReasonAndTypeMatch() {
      Value<Integer> first = Value.unsupported("no conversion exists", Integer.class);
      Value<Integer> second = Value.unsupported("no conversion exists", Integer.class);

      assertThat(first).isEqualTo(second);
      assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @Test
    @DisplayName("not equal when the reason differs")
    void notEqualWhenReasonDiffers() {
      Value<Integer> first = Value.unsupported("no conversion exists", Integer.class);
      Value<Integer> second = Value.unsupported("units are incompatible", Integer.class);

      assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("not equal when the expected type differs")
    void notEqualWhenExpectedTypeDiffers() {
      Value<Integer> first = Value.unsupported("no conversion exists", Integer.class);
      Value<Long> second = Value.unsupported("no conversion exists", Long.class);

      assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("not equal to an ErrorValue carrying the same text and expected type")
    void notEqualToAnErrorCarryingTheSameText() {
      Value<Integer> unsupported = Value.unsupported("no conversion exists", Integer.class);
      Value<Integer> error = Value.errorMessage("no conversion exists", Integer.class);

      assertThat(unsupported).isNotEqualTo(error);
    }
  }
}
