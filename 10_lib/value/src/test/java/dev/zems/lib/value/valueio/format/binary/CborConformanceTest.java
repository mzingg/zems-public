package dev.zems.lib.value.valueio.format.binary;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zems.lib.common.junit.ContractTest;
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

@ContractTest
@DisplayName("CBOR conformance — RFC 8949 Appendix A vectors (simple scalars only)")
class CborConformanceTest {

  /** RFC 8949 Appendix A test vectors, vendored from https://github.com/cbor/test-vectors. */
  private static final String VECTORS_RESOURCE = "/cbor/appendix_a.json";

  /** RFC 8949 §3.3: initial byte of a half-precision float. */
  private static final String FLOAT16_INITIAL_BYTE = "f9";

  /** RFC 8949 §3.4.3: tag 2, a positive bignum. */
  private static final String POSITIVE_BIGNUM_TAG = "c2";

  /** RFC 8949 §3.4.3: tag 3, a negative bignum. */
  private static final String NEGATIVE_BIGNUM_TAG = "c3";

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final HexFormat HEX = HexFormat.of();

  static List<Arguments> simpleVectors() throws IOException {
    List<Arguments> out = new ArrayList<>();
    try (InputStream in = CborConformanceTest.class.getResourceAsStream(VECTORS_RESOURCE)) {
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
        if (isOutsideCodecScope(hex, decoded)) {
          continue;
        }
        boolean roundtrip = entry.has("roundtrip") && entry.get("roundtrip").asBoolean();
        out.add(Arguments.of(hex + " ≈ " + decoded, hex, decoded, roundtrip));
      }
    }
    return out;
  }

  private static boolean isSimple(JsonNode node) {
    return node.isNumber() || node.isTextual() || node.isBoolean() || node.isNull();
  }

  private static boolean isOutsideCodecScope(String hex, JsonNode decoded) {
    boolean halfFloat = hex.startsWith(FLOAT16_INITIAL_BYTE);
    boolean bignum = hex.startsWith(POSITIVE_BIGNUM_TAG) || hex.startsWith(NEGATIVE_BIGNUM_TAG);
    boolean beyondLongRange = decoded.isNumber() && decoded.isIntegralNumber() && !decoded.canConvertToLong();
    return halfFloat || bignum || beyondLongRange;
  }

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
      long actual = r.readInt64();
      if (expected.canConvertToLong()) {
        assertThat(actual).as(label).isEqualTo(expected.asLong());
      }
      return;
    }
    throw new IllegalStateException("Unhandled node type at " + label + ": " + expected);
  }

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
      return;
    }
    var bos = new ByteArrayOutputStream();
    var sink = new TestSink(bos);
    var w = new CborWriter(sink);
    encodeSimple(w, expected);
    String produced = HEX.formatHex(bos.toByteArray());
    assertThat(produced).as(label).isEqualTo(hex);
  }

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
