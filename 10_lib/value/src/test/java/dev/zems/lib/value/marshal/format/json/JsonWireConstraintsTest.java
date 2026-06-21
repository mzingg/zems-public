package dev.zems.lib.value.marshal.format.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.Protocol;
import dev.zems.lib.value.marshal.ValueIo;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import dev.zems.lib.value.marshal.wire.WireConstraintViolationException;
import dev.zems.lib.value.marshal.wire.WireConstraints;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * JSON reader-side wire-constraint enforcement. Each test produces a malicious payload (either by writing through an
 * UNCHECKED writer or hand-constructed) and verifies that the default config rejects it while
 * {@link WireConstraints#UNCHECKED} accepts it.
 */
@DisplayName("JSON reader-side WireConstraints enforcement")
@ContractTest
class JsonWireConstraintsTest {

  /** Writes a Value<String> through the streaming JSON writer and returns the wire bytes. */
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
      assertThat(v.asString().orElseThrow()).hasSize(2_000);
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
    // Hand-rolled wire with duplicate __slot0 and a stray __slot1; secure-default reader rejects.
    String json = "{\"$payload\":{\"__slot0\":1,\"__slot0\":2,\"__slot1\":3}}";
    var protocol = ValueIo.streaming();
    try (var r = protocol.jsonReader(new StringReader(json))) {
      r.hasMoreRecords();
      assertThatThrownBy(() -> r.readRecord(0, "$payload", DupHolder.DESCRIPTOR))
        .isInstanceOf(WireConstraintViolationException.class)
        .hasMessageContaining("duplicateKey");
    }
  }

  @Test
  void uncheckedAcceptsDuplicateKeys() {
    String json = "{\"$payload\":{\"__slot0\":1,\"__slot0\":2,\"__slot1\":3}}";
    var protocol = ValueIo.streaming().withWireConstraints(WireConstraints.UNCHECKED);
    try (var r = protocol.jsonReader(new StringReader(json))) {
      r.hasMoreRecords();
      var holder = r.readRecord(0, "$payload", DupHolder.DESCRIPTOR);
      // descriptor reads __slot1 ("b") first; __slot0 ("a") is buffered. With duplicates
      // allowed, the buffered map keeps the last-seen value for __slot0.
      assertThat(holder.b()).isEqualTo(3);
      assertThat(holder.a()).isEqualTo(2);
    }
  }

  @Test
  @DisplayName("a hasField probe rejects a duplicate key the same way a typed read does")
  void rejectsDuplicateKeysViaHasFieldProbe() {
    // Same wire and policy as rejectsDuplicateKeysByDefault; here the first access is a presence probe of a field
    // that sits after the duplicates, so the probe drives past both __slot0 occurrences.
    String json = "{\"$payload\":{\"__slot0\":1,\"__slot0\":2,\"__slot1\":3}}";
    var protocol = ValueIo.streaming();
    try (var r = protocol.jsonReader(new StringReader(json))) {
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
    String json = "{\"$payload\":{\"__slot0\":1,\"__slot0\":2,\"__slot1\":3}}";
    var protocol = ValueIo.streaming();
    try (var r = protocol.jsonReader(new StringReader(json))) {
      r.hasMoreRecords();
      r.beginNested(0, "$payload");
      assertThatThrownBy(() -> r.peekValueStateOrNull(1, "b"))
        .isInstanceOf(WireConstraintViolationException.class)
        .hasMessageContaining("duplicateKey");
    }
  }

  /**
   * Holder that reads {@code b} (slot 1) before {@code a} (slot 0) to force the buffered-fallback path on the JSON
   * reader (out-of-order field read).
   */
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
