package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.ContractTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("UndefinedValue")
@ContractTest
class UndefinedValueTest {

  @Nested
  @DisplayName("singleton")
  class Singleton {

    @Test
    @DisplayName("instance returns same object regardless of type parameter")
    void instanceReturnsSameObject() {
      UndefinedValue<?> stringUndefined = UndefinedValue.<String>instance();
      UndefinedValue<?> integerUndefined = UndefinedValue.<Integer>instance();
      assertThat(stringUndefined).isSameAs(integerUndefined);
    }

    @Test
    @DisplayName("hashCode returns constant value")
    void hashCodeReturnsConstant() {
      assertThat(UndefinedValue.<String>instance().hashCode()).isEqualTo(UndefinedValue.<Integer>instance().hashCode());
    }

    @Test
    @DisplayName("toString returns class name with empty brackets")
    void toStringReturnsClassName() {
      assertThat(UndefinedValue.<String>instance().toString()).isEqualTo("UndefinedValue[]");
    }
  }

  @Nested
  @DisplayName("state accessors")
  class StateAccessors {

    @Test
    @DisplayName("isDefined returns false")
    void isDefinedReturnsFalse() {
      assertThat(UndefinedValue.<String>instance().isDefined()).isFalse();
    }

    @Test
    @DisplayName("isNotNull returns true")
    void isNotNullReturnsTrue() {
      assertThat(UndefinedValue.<String>instance().isNotNull()).isTrue();
    }

    @Test
    @DisplayName("isResolved returns true")
    void isResolvedReturnsTrue() {
      assertThat(UndefinedValue.<String>instance().isResolved()).isTrue();
    }

    @Test
    @DisplayName("error returns empty Optional")
    void errorReturnsEmpty() {
      assertThat(UndefinedValue.<String>instance().error()).isEmpty();
    }
  }
}
