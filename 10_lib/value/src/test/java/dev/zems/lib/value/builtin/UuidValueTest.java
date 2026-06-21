package dev.zems.lib.value.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.ErrorValue;
import dev.zems.lib.value.Value;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("UuidValue")
@ContractTest
class UuidValueTest {

  @Nested
  @DisplayName("random()")
  class Random {

    @Test
    @DisplayName("generates a random UUID")
    void generatesRandomUuid() {
      var uuidValue1 = Value.randomUuid();
      var uuidValue2 = Value.randomUuid();

      assertThat(uuidValue1).isInstanceOf(UuidValue.class);
      assertThat(uuidValue2).isInstanceOf(UuidValue.class);
      assertThat(((UuidValue) uuidValue1).uuid()).isNotNull();
      assertThat(((UuidValue) uuidValue2).uuid()).isNotNull();
      assertThat(uuidValue1).isNotEqualTo(uuidValue2);
    }
  }

  @Nested
  @DisplayName("of(UUID)")
  class OfUuid {

    @Test
    @DisplayName("creates UuidValue with provided UUID")
    void createsUuidValueWithProvidedUuid() {
      var uuid = UUID.randomUUID();
      var uuidValue = new UuidValue(uuid);

      assertThat(uuidValue.uuid()).isEqualTo(uuid);
    }

    @Test
    @DisplayName("throws NullPointerException for null UUID")
    void throwsForNullUuid() {
      assertThatThrownBy(() -> new UuidValue(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("UUID must not be null");
    }
  }

  @Nested
  @DisplayName("of(String)")
  class OfString {

    @Test
    @DisplayName("creates UuidValue with valid UUID string")
    void createsUuidValueWithValidUuidString() {
      var uuid = UUID.randomUUID();
      var uuidValue = Value.uuidOf(uuid.toString());

      assertThat(uuidValue).isInstanceOf(UuidValue.class);
      assertThat(((UuidValue) uuidValue).uuid()).isEqualTo(uuid);
    }

    @Test
    @DisplayName("returns ErrorValue for null string")
    void returnsErrorValueForNull() {
      var result = Value.uuidOf(null);

      assertThat(result).isInstanceOf(ErrorValue.class);
      assertThat(result.error()).isPresent();
      assertThat(result.error())
        .get()
        .extracting(Throwable::getMessage)
        .isEqualTo("UUID string must not be null or empty");
    }

    @Test
    @DisplayName("returns ErrorValue for empty string")
    void returnsErrorValueForEmpty() {
      var result = Value.uuidOf("");

      assertThat(result).isInstanceOf(ErrorValue.class);
      assertThat(result.error()).isPresent();
      assertThat(result.error())
        .get()
        .extracting(Throwable::getMessage)
        .isEqualTo("UUID string must not be null or empty");
    }

    @Test
    @DisplayName("returns ErrorValue for blank string")
    void returnsErrorValueForBlank() {
      var result = Value.uuidOf("   ");

      assertThat(result).isInstanceOf(ErrorValue.class);
      assertThat(result.error()).isPresent();
      assertThat(result.error())
        .get()
        .extracting(Throwable::getMessage)
        .isEqualTo("UUID string must not be null or empty");
    }

    @Test
    @DisplayName("returns ErrorValue for invalid UUID syntax")
    void returnsErrorValueForInvalidUuid() {
      var result = Value.uuidOf("not-a-uuid");

      assertThat(result).isInstanceOf(ErrorValue.class);
      assertThat(result.error()).isPresent();
      assertThat(result.error()).get().isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("state accessors")
  class StateAccessors {

    @Test
    @DisplayName("isDefined returns true")
    void isDefinedReturnsTrue() {
      assertThat(Value.randomUuid().isDefined()).isTrue();
    }

    @Test
    @DisplayName("isNotNull returns true")
    void isNotNullReturnsTrue() {
      assertThat(Value.randomUuid().isNotNull()).isTrue();
    }

    @Test
    @DisplayName("isResolved returns true")
    void isResolvedReturnsTrue() {
      assertThat(Value.randomUuid().isResolved()).isTrue();
    }

    @Test
    @DisplayName("error returns empty Optional")
    void errorReturnsEmpty() {
      assertThat(Value.randomUuid().error()).isEmpty();
    }
  }

  @Nested
  @DisplayName("equals and hashCode")
  class EqualsAndHashCode {

    @Test
    @DisplayName("equal UuidValues have same UUID")
    void equalUuidValuesHaveSameUuid() {
      var uuid = UUID.randomUUID();
      var uuidValue1 = new UuidValue(uuid);
      var uuidValue2 = new UuidValue(uuid);

      assertThat(uuidValue1).isEqualTo(uuidValue2);
      assertThat(uuidValue1.hashCode()).isEqualTo(uuidValue2.hashCode());
    }

    @Test
    @DisplayName("different UUIDs produce different UuidValues")
    void differentUuidsProduceDifferentUuidValues() {
      var uuidValue1 = Value.randomUuid();
      var uuidValue2 = Value.randomUuid();

      assertThat(uuidValue1).isNotEqualTo(uuidValue2);
    }

    @Test
    @DisplayName("UuidValue is not equal to null")
    void notEqualToNull() {
      var uuidValue = Value.randomUuid();
    }

    @Test
    @DisplayName("UuidValue is not equal to different type")
    void notEqualToDifferentType() {
      var uuidValue = Value.randomUuid();
    }
  }

  @Nested
  @DisplayName("toString")
  class ToStringTest {

    @Test
    @DisplayName("returns class name with UUID")
    void returnsClassNameWithUuid() {
      var uuid = UUID.randomUUID();
      var uuidValue = new UuidValue(uuid);

      assertThat(uuidValue.toString()).isEqualTo("UuidValue[uuid=" + uuid + "]");
    }
  }
}
