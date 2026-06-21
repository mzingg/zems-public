package dev.zems.lib.value.marshal.format.binary;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.Protocol;
import dev.zems.lib.value.marshal.StateWriter;
import dev.zems.lib.value.marshal.ValueIo;
import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Contract test for {@link SizingStateWriter}. The contract is "byte count matches what {@link BinaryStateWriter} would
 * emit for the same call sequence". Each parameterised row exercises one primitive/state/composite/structure path.
 */
@ContractTest
@DisplayName("SizingStateWriter")
class SizingStateWriterTest {

  /** Driver: same call pattern through both writers; byte counts must match exactly. */
  static Stream<Arguments> sizingScenarios() {
    return Stream.of(
      Arguments.of("primitive boolean", (Consumer<StateWriter>) w -> w.writeBoolean(0, "b", true)),
      Arguments.of("primitive char", (Consumer<StateWriter>) w -> w.writeChar(0, "c", 'x')),
      Arguments.of("primitive short", (Consumer<StateWriter>) w -> w.writeShort(0, "s", (short) 7)),
      Arguments.of("primitive int small id", (Consumer<StateWriter>) w -> w.writeInt(0, "i", 42)),
      Arguments.of("primitive int multi-byte varint id", (Consumer<StateWriter>) w -> w.writeInt(200, "i", 42)),
      Arguments.of("primitive long", (Consumer<StateWriter>) w -> w.writeLong(0, "l", 1234567890L)),
      Arguments.of("primitive float", (Consumer<StateWriter>) w -> w.writeFloat(0, "f", 3.14f)),
      Arguments.of("primitive double", (Consumer<StateWriter>) w -> w.writeDouble(0, "d", 2.71828)),
      Arguments.of("primitive string ASCII", (Consumer<StateWriter>) w -> w.writeString(0, "s", "hello")),
      Arguments.of("primitive string empty", (Consumer<StateWriter>) w -> w.writeString(0, "s", "")),
      Arguments.of("primitive string multi-byte UTF-8", (Consumer<StateWriter>) w -> w.writeString(0, "s", "héllo €")),
      Arguments.of("primitive bytes empty", (Consumer<StateWriter>) w -> w.writeBytes(0, "by", new byte[0])),
      Arguments.of("primitive bytes 4", (Consumer<StateWriter>) w -> w.writeBytes(0, "by", new byte[] { 1, 2, 3, 4 })),
      Arguments.of("state marker null", (Consumer<StateWriter>) w -> w.writeNull(0, "n")),
      Arguments.of("state marker undefined", (Consumer<StateWriter>) w -> w.writeUndefined(0, "u")),
      Arguments.of("state marker unresolved", (Consumer<StateWriter>) w -> w.writeUnresolved(0, "ur")),
      Arguments.of("state marker tombstone", (Consumer<StateWriter>) w -> w.writeTombstone(0, "t")),
      Arguments.of(
        "state marker error",
        (Consumer<StateWriter>) w -> w.writeError(0, "e", new IllegalStateException("bad"))
      ),
      Arguments.of(
        "state marker error null message",
        (Consumer<StateWriter>) w -> w.writeError(0, "e", new IllegalStateException())
      ),
      Arguments.of(
        "nested empty",
        (Consumer<StateWriter>) w -> {
          w.beginNested(0, "n");
          w.endNested(0, "n");
        }
      ),
      Arguments.of(
        "nested with primitive",
        (Consumer<StateWriter>) w -> {
          w.beginNested(0, "n");
          w.writeInt(1, "x", 42);
          w.endNested(0, "n");
        }
      ),
      Arguments.of(
        "nested twice",
        (Consumer<StateWriter>) w -> {
          w.beginNested(0, "outer");
          w.beginNested(0, "inner");
          w.writeString(1, "s", "hi");
          w.endNested(0, "inner");
          w.endNested(0, "outer");
        }
      )
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("sizingScenarios")
  @DisplayName("size() matches BinaryStateWriter byte count")
  void sizeMatchesBinaryByteCount(String label, Consumer<StateWriter> op) {
    var bos = new ByteArrayOutputStream();
    try (var bw = ValueIo.streaming().binaryWriter(bos)) {
      op.accept(bw);
    }

    var sw = ValueIo.streaming().sizingWriter();
    op.accept(sw);
    sw.close();

    assertThat(sw.size()).as(label).isEqualTo(bos.size());
  }

  @Test
  @DisplayName("high-level Value write — scalar — size matches binary")
  void highLevelScalarValueWriteSizeMatches() {
    Value<String> v = Value.of("payload");
    TypeDescriptor<String> d = BuiltinTypeDescriptors.STRING;

    var bos = new ByteArrayOutputStream();
    try (var bw = ValueIo.streaming().binaryWriter(bos)) {
      bw.write(v, d);
    }
    var sw = ValueIo.streaming().sizingWriter();
    sw.write(v, d);
    sw.close();

    assertThat(sw.size()).isEqualTo(bos.size());
  }

  @Test
  @DisplayName("high-level Value write — list — size matches binary")
  void highLevelListValueWriteSizeMatches() {
    var listDesc = TypeDescriptor.ofList("StringList", BuiltinTypeDescriptors.STRING);
    Value<List<Value<String>>> list = Value.listOf("a", "b", "c");

    var bos = new ByteArrayOutputStream();
    try (var bw = ValueIo.streaming().binaryWriter(bos)) {
      bw.write(list, listDesc);
    }
    var sw = ValueIo.streaming().sizingWriter();
    sw.write(list, listDesc);
    sw.close();

    assertThat(sw.size()).isEqualTo(bos.size());
  }

  @Test
  @DisplayName("high-level Value write — map — size matches binary")
  void highLevelMapValueWriteSizeMatches() {
    var mapDesc = TypeDescriptor.ofMap(
      "StringIntegerMap",
      BuiltinTypeDescriptors.STRING,
      BuiltinTypeDescriptors.INTEGER
    );
    Value<Map<String, Value<Integer>>> map = Value.mapOf(Map.entry("alpha", 1), Map.entry("beta", 2));

    var bos = new ByteArrayOutputStream();
    try (var bw = ValueIo.streaming().binaryWriter(bos)) {
      bw.write(map, mapDesc);
    }
    var sw = ValueIo.streaming().sizingWriter();
    sw.write(map, mapDesc);
    sw.close();

    assertThat(sw.size()).isEqualTo(bos.size());
  }

  @Test
  @DisplayName("reset() zeros the counter and clears nested-frame stack")
  void resetZerosCounterAndClearsFrames() {
    var sw = ValueIo.streaming().sizingWriter();
    sw.writeInt(0, "i", 42);
    sw.beginNested(1, "n");
    assertThat(sw.size()).isGreaterThan(0);

    sw.reset();
    assertThat(sw.size()).isZero();

    // After reset we can write a fresh sequence — no leftover frame state.
    sw.writeInt(0, "i", 1);
    assertThat(sw.size()).isGreaterThan(0);
  }

  @Test
  @DisplayName("ValueIo.framed().sizingWriter() supports framed mode")
  void framedModeAccepted() {
    var sw = ValueIo.framed().sizingWriter();
    sw.writeInt(0, "i", 42);
    sw.close();
    assertThat(sw.size()).isGreaterThan(0);
  }

  @Test
  @DisplayName("sizing() factory plugs into Protocol.writer")
  void sizingFactoryIsUsable() {
    var factory = SizingStateWriter.sizing();
    try (var sw = ValueIo.streaming().writer(factory)) {
      sw.writeInt(0, "i", 1);
      assertThat(sw.size()).isGreaterThan(0);
    }
  }
}
