package dev.zems.lib.value.marshal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.zems.lib.common._test.JourneyTest;
import dev.zems.lib.value.ValueState;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Contract test: every writer-side feed sequence must symmetrically pair with the matching reader-side feed sequence,
 * otherwise the writer's stored hex disagrees with the reader's recomputed hex and the framed close-time verification
 * throws.
 *
 * <p>
 * For each writer/reader pair the test writes a single-slot framed record under SHA-256, then reads it back. A passing
 * close means the digests matched on both sides; a mismatch (e.g. an unfed field on one side, a drift in the
 * {@link ChecksumComputation#feedThrowable} contract) surfaces as an {@link IllegalStateException} from the close.
 */
class ChecksumSymmetryTest {

  private static final String FRAME = "checksum-test";

  static Stream<SlotCase> slotCases() {
    var pairDescriptor = TypeDescriptor.of(Pair.class);

    return Stream.of(
      new SlotCase("boolean", w -> w.writeBoolean(0, "f", true), r -> assertThat(r.readBoolean(0, "f")).isTrue()),
      new SlotCase("char", w -> w.writeChar(0, "f", 'x'), r -> assertThat(r.readChar(0, "f")).isEqualTo('x')),
      new SlotCase(
        "short",
        w -> w.writeShort(0, "f", (short) 7),
        r -> assertThat(r.readShort(0, "f")).isEqualTo((short) 7)
      ),
      new SlotCase("int", w -> w.writeInt(0, "f", 42), r -> assertThat(r.readInt(0, "f")).isEqualTo(42)),
      new SlotCase("long", w -> w.writeLong(0, "f", 42L), r -> assertThat(r.readLong(0, "f")).isEqualTo(42L)),
      new SlotCase("float", w -> w.writeFloat(0, "f", 1.5f), r -> assertThat(r.readFloat(0, "f")).isEqualTo(1.5f)),
      new SlotCase("double", w -> w.writeDouble(0, "f", 1.5), r -> assertThat(r.readDouble(0, "f")).isEqualTo(1.5)),
      new SlotCase(
        "string",
        w -> w.writeString(0, "f", "hello"),
        r -> assertThat(r.readString(0, "f")).isEqualTo("hello")
      ),
      new SlotCase(
        "bytes",
        w -> w.writeBytes(0, "f", new byte[] { 1, 2, 3 }),
        r -> assertThat(r.readBytes(0, "f")).containsExactly(1, 2, 3)
      ),
      new SlotCase(
        "null marker",
        w -> w.writeNull(0, "f"),
        r -> assertThat(r.peekValueStateOrNull(0, "f")).isEqualTo(ValueState.NULL)
      ),
      new SlotCase(
        "undefined marker",
        w -> w.writeUndefined(0, "f"),
        r -> assertThat(r.peekValueStateOrNull(0, "f")).isEqualTo(ValueState.UNDEFINED)
      ),
      new SlotCase(
        "unresolved marker",
        w -> w.writeUnresolved(0, "f"),
        r -> assertThat(r.peekValueStateOrNull(0, "f")).isEqualTo(ValueState.UNRESOLVED)
      ),
      new SlotCase(
        "error with message",
        w -> w.writeError(0, "f", new RuntimeException("boom")),
        r -> {
          assertThat(r.peekValueStateOrNull(0, "f")).isEqualTo(ValueState.ERROR);
          var t = r.readError(0, "f");
          assertThat(t).isInstanceOf(SerializedThrowable.class);
          assertThat(t.getMessage()).isEqualTo("boom");
        }
      ),
      new SlotCase(
        "error without message",
        w -> w.writeError(0, "f", new RuntimeException()),
        r -> {
          assertThat(r.peekValueStateOrNull(0, "f")).isEqualTo(ValueState.ERROR);
          var t = r.readError(0, "f");
          assertThat(t).isInstanceOf(SerializedThrowable.class);
          // Wire format does not preserve the null/empty distinction for messages — we assert
          // round-trip symmetry of the checksum, not message-null preservation.
          assertThat(t.getMessage() == null || t.getMessage().isEmpty()).isTrue();
        }
      ),
      new SlotCase(
        "nested record",
        w -> w.writeRecord(0, "f", pairDescriptor, new Pair("a", 1)),
        r -> assertThat(r.readRecord(0, "f", pairDescriptor)).isEqualTo(new Pair("a", 1))
      )
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("slotCases")
  @JourneyTest(speakingId = "checksum-feed-sequence-symmetry", acceptance = "a1")
  void writerAndReaderFeedSequencesProduceMatchingChecksums(SlotCase slotCase) {
    var bos = new ByteArrayOutputStream();
    var protocol = ValueIo.framed().withChecksum(ChecksumAlgorithm.SHA_256);

    try (var w = protocol.binaryWriter(bos)) {
      slotCase.write().accept(w);
    }

    // Reader.close() (driven by try-with-resources) verifies the terminator's hex against the
    // reader-side ChecksumComputation. A feed-sequence drift surfaces as an exception here.
    assertThatCode(() -> {
      try (var r = protocol.binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        slotCase.read().accept(r);
      }
    }).doesNotThrowAnyException();
  }

  private record Pair(String first, int second) {}

  /** Pair of writer + reader actions exercising one (writer, reader) feed sequence. */
  private record SlotCase(String name, Consumer<StateWriter> write, Consumer<StateReader> read) {
    @Override
    public String toString() {
      return name;
    }
  }
}
