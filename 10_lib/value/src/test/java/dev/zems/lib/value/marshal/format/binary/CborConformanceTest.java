package dev.zems.lib.value.marshal.format.binary;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zems.lib.common._test.ContractTest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Drives our CBOR encoder / decoder against the public RFC 8949 Appendix A test vectors vendored from
 * {@code https://github.com/cbor/test-vectors}. For each vector with simple primitive {@code decoded} (number / string
 * / bool / null), we:
 *
 * <ul>
 * <li><b>encode</b> the JSON-decoded value via {@link CborWriter} and assert the resulting
 * hex matches the vector's {@code hex} (for {@code roundtrip=true} entries);
 * <li><b>decode</b> the vector's hex via {@link CborReader} and assert the decoded value
 * matches the JSON-decoded {@code decoded} field.
 * </ul>
 *
 * <p>
 * Composite vectors (arrays, maps, tagged data, byte strings, bignums) are skipped — they
 * exercise our higher-level state machinery rather than the raw codec, and they're already
 * covered indirectly by {@link CborJacksonInteropTest}. Vectors without {@code decoded}
 * (e.g. ones carrying only a {@code diagnostic} field) are skipped.
 *
 * <p>
 * Vendored under {@code src/test/resources/cbor/appendix_a.json}. License: see provenance
 * notes on the {@code cbor/test-vectors} repository.
 */
@ContractTest
@DisplayName("CBOR conformance — RFC 8949 Appendix A vectors")
class CborConformanceTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final HexFormat HEX = HexFormat.of();

  /**
   * Generates one argument per simple-typed vector. Each argument carries a label, the expected hex, the JSON-decoded
   * value, and whether the vector is round-trippable.
   */
  static List<Arguments> simpleVectors() throws IOException {
    List<Arguments> out = new ArrayList<>();
    try (InputStream in = CborConformanceTest.class.getResourceAsStream("/cbor/appendix_a.json")) {
      if (in == null) {
        throw new IllegalStateException("vectors not vendored at src/test/resources/cbor/appendix_a.json");
      }
      JsonNode root = JSON.readTree(in);
      for (JsonNode entry : root) {
        if (!entry.has("decoded")) {
          continue;
        }
        JsonNode decoded = entry.get("decoded");
        if (!isSimple(decoded)) {
          continue;
        }
        String hex = entry.get("hex").asText();
        // Float16 (initial byte 0xf9) and CBOR bignum-tagged ints (0xc2 / 0xc3) are out of
        // scope — the codec deliberately ships only float32 / float64 and signed-long ints.
        if (hex.startsWith("f9") || hex.startsWith("c2") || hex.startsWith("c3")) {
          continue;
        }
        // Integers that exceed Java's signed-long range likewise aren't representable.
        if (decoded.isNumber() && decoded.isIntegralNumber() && !decoded.canConvertToLong()) {
          continue;
        }
        boolean roundtrip = entry.has("roundtrip") && entry.get("roundtrip").asBoolean();
        out.add(Arguments.of(hex + " ≈ " + decoded, hex, decoded, roundtrip));
      }
    }
    return out;
  }

  private static boolean isSimple(JsonNode node) {
    return (node.isNumber() || node.isTextual() || node.isBoolean() || node.isNull());
  }

  /** Drives {@link CborReader} for one expected value and asserts the decoded payload matches. */
  private static void assertDecoded(CborReader r, JsonNode expected, String label) {
    if (expected.isNull()) {
      r.readNull();
      return;
    }
    if (expected.isBoolean()) {
      assertThat(r.readBool()).as(label).isEqualTo(expected.asBoolean());
      return;
    }
    if (expected.isTextual()) {
      assertThat(r.readText()).as(label).isEqualTo(expected.asText());
      return;
    }
    if (expected.isNumber()) {
      // Floating-point vectors are encoded as float32 or float64. Detect by initial-byte.
      CborMajorType mt = r.peekMajorType();
      if (mt == CborMajorType.SIMPLE_OR_FLOAT) {
        double actual = r.readFloatAny();
        double expectedDouble = expected.asDouble();
        if (Double.isNaN(expectedDouble)) {
          assertThat(actual).as(label).isNaN();
        } else if (Double.isInfinite(expectedDouble)) {
          assertThat(actual).as(label).isEqualTo(expectedDouble);
        } else {
          assertThat(actual).as(label).isEqualTo(expectedDouble);
        }
        return;
      }
      // Integer (positive or negative). Compare as long for exactness up to 2^63-1.
      long actual = r.readInt64();
      if (expected.canConvertToLong()) {
        assertThat(actual).as(label).isEqualTo(expected.asLong());
      } else {
        // Values beyond Long range — the vector's "decoded" surfaces as double. Skip.
      }
      return;
    }
    throw new IllegalStateException("Unhandled node type at " + label + ": " + expected);
  }

  // ============ Helpers ============

  /** Writes the equivalent CBOR data item for a simple expected value via {@link CborWriter}. */
  private static void encodeSimple(CborWriter w, JsonNode expected) {
    if (expected.isNull()) {
      w.writeNull();
      return;
    }
    if (expected.isBoolean()) {
      w.writeBool(expected.asBoolean());
      return;
    }
    if (expected.isTextual()) {
      w.writeText(expected.asText());
      return;
    }
    if (expected.isIntegralNumber() && expected.canConvertToLong()) {
      long v = expected.asLong();
      if (v >= 0) {
        w.writeUnsignedInt(v);
      } else {
        w.writeNegativeInt(v);
      }
      return;
    }
    if (expected.isFloatingPointNumber()) {
      w.writeFloat64Canonical(expected.asDouble());
      return;
    }
    throw new IllegalStateException("Unhandled simple node: " + expected);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("simpleVectors")
  void decodeMatchesExpected(String label, String hex, JsonNode expected, boolean roundtrip) {
    byte[] bytes = HEX.parseHex(hex);
    MemorySegment seg = MemorySegment.ofArray(bytes);
    var r = new CborReader(SegmentCursors.bounded(seg), Integer.MAX_VALUE);
    assertDecoded(r, expected, label);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("simpleVectors")
  void encodeMatchesHex(String label, String hex, JsonNode expected, boolean roundtrip) {
    if (!roundtrip) {
      return; // some vectors decode-only (e.g. non-canonical encodings)
    }
    var bos = new ByteArrayOutputStream();
    var sink = new TestSink(bos);
    var w = new CborWriter(sink);
    encodeSimple(w, expected);
    String produced = HEX.formatHex(bos.toByteArray());
    assertThat(produced).as(label).isEqualTo(hex);
  }

  /** Minimal {@link CborByteOutput} backed by a {@link ByteArrayOutputStream}. */
  private static final class TestSink implements CborByteOutput {

    private final ByteArrayOutputStream out;

    TestSink(ByteArrayOutputStream out) {
      this.out = out;
    }

    @Override
    public void put(byte b) {
      out.write(b & 0xFF);
    }

    @Override
    public void put(byte[] src, int off, int len) {
      out.write(src, off, len);
    }

    @Override
    public void putShort(short v) {
      out.write((v >>> 8) & 0xFF);
      out.write(v & 0xFF);
    }

    @Override
    public void putInt(int v) {
      out.write((v >>> 24) & 0xFF);
      out.write((v >>> 16) & 0xFF);
      out.write((v >>> 8) & 0xFF);
      out.write(v & 0xFF);
    }

    @Override
    public void putLong(long v) {
      for (int i = 7; i >= 0; i--) {
        out.write((int) ((v >>> (i * 8)) & 0xFF));
      }
    }

    @Override
    public void putFloat(float v) {
      putInt(Float.floatToRawIntBits(v));
    }

    @Override
    public void putDouble(double v) {
      putLong(Double.doubleToRawLongBits(v));
    }
  }
}
