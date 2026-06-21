package dev.zems.lib.value.marshal;

import dev.zems.lib.common._test.ContractTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Minimal contract test for {@link NoOpStateWriter}. The 14 silent write methods aren't worth enumerating — only
 * behaviours that aren't trivially "do nothing" go here.
 */
@ContractTest
@DisplayName("NoOpStateWriter")
class NoOpStateWriterTest {

  @Test
  @DisplayName("close() does not throw and is idempotent")
  void closeIsIdempotent() {
    var w = new NoOpStateWriter();
    w.close();
    w.close();
  }
}
