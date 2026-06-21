package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.ContractTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("UnresolvedValue")
@ContractTest
class UnresolvedValueTest {

  @Nested
  @DisplayName("singleton")
  class Singleton {

    @Test
    @DisplayName("instance returns same object regardless of type parameter")
    void instanceReturnsSameObject() {
      UnresolvedValue<?> stringUnresolved = UnresolvedValue.<String>instance();
      UnresolvedValue<?> integerUnresolved = UnresolvedValue.<Integer>instance();
      assertThat(stringUnresolved).isSameAs(integerUnresolved);
    }

    @Test
    @DisplayName("hashCode returns constant value")
    void hashCodeReturnsConstant() {
      assertThat(UnresolvedValue.<String>instance().hashCode()).isEqualTo(
        UnresolvedValue.<Integer>instance().hashCode()
      );
    }

    @Test
    @DisplayName("toString returns class name with empty brackets")
    void toStringReturnsClassName() {
      assertThat(UnresolvedValue.<String>instance().toString()).isEqualTo("UnresolvedValue[]");
    }
  }

  @Nested
  @DisplayName("state accessors")
  class StateAccessors {

    @Test
    @DisplayName("isDefined returns true")
    void isDefinedReturnsTrue() {
      assertThat(UnresolvedValue.<String>instance().isDefined()).isTrue();
    }

    @Test
    @DisplayName("isNotNull returns true")
    void isNotNullReturnsTrue() {
      assertThat(UnresolvedValue.<String>instance().isNotNull()).isTrue();
    }

    @Test
    @DisplayName("isResolved returns false")
    void isResolvedReturnsFalse() {
      assertThat(UnresolvedValue.<String>instance().isResolved()).isFalse();
    }

    @Test
    @DisplayName("error returns empty Optional")
    void errorReturnsEmpty() {
      assertThat(UnresolvedValue.<String>instance().error()).isEmpty();
    }
  }
}
