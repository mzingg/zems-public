package dev.zems.lib.value.valueio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.zems.lib.common.junit.JourneyTest;
import dev.zems.lib.value.ValueState;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Checksum feed symmetry between writer and reader")
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
        "tombstone marker",
        w -> w.writeTombstone(0, "f"),
        r -> assertThat(r.peekValueStateOrNull(0, "f")).isEqualTo(ValueState.TOMBSTONE)
      ),
      new SlotCase(
        "unsupported marker",
        w -> w.writeUnsupported(0, "f", "no conversion exists"),
        r -> {
          assertThat(r.peekValueStateOrNull(0, "f")).isEqualTo(ValueState.UNSUPPORTED);
          assertThat(r.readUnsupported(0, "f")).isEqualTo("no conversion exists");
        }
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
          assertThat(t.getMessage() == null || t.getMessage().isEmpty()).isTrue();
        }
      ),
      new SlotCase(
        "error re-written after a wire read",
        w -> w.writeError(0, "f", new SerializedThrowable("java.io.IOException", "boom")),
        r -> {
          assertThat(r.peekValueStateOrNull(0, "f")).isEqualTo(ValueState.ERROR);
          var t = r.readError(0, "f");
          assertThat(t)
            .isInstanceOfSatisfying(SerializedThrowable.class, st ->
              assertThat(st.originalClassName()).isEqualTo("java.io.IOException")
            )
            .hasMessage("boom");
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

    assertThatCode(() -> {
      try (var r = protocol.binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        slotCase.read().accept(r);
      }
    }).doesNotThrowAnyException();
  }

  private record Pair(String first, int second) {}

  private record SlotCase(String name, Consumer<StateWriter> write, Consumer<StateReader> read) {
    @Override
    public String toString() {
      return name;
    }
  }
}
