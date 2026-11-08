package dev.zems.lib.value.valueio.format.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.valueio.ValueIo;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import dev.zems.lib.value.valueio.wire.WireConstraintViolationException;
import dev.zems.lib.value.valueio.wire.WireConstraints;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JSON reader-side WireConstraints enforcement")
@ContractTest
class JsonWireConstraintsTest {

  private static final String DUPLICATE_SLOT0_WIRE = "{\"$payload\":{\"__slot0\":1,\"__slot0\":2,\"__slot1\":3}}";

  private static String produceStringPayload(String content) {
    var sw = new StringWriter();
    var write = ValueIo.streaming().withWireConstraints(WireConstraints.UNCHECKED);
    try (var w = write.jsonWriter(sw)) {
      w.write(Value.of(content), TypeDescriptor.of(String.class));
    }
    return sw.toString();
  }

  @Test
  void rejectsTooLongString() {
    String json = produceStringPayload("x".repeat(2_000));
    var protocol = ValueIo.streaming().withWireConstraints(WireConstraints.builder().maxStringLength(100).build());
    try (var r = protocol.jsonReader(new StringReader(json))) {
      r.hasMoreRecords();
      assertThatThrownBy(() -> r.read(String.class))
        .isInstanceOf(WireConstraintViolationException.class)
        .hasMessageContaining("maxStringLength");
    }
  }

  @Test
  void uncheckedAcceptsLongString() {
    String json = produceStringPayload("x".repeat(2_000));
    var protocol = ValueIo.streaming().withWireConstraints(WireConstraints.UNCHECKED);
    try (var r = protocol.jsonReader(new StringReader(json))) {
      r.hasMoreRecords();
      var v = r.read(String.class);
      assertThat(v.convertTo(String.class).orElseThrow()).hasSize(2_000);
    }
  }

  @Test
  void rejectsTooDeepObjectNesting() {
    var sb = new StringBuilder();
    int depth = 50;
    sb.append("{\"value\":");
    sb.repeat("[", depth);
    sb.repeat("]", depth);
    sb.append("}");
    var protocol = ValueIo.streaming().withWireConstraints(WireConstraints.builder().maxNestingDepth(10).build());
    try (var r = protocol.jsonReader(new StringReader(sb.toString()))) {
      r.hasMoreRecords();
      assertThatThrownBy(() -> r.peekValueStateOrNull(0, "value"))
        .isInstanceOf(WireConstraintViolationException.class)
        .hasMessageContaining("maxNestingDepth");
    }
  }

  @Test
  void rejectsDuplicateKeysByDefault() {
    var protocol = ValueIo.streaming();
    try (var r = protocol.jsonReader(new StringReader(DUPLICATE_SLOT0_WIRE))) {
      r.hasMoreRecords();
      assertThatThrownBy(() -> r.readRecord(0, "$payload", DupHolder.DESCRIPTOR))
        .isInstanceOf(WireConstraintViolationException.class)
        .hasMessageContaining("duplicateKey");
    }
  }

  @Test
  @DisplayName("with duplicates allowed, an out-of-order read keeps the last-seen value of the duplicated key")
  void uncheckedAcceptsDuplicateKeys() {
    var protocol = ValueIo.streaming().withWireConstraints(WireConstraints.UNCHECKED);
    try (var r = protocol.jsonReader(new StringReader(DUPLICATE_SLOT0_WIRE))) {
      r.hasMoreRecords();
      var holder = r.readRecord(0, "$payload", DupHolder.DESCRIPTOR);
      assertThat(holder.b()).isEqualTo(3);
      assertThat(holder.a()).isEqualTo(2);
    }
  }

  @Test
  @DisplayName("a hasField probe rejects a duplicate key the same way a typed read does")
  void rejectsDuplicateKeysViaHasFieldProbe() {
    var protocol = ValueIo.streaming();
    try (var r = protocol.jsonReader(new StringReader(DUPLICATE_SLOT0_WIRE))) {
      r.hasMoreRecords();
      r.beginNested(0, "$payload");
      assertThatThrownBy(() -> r.hasField(1, "b"))
        .isInstanceOf(WireConstraintViolationException.class)
        .hasMessageContaining("duplicateKey");
    }
  }

  @Test
  @DisplayName("a state-peek probe rejects a duplicate key the same way a typed read does")
  void rejectsDuplicateKeysViaStatePeekProbe() {
    var protocol = ValueIo.streaming();
    try (var r = protocol.jsonReader(new StringReader(DUPLICATE_SLOT0_WIRE))) {
      r.hasMoreRecords();
      r.beginNested(0, "$payload");
      assertThatThrownBy(() -> r.peekValueStateOrNull(1, "b"))
        .isInstanceOf(WireConstraintViolationException.class)
        .hasMessageContaining("duplicateKey");
    }
  }

  public record DupHolder(int a, int b) {
    public static final TypeDescriptor<DupHolder> DESCRIPTOR = TypeDescriptor.of(
      "DupHolder",
      DupHolder.class,
      r -> {
        int b = r.readInt(1, "b");
        int a = r.readInt(0, "a");
        return new DupHolder(a, b);
      },
      (w, h) -> {
        w.writeInt(1, "b", h.b());
        w.writeInt(0, "a", h.a());
      }
    );
  }
}
