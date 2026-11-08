package dev.zems.lib.value.valueio.wire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.valueio.ValueIo;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Writer-side WireConstraints enforcement")
@ContractTest
class WriterWireConstraintsTest {

  @Nested
  @DisplayName("Max string length")
  class StringLength {

    @Test
    void rejectsStringExceedingDefault() {
      var protocol = ValueIo.framed().withWireConstraints(WireConstraints.builder().maxStringLength(10).build());
      var bos = new ByteArrayOutputStream();
      try (var w = protocol.binaryWriter(bos)) {
        assertThatThrownBy(() -> w.write(Value.of("0123456789X"), TypeDescriptor.of(String.class)))
          .isInstanceOf(WireConstraintViolationException.class)
          .hasMessageContaining("maxStringLength")
          .hasFieldOrPropertyWithValue("constraint", "maxStringLength")
          .hasFieldOrPropertyWithValue("limit", 10L)
          .hasFieldOrPropertyWithValue("actual", 11L);
      }
    }

    @Test
    void uncheckedAcceptsLongString() {
      var protocol = ValueIo.framed().withWireConstraints(WireConstraints.UNCHECKED);
      var bos = new ByteArrayOutputStream();
      try (var w = protocol.binaryWriter(bos)) {
        w.write(Value.of("0123456789X"), TypeDescriptor.of(String.class));
      }
      assertThat(bos.size()).isPositive();
    }
  }

  @Nested
  @DisplayName("Non-finite numbers")
  class NonFinite {

    @Test
    void rejectsNaNDoubleByDefault() {
      var protocol = ValueIo.framed();
      var bos = new ByteArrayOutputStream();
      try (var w = protocol.binaryWriter(bos)) {
        assertThatThrownBy(() -> w.writeDouble(0, "v", Double.NaN))
          .isInstanceOf(WireConstraintViolationException.class)
          .hasMessageContaining("allowNonFiniteNumbers");
      }
    }

    @Test
    void rejectsInfinityFloatByDefault() {
      var protocol = ValueIo.framed();
      var bos = new ByteArrayOutputStream();
      try (var w = protocol.binaryWriter(bos)) {
        assertThatThrownBy(() -> w.writeFloat(0, "v", Float.POSITIVE_INFINITY))
          .isInstanceOf(WireConstraintViolationException.class)
          .hasMessageContaining("allowNonFiniteNumbers");
      }
    }

    @Test
    void uncheckedAcceptsNaN() {
      var protocol = ValueIo.framed().withWireConstraints(WireConstraints.UNCHECKED);
      var bos = new ByteArrayOutputStream();
      try (var w = protocol.binaryWriter(bos)) {
        w.writeDouble(0, "v", Double.NaN);
      }
      assertThat(bos.size()).isPositive();
    }
  }

  @Nested
  @DisplayName("Duplicate keys")
  class DuplicateKeys {

    @Test
    void rejectsDuplicateSlotNameByDefault() {
      var protocol = ValueIo.framed();
      var bos = new ByteArrayOutputStream();
      try (var w = protocol.binaryWriter(bos)) {
        w.beginNested(0, "scope");
        w.writeInt(0, "dup", 1);
        assertThatThrownBy(() -> w.writeInt(0, "dup", 2))
          .isInstanceOf(WireConstraintViolationException.class)
          .hasMessageContaining("duplicateKey")
          .hasMessageContaining("dup");
      }
    }

    @Test
    void uncheckedAcceptsDuplicateSlotName() {
      var protocol = ValueIo.framed().withWireConstraints(WireConstraints.UNCHECKED);
      var bos = new ByteArrayOutputStream();
      try (var w = protocol.binaryWriter(bos)) {
        w.beginNested(0, "scope");
        w.writeInt(0, "dup", 1);
        w.writeInt(0, "dup", 2);
        w.endNested(0, "scope");
      }
      assertThat(bos.size()).isPositive();
    }
  }

  @Nested
  @DisplayName("Max nesting depth")
  class NestingDepth {

    @Test
    void rejectsTooDeepNesting() {
      var protocol = ValueIo.framed().withWireConstraints(WireConstraints.builder().maxNestingDepth(3).build());
      var bos = new ByteArrayOutputStream();
      try (var w = protocol.binaryWriter(bos)) {
        w.beginNested(0, "a");
        w.beginNested(0, "b");
        w.beginNested(0, "c");
        assertThatThrownBy(() -> w.beginNested(0, "d"))
          .isInstanceOf(WireConstraintViolationException.class)
          .hasMessageContaining("maxNestingDepth");
      }
    }

    @Test
    void allowedDepthAccepted() {
      var protocol = ValueIo.framed().withWireConstraints(WireConstraints.builder().maxNestingDepth(4).build());
      var bos = new ByteArrayOutputStream();
      try (var w = protocol.binaryWriter(bos)) {
        w.beginNested(0, "a");
        w.beginNested(0, "b");
        w.beginNested(0, "c");
        w.beginNested(0, "d");
        w.endNested(0, "d");
        w.endNested(0, "c");
        w.endNested(0, "b");
        w.endNested(0, "a");
      }
      assertThat(bos.size()).isPositive();
    }
  }

  @Nested
  @DisplayName("JSON writer")
  class JsonWriter {

    @Test
    void enforcesStringLengthOnJson() {
      var protocol = ValueIo.framed().withWireConstraints(WireConstraints.builder().maxStringLength(5).build());
      var sw = new StringWriter();
      try (var w = protocol.jsonWriter(sw)) {
        assertThatThrownBy(() -> w.write(Value.of("xxxxxx"), TypeDescriptor.of(String.class)))
          .isInstanceOf(WireConstraintViolationException.class)
          .hasMessageContaining("maxStringLength");
      }
    }
  }
}
