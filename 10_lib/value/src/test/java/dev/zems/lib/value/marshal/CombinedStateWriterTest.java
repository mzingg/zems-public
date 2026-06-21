package dev.zems.lib.value.marshal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ContractTest
@DisplayName("CombinedStateWriter")
class CombinedStateWriterTest {

  /** Each row: a Consumer that performs one write call + the expected journal entry it produces. */
  static Stream<Arguments> writeOperations() {
    TypeDescriptor<String> str = TypeDescriptor.of(String.class);
    return Stream.of(
      Arguments.of(
        "writeBoolean",
        (Consumer<StateWriter>) w -> w.writeBoolean(1, "b", true),
        "writeBoolean(1, b, true)"
      ),
      Arguments.of("writeChar", (Consumer<StateWriter>) w -> w.writeChar(2, "c", 'x'), "writeChar(2, c, x)"),
      Arguments.of("writeShort", (Consumer<StateWriter>) w -> w.writeShort(3, "s", (short) 7), "writeShort(3, s, 7)"),
      Arguments.of("writeInt", (Consumer<StateWriter>) w -> w.writeInt(4, "i", 42), "writeInt(4, i, 42)"),
      Arguments.of("writeLong", (Consumer<StateWriter>) w -> w.writeLong(5, "l", 99L), "writeLong(5, l, 99)"),
      Arguments.of("writeFloat", (Consumer<StateWriter>) w -> w.writeFloat(6, "f", 1.5f), "writeFloat(6, f, 1.5)"),
      Arguments.of("writeDouble", (Consumer<StateWriter>) w -> w.writeDouble(7, "d", 2.5), "writeDouble(7, d, 2.5)"),
      Arguments.of(
        "writeString",
        (Consumer<StateWriter>) w -> w.writeString(8, "str", "hi"),
        "writeString(8, str, hi)"
      ),
      Arguments.of("writeNull", (Consumer<StateWriter>) w -> w.writeNull(9, "n"), "writeNull(9, n)"),
      Arguments.of("writeUndefined", (Consumer<StateWriter>) w -> w.writeUndefined(10, "u"), "writeUndefined(10, u)"),
      Arguments.of(
        "writeUnresolved",
        (Consumer<StateWriter>) w -> w.writeUnresolved(11, "ur"),
        "writeUnresolved(11, ur)"
      ),
      Arguments.of("writeTombstone", (Consumer<StateWriter>) w -> w.writeTombstone(12, "t"), "writeTombstone(12, t)"),
      Arguments.of(
        "writeError",
        (Consumer<StateWriter>) w -> w.writeError(13, "e", new RuntimeException("boom")),
        "writeError(13, e, java.lang.RuntimeException: boom)"
      ),
      Arguments.of(
        "writeBytes",
        (Consumer<StateWriter>) w -> w.writeBytes(14, "by", new byte[] { 1, 2 }),
        "writeBytes(14, by, [1, 2])"
      ),
      Arguments.of(
        "writeRecord",
        (Consumer<StateWriter>) w -> w.writeRecord(15, "rec", str, "payload"),
        "writeRecord(15, rec, java.lang.String, payload)"
      ),
      Arguments.of(
        "writeHeader",
        (Consumer<StateWriter>) w -> w.writeHeader(str, "hdr"),
        "writeHeader(java.lang.String, hdr)"
      ),
      Arguments.of(
        "writeTerminator",
        (Consumer<StateWriter>) w -> w.writeTerminator(str, "term"),
        "writeTerminator(java.lang.String, term)"
      ),
      Arguments.of("beginNested", (Consumer<StateWriter>) w -> w.beginNested(20, "nest"), "beginNested(20, nest)"),
      Arguments.of("endNested", (Consumer<StateWriter>) w -> w.endNested(21, "nest"), "endNested(21, nest)")
    );
  }

  @Test
  @DisplayName("ctor rejects null writer list")
  void ctorRejectsNullList() {
    assertThatThrownBy(() -> new CombinedStateWriter((List<StateWriter>) null))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("writers must not be null");
  }

  @Test
  @DisplayName("ctor rejects empty writer list")
  void ctorRejectsEmptyList() {
    assertThatThrownBy(() -> new CombinedStateWriter(List.of()))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("writers must not be empty");
  }

  @Test
  @DisplayName("varargs ctor wraps to list")
  void varargsCtorWrapsToList() {
    var a = new JournalingStateWriter();
    var b = new JournalingStateWriter();
    var combined = new CombinedStateWriter(a, b);
    combined.writeInt(0, "n", 7);
    assertThat(a.journal()).containsExactly("writeInt(0, n, 7)");
    assertThat(b.journal()).containsExactly("writeInt(0, n, 7)");
  }

  @ParameterizedTest(name = "{0} fans out to every sub-writer")
  @MethodSource("writeOperations")
  void fansOutToEverySubWriter(String label, Consumer<StateWriter> op, String expectedEntry) {
    var a = new JournalingStateWriter();
    var b = new JournalingStateWriter();
    var c = new JournalingStateWriter();
    var combined = new CombinedStateWriter(List.of(a, b, c));

    op.accept(combined);

    assertThat(a.journal()).containsExactly(expectedEntry);
    assertThat(b.journal()).containsExactly(expectedEntry);
    assertThat(c.journal()).containsExactly(expectedEntry);
  }

  @Test
  @DisplayName("close() invokes close on every sub-writer")
  void closeInvokesCloseOnEverySubWriter() {
    var counters = new int[3];
    StateWriter a = new ClosingProbe(() -> counters[0]++);
    StateWriter b = new ClosingProbe(() -> counters[1]++);
    StateWriter c = new ClosingProbe(() -> counters[2]++);

    var combined = new CombinedStateWriter(List.of(a, b, c));
    combined.close();

    assertThat(counters).containsExactly(1, 1, 1);
  }

  /** Minimal probe — counts close() invocations; all other writes are no-ops. */
  private static final class ClosingProbe implements StateWriter {

    private final Runnable onClose;

    ClosingProbe(Runnable onClose) {
      this.onClose = onClose;
    }

    @Override
    public void writeBoolean(int id, String name, boolean value) {}

    @Override
    public void writeChar(int id, String name, char value) {}

    @Override
    public void writeShort(int id, String name, short value) {}

    @Override
    public void writeInt(int id, String name, int value) {}

    @Override
    public void writeLong(int id, String name, long value) {}

    @Override
    public void writeFloat(int id, String name, float value) {}

    @Override
    public void writeDouble(int id, String name, double value) {}

    @Override
    public void writeString(int id, String name, String value) {}

    @Override
    public void writeBytes(int id, String name, byte[] value) {}

    @Override
    public <H> void writeHeader(TypeDescriptor<H> descriptor, H header) {}

    @Override
    public <F> void writeTerminator(TypeDescriptor<F> descriptor, F terminator) {}

    @Override
    public void beginNested(int id, String name) {}

    @Override
    public void endNested(int id, String name) {}

    @Override
    public void writeNull(int id, String name) {}

    @Override
    public void writeUndefined(int id, String name) {}

    @Override
    public void writeUnresolved(int id, String name) {}

    @Override
    public void writeError(int id, String name, Throwable throwable) {}

    @Override
    public void writeTombstone(int id, String name) {}

    @Override
    public <T> void writeRecord(int id, String name, TypeDescriptor<T> descriptor, T value) {}

    @Override
    public void close() {
      onClose.run();
    }
  }
}
