package dev.zems.lib.value.valueio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("NoOpStateReader")
class NoOpStateReaderTest {

  @Test
  @DisplayName("readError throws UnsupportedOperationException with documented message")
  void readErrorThrows() {
    var r = new NoOpStateReader();
    assertThatThrownBy(() -> r.readError(0, "any"))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("NoOpStateReader.readError");
  }

  @Test
  @DisplayName("readUnsupported throws UnsupportedOperationException with documented message")
  void readUnsupportedThrows() {
    var r = new NoOpStateReader();
    assertThatThrownBy(() -> r.readUnsupported(0, "any"))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("NoOpStateReader.readUnsupported");
  }

  @Test
  @DisplayName("hasField always returns false")
  void hasFieldReturnsFalse() {
    var r = new NoOpStateReader();
    assertThat(r.hasField(0, "any")).isFalse();
    assertThat(r.hasField(99, "another")).isFalse();
  }

  @Test
  @DisplayName("close() does not throw")
  void closeDoesNotThrow() {
    var r = new NoOpStateReader();
    r.close();
  }
}
