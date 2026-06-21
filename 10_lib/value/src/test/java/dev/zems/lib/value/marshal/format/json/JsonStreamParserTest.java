package dev.zems.lib.value.marshal.format.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.marshal.wire.WireConstraints;
import java.io.StringReader;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("JsonStreamParser tests")
@SuppressWarnings("unchecked") // Safe casts from materialised Object to List/Map
@ContractTest
class JsonStreamParserTest {

  private List<Object> parseArray(String json) {
    return (List<Object>) parse(json);
  }

  private Object parse(String json) {
    var p = new JsonStreamParser(new StringReader(json), WireConstraints.UNCHECKED);
    return p.readAnyValue();
  }

  @Nested
  @DisplayName("Arrays")
  class Arrays {

    @Test
    void emptyArray() {
      assertThat(parseArray("[]")).isEmpty();
    }

    @Test
    void integerArray() {
      assertThat(parseArray("[1, 2, 3]")).containsExactly(1L, 2L, 3L);
    }

    @Test
    void stringArray() {
      assertThat(parseArray("[\"alpha\", \"beta\", \"gamma\"]")).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void mixedArray() {
      assertThat(parseArray("[1, \"hello\", true, null, 3.14]")).containsExactly(1L, "hello", true, null, 3.14);
    }

    @Test
    void nestedArrays() {
      var result = parseArray("[[1, 2], [3, 4]]");
      assertThat(result).hasSize(2);
      assertThat((List<Object>) result.get(0)).containsExactly(1L, 2L);
      assertThat((List<Object>) result.get(1)).containsExactly(3L, 4L);
    }

    @Test
    void arrayOfObjects() {
      var result = parseArray("[{\"name\":\"Alice\"},{\"name\":\"Bob\"}]");
      assertThat(result).hasSize(2);
      assertThat((Map<String, Object>) result.get(0)).containsEntry("name", "Alice");
      assertThat((Map<String, Object>) result.get(1)).containsEntry("name", "Bob");
    }

    @Test
    void arrayWithWhitespace() {
      assertThat(parseArray("  [ 1 , 2 , 3 ]  ")).containsExactly(1L, 2L, 3L);
    }

    @Test
    void unterminatedArray() {
      assertThatThrownBy(() -> parse("[1, 2"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("EOF");
    }

    @Test
    void trailingCommaArrayRejected() {
      assertThatThrownBy(() -> parse("[1, 2,]"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Trailing comma");
    }
  }

  @Nested
  @DisplayName("Unicode escapes")
  class UnicodeEscapes {

    @Test
    void failsOnNonHexUnicodeEscape() {
      assertThatThrownBy(() -> parse("\"\\uXYZW\""))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Invalid hex digit in unicode escape");
    }

    @Test
    void parsesValidUnicodeEscape() {
      assertThat(parse("\"\\u0041\"")).isEqualTo("A");
    }

    @Test
    void parsesSurrogatePair() {
      // U+1F600 GRINNING FACE → 😀
      String parsed = (String) parse("\"\\uD83D\\uDE00\"");
      assertThat(parsed).hasSize(2);
      assertThat(parsed.codePointAt(0)).isEqualTo(0x1F600);
    }
  }

  @Nested
  @DisplayName("Objects containing arrays")
  class ObjectsContainingArrays {

    @Test
    void objectWithArrayField() {
      var map = (Map<String, Object>) parse("{\"tags\":[\"a\",\"b\",\"c\"]}");
      assertThat((List<Object>) map.get("tags")).containsExactly("a", "b", "c");
    }

    @Test
    void objectWithEmptyArrayField() {
      var map = (Map<String, Object>) parse("{\"items\":[]}");
      assertThat((List<Object>) map.get("items")).isEmpty();
    }

    @Test
    void objectWithMixedFieldsIncludingArray() {
      var map = (Map<String, Object>) parse("{\"name\":\"test\",\"scores\":[10,20,30],\"active\":true}");
      assertThat(map).containsEntry("name", "test");
      assertThat((List<Object>) map.get("scores")).containsExactly(10L, 20L, 30L);
      assertThat(map).containsEntry("active", true);
    }
  }

  @Nested
  @DisplayName("Token-level streaming")
  class TokenStream {

    @Test
    void streamYieldsTokensIncrementally() {
      var p = new JsonStreamParser(new StringReader("{\"a\":1,\"b\":\"hi\"}"), WireConstraints.UNCHECKED);
      assertThat(p.nextToken()).isEqualTo(JsonStreamParser.Token.OBJECT_START);
      assertThat(p.nextToken()).isEqualTo(JsonStreamParser.Token.FIELD_NAME);
      assertThat(p.stringValue()).isEqualTo("a");
      assertThat(p.nextToken()).isEqualTo(JsonStreamParser.Token.NUMBER);
      assertThat(p.nextToken()).isEqualTo(JsonStreamParser.Token.FIELD_NAME);
      assertThat(p.stringValue()).isEqualTo("b");
      assertThat(p.nextToken()).isEqualTo(JsonStreamParser.Token.STRING);
      assertThat(p.stringValue()).isEqualTo("hi");
      assertThat(p.nextToken()).isEqualTo(JsonStreamParser.Token.OBJECT_END);
      assertThat(p.nextToken()).isEqualTo(JsonStreamParser.Token.EOF);
    }

    @Test
    void peekIsIdempotent() {
      var p = new JsonStreamParser(new StringReader("[1,2]"), WireConstraints.UNCHECKED);
      assertThat(p.peekToken()).isEqualTo(JsonStreamParser.Token.ARRAY_START);
      assertThat(p.peekToken()).isEqualTo(JsonStreamParser.Token.ARRAY_START);
      assertThat(p.nextToken()).isEqualTo(JsonStreamParser.Token.ARRAY_START);
      assertThat(p.peekToken()).isEqualTo(JsonStreamParser.Token.NUMBER);
    }

    @Test
    void multipleRootObjectsForJsonl() {
      var p = new JsonStreamParser(new StringReader("{\"a\":1}\n{\"b\":2}\n"), WireConstraints.UNCHECKED);
      assertThat(p.nextToken()).isEqualTo(JsonStreamParser.Token.OBJECT_START);
      // skip first object's contents
      while (p.depth() > 0) {
        p.nextToken();
      }
      assertThat(p.nextToken()).isEqualTo(JsonStreamParser.Token.OBJECT_START);
      while (p.depth() > 0) {
        p.nextToken();
      }
      assertThat(p.nextToken()).isEqualTo(JsonStreamParser.Token.EOF);
    }

    @Test
    void skipValueSkipsNested() {
      var p = new JsonStreamParser(new StringReader("[[1,[2,[3]]],4]"), WireConstraints.UNCHECKED);
      assertThat(p.nextToken()).isEqualTo(JsonStreamParser.Token.ARRAY_START);
      p.skipValue(); // consumes [1,[2,[3]]]
      assertThat(p.nextToken()).isEqualTo(JsonStreamParser.Token.NUMBER);
      assertThat(p.nextToken()).isEqualTo(JsonStreamParser.Token.ARRAY_END);
    }
  }

  @Nested
  @DisplayName("Number tokens")
  class Numbers {

    @Test
    @DisplayName("plain integer")
    void plainInteger() {
      assertThat(parse("42")).isEqualTo(42L);
      assertThat(parse("-7")).isEqualTo(-7L);
      assertThat(parse("0")).isEqualTo(0L);
    }

    @Test
    @DisplayName("decimal fraction")
    void decimalFraction() {
      assertThat(parse("3.14")).isEqualTo(3.14);
      assertThat(parse("-0.5")).isEqualTo(-0.5);
    }

    @Test
    @DisplayName("scientific notation — lower-case e")
    void scientificLowerCaseE() {
      assertThat(parse("1e10")).isEqualTo(1e10);
      assertThat(parse("2e-5")).isEqualTo(2e-5);
    }

    @Test
    @DisplayName("scientific notation — upper-case E")
    void scientificUpperCaseE() {
      assertThat(parse("1E10")).isEqualTo(1e10);
      assertThat(parse("1E+10")).isEqualTo(1e10);
    }

    @Test
    @DisplayName("very large integer overflows long → exact BigInteger (no precision loss)")
    void veryLargeInteger() {
      // 23-digit integer overflows long; the parser keeps it exactly as BigInteger, not a lossy Double.
      Object result = parse("12345678901234567890123");
      assertThat(result).isInstanceOf(BigInteger.class).isEqualTo(new BigInteger("12345678901234567890123"));
    }

    @Test
    @DisplayName("negative zero parses cleanly")
    void negativeZero() {
      Object result = parse("-0");
      assertThat(result).isIn(0L, -0.0);
    }

    @Test
    @DisplayName("NaN is rejected (RFC 8259)")
    void nanRejected() {
      assertThatThrownBy(() -> parse("NaN")).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Infinity is rejected (RFC 8259)")
    void infinityRejected() {
      assertThatThrownBy(() -> parse("Infinity")).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("leading-zero integers are rejected (RFC 8259)")
    void leadingZeroRejected() {
      assertThatThrownBy(() -> parse("007"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("leading zeros");
      assertThatThrownBy(() -> parse("01"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("leading zeros");
      assertThatThrownBy(() -> parse("-01"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("leading zeros");
    }

    @Test
    @DisplayName("a single zero and zero-led fractions stay valid")
    void singleZeroAndFractionsValid() {
      assertThat(parse("0")).isEqualTo(0L);
      assertThat(parse("0.5")).isEqualTo(0.5);
      assertThat(parse("0e1")).isEqualTo(0.0);
    }
  }

  @Nested
  @DisplayName("String escapes")
  class StringEscapes {

    @Test
    @DisplayName("\\b \\t \\n \\r \\f are recognised")
    void controlCharEscapes() {
      assertThat(parse("\"\\b\"")).isEqualTo("\b");
      assertThat(parse("\"\\t\"")).isEqualTo("\t");
      assertThat(parse("\"\\n\"")).isEqualTo("\n");
      assertThat(parse("\"\\r\"")).isEqualTo("\r");
      assertThat(parse("\"\\f\"")).isEqualTo("\f");
    }

    @Test
    @DisplayName("\\\" \\\\ \\/ are recognised")
    void quoteAndSlashEscapes() {
      assertThat(parse("\"\\\"\"")).isEqualTo("\"");
      assertThat(parse("\"\\\\\"")).isEqualTo("\\");
      assertThat(parse("\"\\/\"")).isEqualTo("/");
    }

    @Test
    @DisplayName("unknown escape sequence rejected")
    void unknownEscapeRejected() {
      assertThatThrownBy(() -> parse("\"\\x\"")).isInstanceOf(RuntimeException.class);
    }
  }

  @Nested
  @DisplayName("Malformed input")
  class Malformed {

    @Test
    @DisplayName("unterminated string")
    void unterminatedString() {
      assertThatThrownBy(() -> parse("\"hello")).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("missing colon in object")
    void missingColon() {
      assertThatThrownBy(() -> parse("{\"a\" 1}")).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("trailing comma in array")
    void trailingCommaInArray() {
      assertThatThrownBy(() -> parse("[1, 2,]")).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("trailing comma in object")
    void trailingCommaInObject() {
      assertThatThrownBy(() -> parse("{\"a\":1,}")).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("garbage after value")
    void garbageAfterValue() {
      // readAnyValue stops after the value; trailing garbage may or may not error, but
      // at minimum the parser shouldn't loop or NPE.
      Object result = parse("42 garbage");
      assertThat(result).isEqualTo(42L);
    }
  }
}
