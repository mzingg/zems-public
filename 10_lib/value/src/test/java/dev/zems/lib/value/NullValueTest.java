package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.ContractTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("NullValue")
@ContractTest
class NullValueTest {

  @Nested
  @DisplayName("singleton")
  class Singleton {

    @Test
    @DisplayName("instance returns same object regardless of type parameter")
    void instanceReturnsSameObject() {
      NullValue<?> stringNull = NullValue.<String>instance();
      NullValue<?> integerNull = NullValue.<Integer>instance();
      assertThat(stringNull).isSameAs(integerNull);
    }

    @Test
    @DisplayName("hashCode returns constant value")
    void hashCodeReturnsConstant() {
      assertThat(NullValue.<String>instance().hashCode()).isEqualTo(NullValue.<Integer>instance().hashCode());
    }

    @Test
    @DisplayName("toString returns class name with empty brackets")
    void toStringReturnsClassName() {
      assertThat(NullValue.<String>instance().toString()).isEqualTo("NullValue[]");
    }
  }

  @Nested
  @DisplayName("state accessors")
  class StateAccessors {

    @Test
    @DisplayName("isDefined returns true")
    void isDefinedReturnsTrue() {
      assertThat(NullValue.<String>instance().isDefined()).isTrue();
    }

    @Test
    @DisplayName("isNotNull returns false")
    void isNotNullReturnsFalse() {
      assertThat(NullValue.<String>instance().isNotNull()).isFalse();
    }

    @Test
    @DisplayName("isResolved returns true")
    void isResolvedReturnsTrue() {
      assertThat(NullValue.<String>instance().isResolved()).isTrue();
    }

    @Test
    @DisplayName("error returns empty Optional")
    void errorReturnsEmpty() {
      assertThat(NullValue.<String>instance().error()).isEmpty();
    }
  }
}
