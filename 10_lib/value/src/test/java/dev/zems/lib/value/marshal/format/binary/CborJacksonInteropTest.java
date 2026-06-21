package dev.zems.lib.value.marshal.format.binary;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import dev.zems.lib.common._test.JourneyTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.Protocol;
import dev.zems.lib.value.marshal.ValueIo;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cross-implementation interop with Jackson CBOR. Validates that our wire is spec-conformant CBOR that any RFC 8949
 * reader can consume, and that we can decode CBOR produced by a different codebase. Test-scope only — the runtime
 * library has no Jackson dependency.
 *
 * <p>
 * All scenarios use {@link Protocol.Mode#STREAMING} to side-step the framed envelope (self-describe tag prefix + header
 * + body + terminator) and focus the assertions on the record body shape.
 */
@DisplayName("CBOR interop with jackson-dataformat-cbor")
class CborJacksonInteropTest {

  private static final CBORMapper MAPPER = new CBORMapper();
  private static final CBORFactory FACTORY = new CBORFactory();

  // ============ Our encode → Jackson decode ============

  private static <T> byte[] writeStreaming(Value<T> value, TypeDescriptor<T> descriptor) {
    var bos = new ByteArrayOutputStream();
    try (var w = ValueIo.streaming().binaryWriter(bos)) {
      w.write(value, descriptor);
    }
    return bos.toByteArray();
  }

  private static JsonNode readTree(byte[] cbor) {
    try {
      return MAPPER.readTree(cbor);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Encodes a single-record CBOR map keyed by integer slot ids. Jackson's streaming API preserves the key type so the
   * bytes match our reader's int-key expectation.
   */
  private static byte[] encodeCborMap(Map<Integer, ?> entries) throws IOException {
    // LinkedHashMap to keep iteration deterministic (matches our canonical ordering for
    // ascending integer keys 0..N).
    var ordered = new LinkedHashMap<>(entries);
    var bos = new ByteArrayOutputStream();
    try (var gen = FACTORY.createGenerator(bos)) {
      gen.writeStartObject();
      for (var e : ordered.entrySet()) {
        gen.writeFieldId(e.getKey().longValue());
        Object v = e.getValue();
        if (v instanceof String s) {
          gen.writeString(s);
        } else if (v instanceof Integer i) {
          gen.writeNumber(i);
        } else if (v instanceof Long l) {
          gen.writeNumber(l);
        } else {
          throw new IllegalArgumentException("unsupported test value type: " + v.getClass());
        }
      }
      gen.writeEndObject();
    }
    return bos.toByteArray();
  }

  @Test
  @JourneyTest(speakingId = "cbor-interop-ours-to-jackson", acceptance = "a1")
  @DisplayName("our writer → Jackson reader: scalar string round-trip")
  void scalarString_ours_to_jackson() {
    byte[] wire = writeStreaming(Value.of("hello"), TypeDescriptor.of(String.class));
    JsonNode tree = readTree(wire);
    assertThat(tree.isObject()).isTrue();
    // Slot 0 carries the value; Jackson surfaces integer keys via fieldNames() as strings.
    assertThat(tree.get("0").asText()).isEqualTo("hello");
  }

  // ============ Jackson encode → our reader ============

  @Test
  @JourneyTest(speakingId = "cbor-interop-ours-to-jackson", acceptance = "a2")
  @DisplayName("our writer → Jackson reader: int round-trip")
  void scalarInt_ours_to_jackson() {
    byte[] wire = writeStreaming(Value.of(42), TypeDescriptor.of(Integer.class));
    JsonNode tree = readTree(wire);
    assertThat(tree.get("0").asInt()).isEqualTo(42);
  }

  @Test
  @JourneyTest(speakingId = "cbor-interop-ours-to-jackson", acceptance = "a3")
  @DisplayName("our writer → Jackson reader: record with multiple slots")
  void record_ours_to_jackson() throws IOException {
    record Person(String name, int age) {}
    var desc = TypeDescriptor.of(
      "Person",
      Person.class,
      r -> new Person(r.readString(0, "name"), r.readInt(1, "age")),
      (w, p) -> {
        w.writeString(0, "name", p.name());
        w.writeInt(1, "age", p.age());
      }
    );
    byte[] wire = writeStreaming(Value.of(new Person("Alice", 30)), desc);
    // Read using the streaming parser so we can verify integer keys directly.
    try (JsonParser parser = FACTORY.createParser(wire)) {
      assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT);
      // Key 0 (name)
      assertThat(parser.nextToken()).isEqualTo(JsonToken.FIELD_NAME);
      assertThat(parser.currentName()).isEqualTo("0");
      assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_STRING);
      assertThat(parser.getText()).isEqualTo("Alice");
      // Key 1 (age)
      assertThat(parser.nextToken()).isEqualTo(JsonToken.FIELD_NAME);
      assertThat(parser.currentName()).isEqualTo("1");
      assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_INT);
      assertThat(parser.getIntValue()).isEqualTo(30);
      assertThat(parser.nextToken()).isEqualTo(JsonToken.END_OBJECT);
    }
  }

  @Test
  @JourneyTest(speakingId = "cbor-interop-ours-to-jackson", acceptance = "a4")
  @DisplayName("our writer → Jackson reader: null state marker is a tagged null")
  void nullMarker_ours_to_jackson() throws IOException {
    byte[] wire = writeStreaming(Value.nullValue(), TypeDescriptor.of(String.class));
    // Wire shape: tag(49152) null.  Bytes: D9 C0 00 F6.
    assertThat(HexFormat.of().formatHex(wire)).isEqualTo("d9c000f6");
    // Sanity: Jackson interprets the tagged null as the null token after the tag.
    try (JsonParser parser = FACTORY.createParser(wire)) {
      // Jackson's CBOR parser stops on the tag and surfaces it via getCurrentTag().
      // We just assert that the stream isn't garbage by reading through it.
      JsonToken t;
      do {
        t = parser.nextToken();
      } while (t != null && t != JsonToken.VALUE_NULL);
      assertThat(t).isEqualTo(JsonToken.VALUE_NULL);
    }
  }

  // ============ Helpers ============

  @Test
  @JourneyTest(speakingId = "cbor-interop-jackson-to-ours", acceptance = "a1")
  @DisplayName("Jackson writer → our reader: scalar string round-trip")
  void scalarString_jackson_to_ours() throws IOException {
    byte[] wire = encodeCborMap(Map.of(0, "hello"));
    try (var r = ValueIo.streaming().binaryReader(new ByteArrayInputStream(wire))) {
      Value<String> v = r.read(String.class);
      assertThat(v.asString()).hasValue("hello");
    }
  }

  @Test
  @JourneyTest(speakingId = "cbor-interop-jackson-to-ours", acceptance = "a2")
  @DisplayName("Jackson writer → our reader: int round-trip")
  void scalarInt_jackson_to_ours() throws IOException {
    byte[] wire = encodeCborMap(Map.of(0, 42));
    try (var r = ValueIo.streaming().binaryReader(new ByteArrayInputStream(wire))) {
      Value<Integer> v = r.read(Integer.class);
      assertThat(v.asInteger()).hasValue(42);
    }
  }

  @Test
  @JourneyTest(speakingId = "cbor-interop-jackson-to-ours", acceptance = "a3")
  @DisplayName("Jackson writer → our reader: record with multiple slots")
  void record_jackson_to_ours() throws IOException {
    record Person(String name, int age) {}
    var desc = TypeDescriptor.of(
      "Person",
      Person.class,
      r -> new Person(r.readString(0, "name"), r.readInt(1, "age")),
      (w, p) -> {
        w.writeString(0, "name", p.name());
        w.writeInt(1, "age", p.age());
      }
    );
    // Build a CBOR map with integer keys via Jackson's streaming API to preserve key types.
    var bos = new ByteArrayOutputStream();
    try (var gen = FACTORY.createGenerator(bos)) {
      gen.writeStartObject();
      gen.writeFieldId(0L);
      gen.writeString("Alice");
      gen.writeFieldId(1L);
      gen.writeNumber(30);
      gen.writeEndObject();
    }
    try (var r = ValueIo.streaming().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
      Value<Person> v = r.read(desc);
      assertThat(Value.unbox(v)).isEqualTo(new Person("Alice", 30));
    }
  }
}
