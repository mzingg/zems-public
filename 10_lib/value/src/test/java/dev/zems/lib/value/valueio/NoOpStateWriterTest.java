package dev.zems.lib.value.valueio;

import dev.zems.lib.common.junit.ContractTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
