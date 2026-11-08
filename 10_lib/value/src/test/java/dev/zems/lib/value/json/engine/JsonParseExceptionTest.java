package dev.zems.lib.value.json.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JsonParseException")
@ContractTest
class JsonParseExceptionTest {

  @Test
  @DisplayName("line and position 0 are the first line and its first position")
  void acceptsZeroLineAndPosition() {
    JsonParseException exception = new JsonParseException("cannot parse", 0, 0);

    assertThat(exception).hasMessage("cannot parse");
    assertThat(exception.getErrorLine()).isZero();
    assertThat(exception.getErrorPosition()).isZero();
  }

  @Test
  @DisplayName("a negative line or position is refused (N2)")
  void refusesNegativeLineOrPosition() {
    assertThatThrownBy(() -> new JsonParseException("cannot parse", -1, 0))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("line and position must not be negative, got line -1, position 0");
    assertThatThrownBy(() -> new JsonParseException("cannot parse", 0, -2))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("line and position must not be negative, got line 0, position -2");
    assertThatThrownBy(() -> new JsonParseException("cannot parse", -3, -4))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("line and position must not be negative, got line -3, position -4");
  }
}
