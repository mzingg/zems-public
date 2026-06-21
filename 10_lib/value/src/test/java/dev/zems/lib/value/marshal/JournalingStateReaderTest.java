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
@DisplayName("JournalingStateReader")
class JournalingStateReaderTest {

  /** Each row: a read call + its expected journal entry. */
  static Stream<Arguments> readOperations() {
    TypeDescriptor<String> str = TypeDescriptor.of(String.class);
    return Stream.of(
      Arguments.of((Consumer<StateReader>) r -> r.readBoolean(0, "b"), "readBoolean(0, b)"),
      Arguments.of((Consumer<StateReader>) r -> r.readChar(1, "c"), "readChar(1, c)"),
      Arguments.of((Consumer<StateReader>) r -> r.readShort(2, "s"), "readShort(2, s)"),
      Arguments.of((Consumer<StateReader>) r -> r.readInt(3, "i"), "readInt(3, i)"),
      Arguments.of((Consumer<StateReader>) r -> r.readLong(4, "l"), "readLong(4, l)"),
      Arguments.of((Consumer<StateReader>) r -> r.readFloat(5, "f"), "readFloat(5, f)"),
      Arguments.of((Consumer<StateReader>) r -> r.readDouble(6, "d"), "readDouble(6, d)"),
      Arguments.of((Consumer<StateReader>) r -> r.readString(7, "str"), "readString(7, str)"),
      Arguments.of((Consumer<StateReader>) r -> r.readBytes(8, "by"), "readBytes(8, by)"),
      Arguments.of((Consumer<StateReader>) r -> r.readRecord(9, "rec", str), "readRecord(9, rec, java.lang.String)"),
      Arguments.of((Consumer<StateReader>) r -> r.peekValueStateOrNull(10, "ps"), "peekValueStateOrNull(10, ps)"),
      Arguments.of((Consumer<StateReader>) r -> r.readError(11, "e"), "readError(11, e)"),
      Arguments.of((Consumer<StateReader>) r -> r.readHeader(str), "readHeader(java.lang.String)"),
      Arguments.of((Consumer<StateReader>) r -> r.readTerminator(str), "readTerminator(java.lang.String)"),
      Arguments.of((Consumer<StateReader>) r -> r.hasField(12, "h"), "hasField(12, h)"),
      Arguments.of((Consumer<StateReader>) r -> r.beginNested(20, "nest"), "beginNested(20, nest)"),
      Arguments.of((Consumer<StateReader>) r -> r.endNested(21, "nest"), "endNested(21, nest)")
    );
  }

  @Test
  @DisplayName("journal is empty before any read")
  void journalEmptyInitially() {
    var r = new JournalingStateReader();
    assertThat(r.journal()).isEmpty();
  }

  @Test
  @DisplayName("journal() returns an immutable copy")
  void journalReturnsImmutableCopy() {
    var r = new JournalingStateReader();
    r.readInt(0, "x");
    var snapshot = r.journal();
    assertThatThrownBy(() -> snapshot.add("nope")).isInstanceOf(UnsupportedOperationException.class);
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("readOperations")
  @DisplayName("each read method records the documented entry format")
  void eachReadRecordsExpectedEntry(Consumer<StateReader> op, String expected) {
    var r = new JournalingStateReader();
    op.accept(r);
    assertThat(r.journal()).containsExactly(expected);
  }

  @Test
  @DisplayName("close() appends a close() entry")
  void closeAppendsEntry() {
    var r = new JournalingStateReader();
    r.readInt(0, "x");
    r.close();
    assertThat(r.journal()).containsExactly("readInt(0, x)", "close()");
  }

  @Test
  @DisplayName("concurrent reads from many threads do not lose entries")
  void concurrentReadsAreThreadSafe() throws InterruptedException {
    var r = new JournalingStateReader();
    int threads = 4;
    int readsPerThread = 250;
    var start = new CountDownLatch(1);
    var done = new CountDownLatch(threads);
    for (int t = 0; t < threads; t++) {
      final int threadId = t;
      Thread.ofPlatform().start(() -> {
        try {
          start.await();
          for (int i = 0; i < readsPerThread; i++) {
            r.readInt(threadId, "x");
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
    assertThat(r.journal()).hasSize(threads * readsPerThread);
  }
}
