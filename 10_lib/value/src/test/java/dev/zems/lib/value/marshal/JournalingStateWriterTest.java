package dev.zems.lib.value.marshal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ContractTest
@DisplayName("JournalingStateWriter")
class JournalingStateWriterTest {

  /** Each row: a write call + its expected journal entry. */
  static Stream<Arguments> writeOperations() {
    TypeDescriptor<String> str = TypeDescriptor.of(String.class);
    return Stream.of(
      Arguments.of((Consumer<StateWriter>) w -> w.writeBoolean(0, "b", false), "writeBoolean(0, b, false)"),
      Arguments.of((Consumer<StateWriter>) w -> w.writeChar(1, "c", 'a'), "writeChar(1, c, a)"),
      Arguments.of((Consumer<StateWriter>) w -> w.writeShort(2, "s", (short) -3), "writeShort(2, s, -3)"),
      Arguments.of((Consumer<StateWriter>) w -> w.writeInt(3, "i", 100), "writeInt(3, i, 100)"),
      Arguments.of((Consumer<StateWriter>) w -> w.writeLong(4, "l", 1234567890L), "writeLong(4, l, 1234567890)"),
      Arguments.of((Consumer<StateWriter>) w -> w.writeFloat(5, "f", 3.14f), "writeFloat(5, f, 3.14)"),
      Arguments.of((Consumer<StateWriter>) w -> w.writeDouble(6, "d", 2.71), "writeDouble(6, d, 2.71)"),
      Arguments.of((Consumer<StateWriter>) w -> w.writeString(7, "str", "hello"), "writeString(7, str, hello)"),
      Arguments.of(
        (Consumer<StateWriter>) w -> w.writeBytes(8, "by", new byte[] { 0, 1, 2 }),
        "writeBytes(8, by, [0, 1, 2])"
      ),
      Arguments.of((Consumer<StateWriter>) w -> w.writeNull(9, "n"), "writeNull(9, n)"),
      Arguments.of((Consumer<StateWriter>) w -> w.writeUndefined(10, "u"), "writeUndefined(10, u)"),
      Arguments.of((Consumer<StateWriter>) w -> w.writeUnresolved(11, "ur"), "writeUnresolved(11, ur)"),
      Arguments.of((Consumer<StateWriter>) w -> w.writeTombstone(12, "t"), "writeTombstone(12, t)"),
      Arguments.of(
        (Consumer<StateWriter>) w -> w.writeError(13, "e", new IllegalStateException("bad")),
        "writeError(13, e, java.lang.IllegalStateException: bad)"
      ),
      Arguments.of(
        (Consumer<StateWriter>) w -> w.writeRecord(14, "rec", str, "payload"),
        "writeRecord(14, rec, java.lang.String, payload)"
      ),
      Arguments.of((Consumer<StateWriter>) w -> w.writeHeader(str, "hdr"), "writeHeader(java.lang.String, hdr)"),
      Arguments.of(
        (Consumer<StateWriter>) w -> w.writeTerminator(str, "term"),
        "writeTerminator(java.lang.String, term)"
      ),
      Arguments.of((Consumer<StateWriter>) w -> w.beginNested(20, "nest"), "beginNested(20, nest)"),
      Arguments.of((Consumer<StateWriter>) w -> w.endNested(21, "nest"), "endNested(21, nest)")
    );
  }

  @Test
  @DisplayName("journal is empty before any write")
  void journalEmptyInitially() {
    var w = new JournalingStateWriter();
    assertThat(w.journal()).isEmpty();
  }

  @Test
  @DisplayName("journal() returns an immutable copy")
  void journalReturnsImmutableCopy() {
    var w = new JournalingStateWriter();
    w.writeInt(0, "x", 1);
    var snapshot = w.journal();
    assertThatThrownBy(() -> snapshot.add("nope")).isInstanceOf(UnsupportedOperationException.class);
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("writeOperations")
  @DisplayName("each write method records the documented entry format")
  void eachWriteRecordsExpectedEntry(Consumer<StateWriter> op, String expected) {
    var w = new JournalingStateWriter();
    op.accept(w);
    assertThat(w.journal()).containsExactly(expected);
  }

  @Test
  @DisplayName("writeRecord records descriptor's descriptorName(), not its toString()")
  void writeRecordUsesDescriptorName() {
    var w = new JournalingStateWriter();
    var custom = TypeDescriptor.builder(String.class)
      .withName("MyAlias")
      .reader(r -> r.readString(0, "s"))
      .writer((sw, s) -> sw.writeString(0, "s", s))
      .build();
    w.writeRecord(0, "r", custom, "v");
    assertThat(w.journal()).containsExactly("writeRecord(0, r, MyAlias, v)");
  }

  @Test
  @DisplayName("close() appends a close() entry")
  void closeAppendsEntry() {
    var w = new JournalingStateWriter();
    w.writeInt(0, "x", 1);
    w.close();
    assertThat(w.journal()).containsExactly("writeInt(0, x, 1)", "close()");
  }

  @Test
  @DisplayName("concurrent writes from many threads do not lose entries")
  void concurrentWritesAreThreadSafe() throws InterruptedException {
    var w = new JournalingStateWriter();
    int threads = 4;
    int writesPerThread = 250;
    var start = new CountDownLatch(1);
    var done = new CountDownLatch(threads);
    for (int t = 0; t < threads; t++) {
      final int threadId = t;
      Thread.ofPlatform().start(() -> {
        try {
          start.await();
          for (int i = 0; i < writesPerThread; i++) {
            w.writeInt(threadId, "x", i);
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          done.countDown();
        }
      });
    }
    start.countDown();
    assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(w.journal()).hasSize(threads * writesPerThread);
  }
}
