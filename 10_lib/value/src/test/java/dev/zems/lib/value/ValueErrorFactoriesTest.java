package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.ContractTest;
import java.net.URISyntaxException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Value error-factory helpers")
@ContractTest
class ValueErrorFactoriesTest {

  @Nested
  @DisplayName("typed factory null message")
  class TypedFactoryNullMessage {

    @Test
    @DisplayName("of((String) null) emits 'String must not be null' and preserves expected type")
    void emitsNullMessageWithExpectedType() {
      var error = (ErrorValue<String>) Value.of((String) null);

      assertThat(error.throwable()).hasMessage("String must not be null");
      assertThat(error.expectedType().qualifiedName()).isEqualTo("java.lang.String");
    }
  }

  @Nested
  @DisplayName("parser null-or-blank message")
  class ParserNullOrBlankMessage {

    @Test
    @DisplayName("uuidOf(null) emits 'UUID string must not be null or empty' and preserves expected type")
    void emitsBlankMessageWithExpectedType() {
      var error = (ErrorValue<UUID>) Value.uuidOf(null);

      assertThat(error.throwable()).hasMessage("UUID string must not be null or empty");
      assertThat(error.expectedType().qualifiedName()).isEqualTo("java.util.UUID");
    }
  }

  @Nested
  @DisplayName("call sites preserve original exception via errorOf(Throwable, Class)")
  class CatchBlockNormalisation {

    @Test
    @DisplayName("uuidOf with malformed input wraps the IllegalArgumentException itself")
    void uuidOfWrapsThrowable() {
      var error = (ErrorValue<UUID>) Value.uuidOf("not-a-uuid");

      assertThat(error.throwable()).isInstanceOf(IllegalArgumentException.class);
      assertThat(error.throwable().getMessage()).contains("not-a-uuid");
      assertThat(error.expectedType().qualifiedName()).isEqualTo("java.util.UUID");
    }

    @Test
    @DisplayName("urlOf with bad input wraps URISyntaxException")
    void urlOfWrapsThrowable() {
      var error = (ErrorValue<?>) Value.urlOf(":::");

      assertThat(error.throwable()).isInstanceOf(URISyntaxException.class);
    }
  }
}
