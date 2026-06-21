package dev.zems.lib.value.marshal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.ValueState;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contract test for the {@link AbstractStateReader} STREAMING-only hooks (`doHasMoreRecords` /
 * `doConsumeRecordSeparator`).
 *
 * <p>
 * The hooks default to throwing {@link UnsupportedOperationException} with a documented message, on the contract that a
 * subclass declaring {@link Protocol.Mode#STREAMING} support must override them. The base class's runtime mode-guard
 * makes the UOE unreachable in production (both built-in readers override both hooks). This test pins the diagnostic
 * by constructing a contrived reader that declares STREAMING support but deliberately does not override the hooks —
 * if someone ever drops the runtime guard, the UOE arm becomes reachable and this test breaks loudly.
 */
@ContractTest
@DisplayName("AbstractStateReader streaming-only hooks")
class AbstractStateReaderHooksTest {

  @Test
  @DisplayName("hasMoreRecords() throws UOE when subclass declared STREAMING but did not override doHasMoreRecords")
  void hasMoreRecordsPropagatesUoe() {
    var reader = new HookedReader(Protocol.V1.streaming());
    assertThatThrownBy(reader::hasMoreRecords)
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("HookedReader declares STREAMING support but did not override doHasMoreRecords");
  }

  @Test
  @DisplayName(
    "consumeRecordSeparator() throws UOE when subclass declared STREAMING but did not override doConsumeRecordSeparator"
  )
  void consumeRecordSeparatorPropagatesUoe() {
    var reader = new HookedReader(Protocol.V1.streaming());
    assertThatThrownBy(reader::consumeRecordSeparator)
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("HookedReader declares STREAMING support but did not override doConsumeRecordSeparator");
  }

  /**
   * A reader that declares STREAMING (and FRAMED) support but does NOT override either streaming-only hook —
   * deliberately broken so the test can probe the diagnostic.
   */
  private static final class HookedReader extends AbstractStateReader {

    HookedReader(Protocol protocol) {
      super(protocol, Set.of(Protocol.Mode.FRAMED, Protocol.Mode.STREAMING));
    }

    // The remaining abstract methods are unreachable on the assertion paths exercised here — they're only stubbed so
    // the class compiles. Returning defaults rather than throwing keeps the contract surface noise-free.

    @Override
    protected void doReadRecordOpen(int id, String name, TypeDescriptor<?> descriptor) {}

    @Override
    protected void doReadRecordClose(int id, String name, TypeDescriptor<?> descriptor) {}

    @Override
    protected Throwable doReadError(int id, String name) {
      return null;
    }

    @Override
    protected ValueState doPeekValueStateOrNull(int id, String name) {
      return null;
    }

    @Override
    protected void doEndNested(int id, String name) {}

    @Override
    protected void doBeginNested(int id, String name) {}

    @Override
    protected byte[] doReadBytes(int id, String name) {
      return new byte[0];
    }

    @Override
    protected String doReadString(int id, String name) {
      return "";
    }

    @Override
    protected double doReadDouble(int id, String name) {
      return 0;
    }

    @Override
    protected float doReadFloat(int id, String name) {
      return 0;
    }

    @Override
    protected long doReadLong(int id, String name) {
      return 0;
    }

    @Override
    protected int doReadInt(int id, String name) {
      return 0;
    }

    @Override
    protected short doReadShort(int id, String name) {
      return 0;
    }

    @Override
    protected char doReadChar(int id, String name) {
      return 0;
    }

    @Override
    protected boolean doReadBoolean(int id, String name) {
      return false;
    }

    @Override
    protected boolean doHasField(int id, String name) {
      return false;
    }
  }
}
