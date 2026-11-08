package dev.zems.lib.value.json.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Json")
@ContractTest
class JsonTest {

  private static final String NO_VALUE = "the text contains no JSON value; pass a value such as {}, [] or \"text\"";
  private static final String COMMENT = "JSON does not allow comments; remove the comment";
  private static final String ARRAY_TRAILING_COMMA =
    "the array ends with a trailing comma, which JSON does not allow; remove the comma";
  private static final String OBJECT_TRAILING_COMMA =
    "the object ends with a trailing comma, which JSON does not allow; remove the comma";
  private static final String SINGLE_QUOTE =
    "a JSON value cannot start with a single quote; JSON strings use double quotes";
  private static final String MORE_TEXT =
    "there is more text after the JSON value; a JSON text contains exactly one value, so wrap several values in an array";
  private static final String X_IS_NOT_A_LITERAL =
    "x is not valid JSON; the only literals are true, false and null, written in lower case";
  private static final String INDENT_RULE =
    ": an indent may contain only spaces, tabs, line feeds and carriage returns";

  @Nested
  @DisplayName("parse")
  class Parse {

    static Stream<ParseError> emptyTexts() {
      return Stream.of(
        new ParseError("empty text", "", 1, 1, "$", NO_VALUE),
        new ParseError("spaces only", "  ", 1, 3, "$", NO_VALUE),
        new ParseError("line feed and tab only", "\n\t", 2, 2, "$", NO_VALUE)
      );
    }

    static Stream<ParseError> comments() {
      return Stream.of(
        new ParseError("comment before the root value", "// c\n1", 1, 1, "$", COMMENT),
        new ParseError("comment at an element position", "[/* c */1]", 1, 2, "$[0]", COMMENT),
        new ParseError("comment after a member value", "{\"a\":1/* c */}", 1, 7, "$.a", COMMENT),
        new ParseError("comment after an element", "[1// c\n]", 1, 3, "$[0]", COMMENT),
        new ParseError("comment at a member name position", "{/* c */\"a\":1}", 1, 2, "$", COMMENT),
        new ParseError("comment between a member name and ':'", "{\"a\" /* c */ : 1}", 1, 6, "$.a", COMMENT),
        new ParseError("comment after the root value", "1 /* c */", 1, 3, "$", COMMENT),
        new ParseError("comment after a comma", "[1,/* c */2]", 1, 4, "$[1]", COMMENT)
      );
    }

    static Stream<ParseError> trailingCommas() {
      return Stream.of(
        new ParseError("array, space before ']'", "[1, ]", 1, 5, "$[1]", ARRAY_TRAILING_COMMA),
        new ParseError("nested array", "{\"a\":[1,]}", 1, 9, "$.a[1]", ARRAY_TRAILING_COMMA),
        new ParseError("object, space before '}'", "{\"a\":1, }", 1, 9, "$", OBJECT_TRAILING_COMMA)
      );
    }

    static Stream<ParseError> duplicateNames() {
      String longName = "n".repeat(45);
      return Stream.of(
        new ParseError(
          "repeated name",
          "{\"a\":1,\"a\":2}",
          1,
          8,
          "$.a",
          "the member name \"a\" appears twice in the same object; rename or remove one of the two members"
        ),
        new ParseError(
          "repeated name in a nested object",
          "{\"x\":{\"a\":1,\"a\":2}}",
          1,
          13,
          "$.x.a",
          "the member name \"a\" appears twice in the same object; rename or remove one of the two members"
        ),
        new ParseError(
          "repeated name with an escape, JSON-escaped in the message and the path",
          "{\"a\\\"b\":1,\"a\\\"b\":2}",
          1,
          11,
          "$[\"a\\\"b\"]",
          "the member name \"a\\\"b\" appears twice in the same object; rename or remove one of the two members"
        ),
        new ParseError(
          "repeated long name, cut at 40 characters in the message and the path",
          "{\"" + longName + "\":1,\"" + longName + "\":2}",
          1,
          52,
          "$." + "n".repeat(40) + "…",
          "the member name \"" +
            "n".repeat(40) +
            "…\" appears twice in the same object; rename or remove one of the two members"
        ),
        new ParseError(
          "repeated name found before the text ends",
          "{\"a\":1,\"a\"",
          1,
          8,
          "$.a",
          "the member name \"a\" appears twice in the same object; rename or remove one of the two members"
        )
      );
    }

    static Stream<ParseError> badNumbers() {
      return Stream.of(
        new ParseError(
          "negative leading zero",
          "-01",
          1,
          1,
          "$",
          "the number -01 has a leading zero, which JSON does not allow; remove the zero, or write the value as a string if" +
            " the zero matters"
        ),
        new ParseError(
          "double zero",
          "{\"a\":00}",
          1,
          6,
          "$.a",
          "the number 00 has a leading zero, which JSON does not allow; remove the zero, or write the value as a string if" +
            " the zero matters"
        ),
        new ParseError(
          "NaN",
          "NaN",
          1,
          1,
          "$",
          "NaN is not a valid JSON number; use null, or a string such as \"NaN\""
        ),
        new ParseError(
          "Infinity",
          "[Infinity]",
          1,
          2,
          "$[0]",
          "Infinity is not a valid JSON number; use null, or a string such as \"Infinity\""
        ),
        new ParseError(
          "-Infinity",
          "{\"a\":-Infinity}",
          1,
          6,
          "$.a",
          "-Infinity is not a valid JSON number; use null, or a string such as \"-Infinity\""
        ),
        new ParseError(
          "minus alone",
          "-",
          1,
          2,
          "$",
          "the number - is incomplete because no digit follows '-'; add the missing digits"
        ),
        new ParseError(
          "no digit after the decimal point",
          "[1.]",
          1,
          4,
          "$[0]",
          "the number 1. is incomplete because no digit follows '.'; add the missing digits"
        ),
        new ParseError(
          "no digit after e",
          "1e",
          1,
          3,
          "$",
          "the number 1e is incomplete because no digit follows 'e'; add the missing digits"
        ),
        new ParseError(
          "no digit after E and its sign",
          "1E+",
          1,
          4,
          "$",
          "the number 1E+ is incomplete because no digit follows 'E'; add the missing digits"
        ),
        new ParseError(
          "text ends right after the decimal point",
          "[1.",
          1,
          4,
          "$[0]",
          "the number 1. is incomplete because no digit follows '.'; add the missing digits"
        )
      );
    }

    static Stream<ParseError> badValueStarts() {
      return Stream.of(
        new ParseError(
          "capitalised literal",
          "True",
          1,
          1,
          "$",
          "True is not valid JSON; the only literals are true, false and null, written in lower case"
        ),
        new ParseError(
          "truncated literal",
          "[nul]",
          1,
          2,
          "$[0]",
          "nul is not valid JSON; the only literals are true, false and null, written in lower case"
        ),
        new ParseError(
          "long letter run, cut at 40 characters",
          "a".repeat(45),
          1,
          1,
          "$",
          "a".repeat(40) + "… is not valid JSON; the only literals are true, false and null, written in lower case"
        ),
        new ParseError(
          "a letter run stops at a digit",
          "[true1]",
          1,
          6,
          "$[0]",
          "expected ',' or ']' after the element, but found '1'; separate elements with commas and close the array with ']'"
        ),
        new ParseError("single-quoted root", "'a'", 1, 1, "$", SINGLE_QUOTE),
        new ParseError("single-quoted member value", "{\"a\":'b'}", 1, 6, "$.a", SINGLE_QUOTE),
        new ParseError(
          "hash sign",
          "#",
          1,
          1,
          "$",
          "a JSON value cannot start with '#'; it must be an object, array, string, number, true, false or null"
        ),
        new ParseError(
          "closing brace at a member value",
          "{\"a\":}",
          1,
          6,
          "$.a",
          "a JSON value cannot start with '}'; it must be an object, array, string, number, true, false or null"
        ),
        new ParseError(
          "comma at the first element",
          "[,1]",
          1,
          2,
          "$[0]",
          "a JSON value cannot start with ','; it must be an object, array, string, number, true, false or null"
        ),
        new ParseError(
          "byte-order mark",
          "\uFEFF{}",
          1,
          1,
          "$",
          "a JSON value cannot start with U+FEFF; it must be an object, array, string, number, true, false or null"
        ),
        new ParseError(
          "form feed is not JSON whitespace",
          "\f1",
          1,
          1,
          "$",
          "a JSON value cannot start with U+000C; it must be an object, array, string, number, true, false or null"
        ),
        new ParseError(
          "number without an integer part",
          ".5",
          1,
          1,
          "$",
          "a JSON value cannot start with '.'; it must be an object, array, string, number, true, false or null"
        )
      );
    }

    static Stream<ParseError> badStrings() {
      return Stream.of(
        new ParseError(
          "unclosed root string",
          "\"abc",
          1,
          5,
          "$",
          "the text ends before the string that starts at line 1, column 1 is closed; add the closing '\"'"
        ),
        new ParseError(
          "unclosed element string",
          "[\"a\", \"b",
          1,
          9,
          "$[1]",
          "the text ends before the string that starts at line 1, column 7 is closed; add the closing '\"'"
        ),
        new ParseError(
          "unclosed member name",
          "{\"ab",
          1,
          5,
          "$",
          "the text ends before the string that starts at line 1, column 2 is closed; add the closing '\"'"
        ),
        new ParseError(
          "unclosed string on the second line",
          "[\n  \"abc",
          2,
          7,
          "$[0]",
          "the text ends before the string that starts at line 2, column 3 is closed; add the closing '\"'"
        ),
        new ParseError(
          "text ends after a backslash",
          "\"a\\",
          1,
          4,
          "$",
          "the text ends before the string that starts at line 1, column 1 is closed; add the closing '\"'"
        ),
        new ParseError(
          "raw tab",
          "\"a\tb\"",
          1,
          3,
          "$",
          "the string contains an unescaped control character (U+0009); write it as \\t"
        ),
        new ParseError(
          "raw U+0001",
          "\"\u0001\"",
          1,
          2,
          "$",
          "the string contains an unescaped control character (U+0001); write it as \\u0001"
        ),
        new ParseError(
          "raw line feed",
          "[\"\n\"]",
          1,
          3,
          "$[0]",
          "the string contains an unescaped control character (U+000A); write it as \\n"
        ),
        new ParseError(
          "raw U+001F, upper-case hex",
          "\"\u001F\"",
          1,
          2,
          "$",
          "the string contains an unescaped control character (U+001F); write it as \\u001F"
        ),
        new ParseError(
          "unknown escape",
          "\"\\q\"",
          1,
          2,
          "$",
          "\\q is not a valid escape sequence; JSON allows \\\" \\\\ \\/ \\b \\f \\n \\r \\t and \\u followed by four hex" +
            " digits"
        ),
        new ParseError(
          "unknown escape in an element",
          "[\"\\x\"]",
          1,
          3,
          "$[0]",
          "\\x is not a valid escape sequence; JSON allows \\\" \\\\ \\/ \\b \\f \\n \\r \\t and \\u followed by four hex" +
            " digits"
        ),
        new ParseError(
          "backslash before a tab",
          "\"\\\t\"",
          1,
          2,
          "$",
          "\\U+0009 is not a valid escape sequence; JSON allows \\\" \\\\ \\/ \\b \\f \\n \\r \\t and \\u followed by four" +
            " hex digits"
        ),
        new ParseError(
          "non-hex digit in a Unicode escape",
          "\"\\u12G4\"",
          1,
          2,
          "$",
          "\\u12G4 is not a valid Unicode escape; \\u must be followed by four hex digits"
        ),
        new ParseError(
          "Unicode escape closed early",
          "\"\\u12\"",
          1,
          2,
          "$",
          "\\u12\" is not a valid Unicode escape; \\u must be followed by four hex digits"
        ),
        new ParseError(
          "text ends inside a Unicode escape",
          "\"\\u12",
          1,
          2,
          "$",
          "\\u12 is not a valid Unicode escape; \\u must be followed by four hex digits"
        )
      );
    }

    static Stream<ParseError> unclosedContainers() {
      return Stream.of(
        new ParseError(
          "object after a member value",
          "{\"a\":1",
          1,
          7,
          "$.a",
          "the text ends before the object that starts at line 1, column 1 is closed; add the closing '}'"
        ),
        new ParseError(
          "array after an element",
          "[1,2",
          1,
          5,
          "$[1]",
          "the text ends before the array that starts at line 1, column 1 is closed; add the closing ']'"
        ),
        new ParseError(
          "array alone",
          "[",
          1,
          2,
          "$[0]",
          "the text ends before the array that starts at line 1, column 1 is closed; add the closing ']'"
        ),
        new ParseError(
          "object alone",
          "{",
          1,
          2,
          "$",
          "the text ends before the object that starts at line 1, column 1 is closed; add the closing '}'"
        ),
        new ParseError(
          "object after ':'",
          "{\"a\":",
          1,
          6,
          "$.a",
          "the text ends before the object that starts at line 1, column 1 is closed; add the closing '}'"
        ),
        new ParseError(
          "object after a member name",
          "{\"a\"",
          1,
          5,
          "$.a",
          "the text ends before the object that starts at line 1, column 1 is closed; add the closing '}'"
        ),
        new ParseError(
          "array inside an object, the innermost is named",
          "{\"a\":[",
          1,
          7,
          "$.a[0]",
          "the text ends before the array that starts at line 1, column 6 is closed; add the closing ']'"
        )
      );
    }

    static Stream<ParseError> badSeparators() {
      return Stream.of(
        new ParseError(
          "member without a comma",
          "{\"a\":1 \"b\":2}",
          1,
          8,
          "$.a",
          "expected ',' or '}' after the member value, but found '\"'; separate members with commas and close the object" +
            " with '}'"
        ),
        new ParseError(
          "object closed with ']'",
          "{\"a\":1]",
          1,
          7,
          "$.a",
          "expected ',' or '}' after the member value, but found ']'; separate members with commas and close the object" +
            " with '}'"
        ),
        new ParseError(
          "element without a comma",
          "[1 2]",
          1,
          4,
          "$[0]",
          "expected ',' or ']' after the element, but found '2'; separate elements with commas and close the array with ']'"
        ),
        new ParseError(
          "array closed with '}'",
          "[1}",
          1,
          3,
          "$[0]",
          "expected ',' or ']' after the element, but found '}'; separate elements with commas and close the array with ']'"
        ),
        new ParseError(
          "number as a member name",
          "{1:2}",
          1,
          2,
          "$",
          "expected a member name, but found '1'; member names are strings in double quotes"
        ),
        new ParseError(
          "bare word as a later member name",
          "{\"a\":1,b:2}",
          1,
          8,
          "$",
          "expected a member name, but found 'b'; member names are strings in double quotes"
        ),
        new ParseError(
          "no ':' after the member name",
          "{\"a\" 1}",
          1,
          6,
          "$.a",
          "expected ':' after the member name \"a\", but found '1'; write \"a\": value"
        ),
        new ParseError(
          "'=' instead of ':'",
          "{\"a\"=1}",
          1,
          5,
          "$.a",
          "expected ':' after the member name \"a\", but found '='; write \"a\": value"
        )
      );
    }

    static Stream<ParseError> trailingText() {
      return Stream.of(
        new ParseError("two numbers", "1 2", 1, 3, "$", MORE_TEXT),
        new ParseError("extra closing brace", "{}}", 1, 3, "$", MORE_TEXT),
        new ParseError("two strings", "\"a\"\"b\"", 1, 4, "$", MORE_TEXT)
      );
    }

    static Stream<ParseError> locations() {
      return Stream.of(
        new ParseError("a carriage return alone does not end a line", "[1,\r2,]", 1, 7, "$[2]", ARRAY_TRAILING_COMMA),
        new ParseError("CR LF ends a line at the line feed", "[1,\r\n2,]", 2, 3, "$[2]", ARRAY_TRAILING_COMMA),
        new ParseError("columns count UTF-16 code units", "[\"\uD83D\uDE00\", x]", 1, 8, "$[1]", X_IS_NOT_A_LITERAL)
      );
    }

    static Stream<ParseError> paths() {
      return Stream.of(
        new ParseError("name with a space", "{\"a b\": [1,]}", 1, 12, "$[\"a b\"][1]", ARRAY_TRAILING_COMMA),
        new ParseError("name starting with a digit", "{\"1a\": x}", 1, 8, "$[\"1a\"]", X_IS_NOT_A_LITERAL),
        new ParseError("identifier with underscore and digit", "{\"_x1\": x}", 1, 9, "$._x1", X_IS_NOT_A_LITERAL),
        new ParseError("empty name", "{\"\": x}", 1, 6, "$[\"\"]", X_IS_NOT_A_LITERAL),
        new ParseError("name with an escaped line feed", "{\"a\\nb\": x}", 1, 10, "$[\"a\\nb\"]", X_IS_NOT_A_LITERAL),
        new ParseError("name with a non-ASCII letter", "{\"é\": x}", 1, 7, "$[\"é\"]", X_IS_NOT_A_LITERAL),
        new ParseError(
          "64 segments print in full",
          "[".repeat(64) + "x",
          1,
          65,
          "$" + "[0]".repeat(64),
          X_IS_NOT_A_LITERAL
        ),
        new ParseError(
          "65 segments are elided",
          "[".repeat(65) + "x",
          1,
          66,
          "$" + "[0]".repeat(32) + "…" + "[0]".repeat(32),
          X_IS_NOT_A_LITERAL
        )
      );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("emptyTexts")
    @DisplayName("text without a value is rejected (P1)")
    void rejectsTextWithoutValue(ParseError error) {
      assertRejected(error);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("comments")
    @DisplayName("a comment is rejected wherever a value or structure is expected (P2)")
    void rejectsComments(ParseError error) {
      assertRejected(error);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("trailingCommas")
    @DisplayName("a trailing comma is rejected (P3, P4)")
    void rejectsTrailingCommas(ParseError error) {
      assertRejected(error);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("duplicateNames")
    @DisplayName("a repeated member name is rejected at its opening quote (P5)")
    void rejectsDuplicateNames(ParseError error) {
      assertRejected(error);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("badNumbers")
    @DisplayName("a leading zero, a non-finite token or a number missing digits is rejected (P6, P7, P11)")
    void rejectsBadNumbers(ParseError error) {
      assertRejected(error);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("badValueStarts")
    @DisplayName("an unknown literal, a single quote or another character at a value position is rejected (P8–P10)")
    void rejectsBadValueStarts(ParseError error) {
      assertRejected(error);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("badStrings")
    @DisplayName("an unclosed string, a raw control character or a bad escape is rejected (P12, P14–P16)")
    void rejectsBadStrings(ParseError error) {
      assertRejected(error);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unclosedContainers")
    @DisplayName("text ending inside an object or array is rejected, naming where it starts (P13)")
    void rejectsUnclosedContainers(ParseError error) {
      assertRejected(error);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("badSeparators")
    @DisplayName("a missing comma, closer, member name or ':' is rejected (P17–P20)")
    void rejectsBadSeparators(ParseError error) {
      assertRejected(error);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("trailingText")
    @DisplayName("text after the root value is rejected (P21)")
    void rejectsTrailingText(ParseError error) {
      assertRejected(error);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("locations")
    @DisplayName("lines split on line feeds only and columns count UTF-16 code units")
    void locatesErrorsByLineFeedAndCodeUnit(ParseError error) {
      assertRejected(error);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("paths")
    @DisplayName(
      "the path writes identifier names with '.', other names quoted in brackets, and elides past 64 segments"
    )
    void writesPathsInPathSyntax(ParseError error) {
      assertRejected(error);
    }

    @Test
    @DisplayName("an elided path keeps the first 32 and the last 32 segments")
    void elidedPathKeepsHeadAndTail() {
      String text =
        IntStream.range(0, 70)
          .mapToObj(level -> "{\"k" + level + "\":")
          .collect(Collectors.joining()) + "x";
      String head = IntStream.range(0, 32)
        .mapToObj(level -> ".k" + level)
        .collect(Collectors.joining());
      String tail = IntStream.range(38, 70)
        .mapToObj(level -> ".k" + level)
        .collect(Collectors.joining());

      assertRejected(
        new ParseError("70 named levels", text, 1, text.length(), "$" + head + "…" + tail, X_IS_NOT_A_LITERAL)
      );
    }

    @Test
    @DisplayName("strict documents parse to their compact form")
    void acceptsStrictDocuments() {
      assertThat(Json.parse(" [ 1 , 2 ] ").toString()).isEqualTo("[1,2]");
      assertThat(Json.parse("{\"a\":{},\"b\":[]}").toString()).isEqualTo("{\"a\":{},\"b\":[]}");
      assertThat(Json.parse("\"\u007F\u00E9\u2028\uD83D\uDE00\"").asString()).isEqualTo(
        "\u007F\u00E9\u2028\uD83D\uDE00"
      );
      assertThat(Json.parse("1e999999999999").toString()).isEqualTo("1e999999999999");
      assertThat(Json.parse("-0.0E-0").toString()).isEqualTo("-0.0E-0");
    }

    @Test
    @DisplayName("parse(char[]) parses like parse(String) and rejects with the same message")
    void parsesCharArrayLikeString() {
      assertThat(Json.parse("{\"a\": [1, true]}".toCharArray()).toString()).isEqualTo("{\"a\":[1,true]}");
      assertThatThrownBy(() -> Json.parse("[1,]".toCharArray()))
        .isInstanceOf(JsonParseException.class)
        .hasMessage("cannot parse the JSON at line 1, column 4 ($[1]): " + ARRAY_TRAILING_COMMA);
    }

    @Test
    @DisplayName("changing the char[] after parsing leaves the document and its error locations unchanged")
    void copiesTheCharArray() {
      char[] text = "{\"a\":[12]}".toCharArray();
      JsonValue document = Json.parse(text);

      Arrays.fill(text, ' ');

      assertThat(document.toString()).isEqualTo("{\"a\":[12]}");
      assertThat(document.get("a").get(0).toString()).isEqualTo("12");
      assertThatThrownBy(() -> document.get("a").asInt())
        .isInstanceOf(JsonValueException.class)
        .hasMessage(
          "cannot read $.a (line 1, column 6) as an int: it is an array; read an array with get(int) or asList()"
        );
    }

    @Test
    @DisplayName("a null text is refused with NullPointerException")
    void refusesNullText() {
      assertThatThrownBy(() -> Json.parse((String) null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("text must not be null");
      assertThatThrownBy(() -> Json.parse((char[]) null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("text must not be null");
    }

    private void assertRejected(ParseError error) {
      assertThatThrownBy(() -> Json.parse(error.text())).isInstanceOfSatisfying(JsonParseException.class, thrown -> {
        assertThat(thrown).hasMessage(error.message());
        assertThat(thrown.getErrorLine()).isEqualTo(error.line() - 1);
        assertThat(thrown.getErrorPosition()).isEqualTo(error.column() - 1);
      });
    }
  }

  @Nested
  @DisplayName("toDisplayString")
  class ToDisplayString {

    @Test
    @DisplayName("prints one member or element per line, indented once per level, empty containers inline")
    void printsTheIndentedLayout() {
      JsonValue document = Json.parse("{\"a\":1,\"b\":[true,null],\"c\":{},\"d\":[]}");

      assertThat(Json.toDisplayString(document, "  ")).isEqualTo(
        """
        {
          "a": 1,
          "b": [
            true,
            null
          ],
          "c": {},
          "d": []
        }"""
      );
    }

    @Test
    @DisplayName("a tab indent repeats the tab per level")
    void indentsWithTabs() {
      assertThat(Json.toDisplayString(Json.parse("{\"a\":[1]}"), "\t")).isEqualTo("{\n\t\"a\": [\n\t\t1\n\t]\n}");
    }

    @Test
    @DisplayName("an empty indent keeps the line breaks without indentation")
    void emptyIndentKeepsLineBreaks() {
      assertThat(Json.toDisplayString(Json.parse("{\"a\":[1]}"), "")).isEqualTo("{\n\"a\": [\n1\n]\n}");
    }

    @Test
    @DisplayName("an indent may mix all four JSON whitespace characters")
    void acceptsEveryJsonWhitespaceInTheIndent() {
      assertThat(Json.toDisplayString(Json.parse("[1]"), " \t\r\n")).isEqualTo("[\n \t\r\n1\n]");
    }

    @Test
    @DisplayName("a scalar or empty root prints as its compact form, parsed text kept")
    void printsScalarAndEmptyRootsCompact() {
      assertThat(Json.toDisplayString(Json.parse("1.0E+2"), "  ")).isEqualTo("1.0E+2");
      assertThat(Json.toDisplayString(JsonString.of("a\"b"), "  ")).isEqualTo("\"a\\\"b\"");
      assertThat(Json.toDisplayString(JsonNull.of(), "  ")).isEqualTo("null");
      assertThat(Json.toDisplayString(Json.parse(" { } "), "  ")).isEqualTo("{}");
      assertThat(Json.toDisplayString(JsonArray.of(List.of()), "  ")).isEqualTo("[]");
    }

    @Test
    @DisplayName("an indent with any other character is refused, JSON-escaped and cut at 40 characters (F4)")
    void refusesNonWhitespaceIndent() {
      JsonValue document = Json.parse("[1]");

      assertThatThrownBy(() -> Json.toDisplayString(document, "ab"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cannot indent the JSON with \"ab\"" + INDENT_RULE);
      assertThatThrownBy(() -> Json.toDisplayString(document, "\u000B"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cannot indent the JSON with \"\\u000B\"" + INDENT_RULE);
      assertThatThrownBy(() -> Json.toDisplayString(document, "\u00A0"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cannot indent the JSON with \"\u00A0\"" + INDENT_RULE);
      assertThatThrownBy(() -> Json.toDisplayString(document, "x".repeat(41)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cannot indent the JSON with \"" + "x".repeat(40) + "…\"" + INDENT_RULE);
    }

    @Test
    @DisplayName("a null value or indent is refused with NullPointerException")
    void refusesNullArguments() {
      assertThatThrownBy(() -> Json.toDisplayString(null, "  "))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("value must not be null");
      assertThatThrownBy(() -> Json.toDisplayString(JsonNull.of(), null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("indent must not be null");
    }
  }

  record ParseError(String label, String text, int line, int column, String path, String detail) {
    String message() {
      return "cannot parse the JSON at line " + line + ", column " + column + " (" + path + "): " + detail;
    }

    @Override
    public String toString() {
      return label;
    }
  }
}
