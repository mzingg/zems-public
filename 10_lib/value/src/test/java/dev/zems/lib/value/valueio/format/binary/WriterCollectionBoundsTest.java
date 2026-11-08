package dev.zems.lib.value.valueio.format.binary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.valueio.ValueIo;
import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import dev.zems.lib.value.valueio.wire.WireConstraintViolationException;
import dev.zems.lib.value.valueio.wire.WireConstraints;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    } catch (RuntimeException ignored) {}
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
    } catch (RuntimeException ignored) {}
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
      assertThat(r.read(descriptor).unbox()).containsExactly(Value.of(1), Value.of(2), Value.of(3));
    }
  }
}
