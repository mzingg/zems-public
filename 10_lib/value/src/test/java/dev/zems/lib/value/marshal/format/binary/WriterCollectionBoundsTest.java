package dev.zems.lib.value.marshal.format.binary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.ValueIo;
import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import dev.zems.lib.value.marshal.wire.WireConstraintViolationException;
import dev.zems.lib.value.marshal.wire.WireConstraints;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Writer-side enforcement of {@code maxArrayLength} / {@code maxMapEntries}: an over-large list or map is rejected at
 * write time, before any wire bytes are emitted, so it never produces bytes a conforming reader would reject.
 */
@DisplayName("Binary writer-side collection-bound enforcement")
@ContractTest
class WriterCollectionBoundsTest {

  @Test
  @DisplayName("a list larger than maxArrayLength is rejected at write time")
  void writerRejectsListExceedingMaxArrayLength() {
    var protocol = ValueIo.framed().withWireConstraints(WireConstraints.builder().maxArrayLength(2).build());
    TypeDescriptor<List<Value<Integer>>> descriptor = TypeDescriptor.ofList(
      "test.list",
      BuiltinTypeDescriptors.INTEGER
    );
    Value<List<Value<Integer>>> tooBig = Value.listOf(1, 2, 3);

    try (var w = protocol.binaryWriter(new ByteArrayOutputStream())) {
      assertThatThrownBy(() -> w.write(tooBig, descriptor))
        .isInstanceOf(WireConstraintViolationException.class)
        .hasMessageContaining("maxArrayLength");
    } catch (RuntimeException closeError) {
      // close() after a mid-write failure may rethrow — not the assertion target.
    }
  }

  @Test
  @DisplayName("a map larger than maxMapEntries is rejected at write time")
  void writerRejectsMapExceedingMaxMapEntries() {
    var protocol = ValueIo.framed().withWireConstraints(WireConstraints.builder().maxMapEntries(1).build());
    TypeDescriptor<Map<String, Value<Integer>>> descriptor = TypeDescriptor.ofMap(
      "test.map",
      BuiltinTypeDescriptors.STRING,
      BuiltinTypeDescriptors.INTEGER
    );
    Value<Map<String, Value<Integer>>> tooBig = Value.mapOf(Map.entry("a", 1), Map.entry("b", 2));

    try (var w = protocol.binaryWriter(new ByteArrayOutputStream())) {
      assertThatThrownBy(() -> w.write(tooBig, descriptor))
        .isInstanceOf(WireConstraintViolationException.class)
        .hasMessageContaining("maxMapEntries");
    } catch (RuntimeException closeError) {
      // close() after a mid-write failure may rethrow — not the assertion target.
    }
  }

  @Test
  @DisplayName("a list at the maxArrayLength bound writes and round-trips cleanly")
  void writerAcceptsListAtBound() {
    var protocol = ValueIo.framed().withWireConstraints(WireConstraints.builder().maxArrayLength(3).build());
    TypeDescriptor<List<Value<Integer>>> descriptor = TypeDescriptor.ofList(
      "test.list",
      BuiltinTypeDescriptors.INTEGER
    );
    Value<List<Value<Integer>>> atBound = Value.listOf(1, 2, 3);

    var bos = new ByteArrayOutputStream();
    try (var w = protocol.binaryWriter(bos)) {
      w.write(atBound, descriptor);
    }
    try (var r = protocol.binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
      assertThat(Value.unbox(r.read(descriptor))).containsExactly(Value.of(1), Value.of(2), Value.of(3));
    }
  }
}
