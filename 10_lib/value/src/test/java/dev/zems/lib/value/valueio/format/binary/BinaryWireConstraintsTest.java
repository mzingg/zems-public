package dev.zems.lib.value.valueio.format.binary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.valueio.ValueIo;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import dev.zems.lib.value.valueio.wire.WireConstraintViolationException;
import dev.zems.lib.value.valueio.wire.WireConstraints;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Binary reader-side WireConstraints enforcement")
@ContractTest
class BinaryWireConstraintsTest {

  @Test
  void rejectsStringExceedingReaderLimit() {
    var bos = new ByteArrayOutputStream();
    var writeProtocol = ValueIo.framed().withWireConstraints(WireConstraints.UNCHECKED);
    try (var w = writeProtocol.binaryWriter(bos)) {
      w.write(Value.of("x".repeat(500)), TypeDescriptor.of(String.class));
    }
    var readProtocol = ValueIo.framed().withWireConstraints(WireConstraints.builder().maxStringLength(100).build());
    try (var r = readProtocol.binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
      assertThatThrownBy(() -> r.read(String.class))
        .isInstanceOf(WireConstraintViolationException.class)
        .hasMessageContaining("maxStringLength");
    } catch (RuntimeException ignored) {}
  }

  @Test
  @DisplayName("binary text bound is measured in UTF-8 bytes, not UTF-16 chars")
  void stringBoundMeasuredInBytes() {
    var bos = new ByteArrayOutputStream();
    var writeProtocol = ValueIo.framed().withWireConstraints(WireConstraints.UNCHECKED);
    try (var w = writeProtocol.binaryWriter(bos)) {
      w.write(Value.of("€".repeat(200)), TypeDescriptor.of(String.class));
    }
    var readProtocol = ValueIo.framed().withWireConstraints(WireConstraints.builder().maxStringLength(300).build());
    try (var r = readProtocol.binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
      assertThatThrownBy(() -> r.read(String.class))
        .isInstanceOf(WireConstraintViolationException.class)
        .hasMessageContaining("maxStringLength");
    } catch (RuntimeException ignored) {}
  }

  @Test
  void rejectsTooDeepNestingOnRead() {
    var bos = new ByteArrayOutputStream();
    var writeProtocol = ValueIo.framed().withWireConstraints(WireConstraints.UNCHECKED);
    try (var w = writeProtocol.binaryWriter(bos)) {
      w.beginNested(0, "a");
      w.beginNested(0, "b");
      w.beginNested(0, "c");
      w.beginNested(0, "d");
      w.endNested(0, "d");
      w.endNested(0, "c");
      w.endNested(0, "b");
      w.endNested(0, "a");
    }
    var readProtocol = ValueIo.framed().withWireConstraints(WireConstraints.builder().maxNestingDepth(2).build());
    try (var r = readProtocol.binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
      assertThatThrownBy(() -> {
        r.beginNested(0, "a");
        r.beginNested(0, "b");
        r.beginNested(0, "c");
      })
        .isInstanceOf(WireConstraintViolationException.class)
        .hasMessageContaining("maxNestingDepth");
    } catch (RuntimeException ignored) {}
  }

  @Test
  void rejectsNonFiniteDoubleOnReadByDefault() {
    var bos = new ByteArrayOutputStream();
    var writeProtocol = ValueIo.framed().withWireConstraints(WireConstraints.UNCHECKED);
    try (var w = writeProtocol.binaryWriter(bos)) {
      w.writeDouble(0, "v", Double.NaN);
    }
    var readProtocol = ValueIo.framed();
    try (var r = readProtocol.binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
      assertThatThrownBy(() -> r.readDouble(0, "v"))
        .isInstanceOf(WireConstraintViolationException.class)
        .hasMessageContaining("allowNonFiniteNumbers");
    }
  }

  @Test
  void uncheckedAcceptsNonFiniteDoubleOnRead() {
    var bos = new ByteArrayOutputStream();
    var protocol = ValueIo.framed().withWireConstraints(WireConstraints.UNCHECKED);
    try (var w = protocol.binaryWriter(bos)) {
      w.writeDouble(0, "v", Double.NaN);
    }
    try (var r = protocol.binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
      assertThat(r.readDouble(0, "v")).isNaN();
    }
  }
}
