package dev.zems.lib.value.marshal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Minimal contract test for {@link NoOpStateReader}. The dozen trivial-default read methods aren't worth enumerating —
 * only behaviours that aren't "return the zero value" go here.
 */
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
