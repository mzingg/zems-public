package dev.zems.lib.value.json.engine.internal;

import dev.zems.lib.value.json.engine.JsonArray;
import dev.zems.lib.value.json.engine.JsonBoolean;
import dev.zems.lib.value.json.engine.JsonNull;
import dev.zems.lib.value.json.engine.JsonNumber;
import dev.zems.lib.value.json.engine.JsonObject;
import dev.zems.lib.value.json.engine.JsonString;
import dev.zems.lib.value.json.engine.JsonValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SequencedCollection;

/** Every message the JSON document model puts into an exception: what happened, why, and what the caller can do. */
public final class JsonMessages {

  public static final String TEXT_NULL = "text must not be null";
  public static final String VALUE_NULL = "value must not be null";
  public static final String INDENT_NULL = "indent must not be null";
  public static final String NAME_NULL = "name must not be null";
  static final String MEMBERS_NULL = "members must not be null";
  static final String MEMBER_NAMES_NULL = "member names must not be null";
  static final String ELEMENTS_NULL = "elements must not be null";

  public static final String BOOLEAN = "a boolean";
  public static final String INT = "an int";
  public static final String LONG = "a long";
  public static final String DOUBLE = "a double";
  public static final String STRING = "a string";
  public static final String ARRAY = "an array";
  public static final String OBJECT = "an object";

  private static final int QUOTE_LIMIT = 40;
  private static final int PATH_HEAD = 32;
  private static final int PATH_TAIL = 32;
  private static final String ELLIPSIS = "…";
  private static final char FIRST_PRINTABLE = '!';
  private static final char LAST_PRINTABLE = '~';

  private static final String CANNOT_READ = "cannot read ";
  private static final String THE_VALUE = "the value";
  private static final String EXACT_VALUE = "new BigDecimal(toString()) for the exact value";
  private static final String NOT_ALLOWED = ", which JSON does not allow; ";
  private static final String BUT_FOUND = ", but found ";

  private JsonMessages() {}

  static String memberValueNull(String name) {
    return "the value of member " + quoted(name) + " must not be null";
  }

  static String elementNull(int index) {
    return "element " + index + " must not be null";
  }

  public static String negativeLocation(int line, int position) {
    return "line and position must not be negative, got line " + line + ", position " + position;
  }

  static String lineAndColumn(int line, int column) {
    return "line " + line + ", column " + column;
  }

  static String parseError(String lineAndColumn, String path, String detail) {
    return "cannot parse the JSON at " + lineAndColumn + " (" + path + "): " + detail;
  }

  static String noValue() {
    return "the text contains no JSON value; pass a value such as {}, [] or \"text\"";
  }

  static String comment() {
    return "JSON does not allow comments; remove the comment";
  }

  static String trailingComma(boolean object) {
    return "the " + kind(object) + " ends with a trailing comma" + NOT_ALLOWED + "remove the comma";
  }

  static String duplicateMember(String name) {
    return (
      "the member name " + quoted(name) + " appears twice in the same object; rename or remove one of the two members"
    );
  }

  static String leadingZero(String text) {
    return (
      "the number " +
      cut(text) +
      " has a leading zero" +
      NOT_ALLOWED +
      "remove the zero, or write the value as a string if the zero matters"
    );
  }

  static String nonFiniteNumber(String token) {
    return token + " is not a valid JSON number; use null, or a string such as \"" + token + "\"";
  }

  static String unknownLiteral(String run) {
    return cut(run) + " is not valid JSON; the only literals are true, false and null, written in lower case";
  }

  static String singleQuote() {
    return "a JSON value cannot start with a single quote; JSON strings use double quotes";
  }

  static String unexpectedValueStart(int codePoint) {
    return (
      "a JSON value cannot start with " +
      character(codePoint) +
      "; it must be an object, array, string, number, true, false or null"
    );
  }

  static String incompleteNumber(String text, char mark) {
    return "the number " + cut(text) + " is incomplete because no digit follows '" + mark + "'; add the missing digits";
  }

  static String unclosedString(String startLineAndColumn) {
    return "the text ends before the string that starts at " + startLineAndColumn + " is closed; add the closing '\"'";
  }

  static String unclosedContainer(boolean object, String startLineAndColumn) {
    return (
      "the text ends before the " +
      kind(object) +
      " that starts at " +
      startLineAndColumn +
      " is closed; add the closing '" +
      (object ? '}' : ']') +
      "'"
    );
  }

  static String controlCharacter(char control) {
    return (
      "the string contains an unescaped control character (" +
      codePointName(control) +
      "); write it as " +
      JsonGenerator.escape(control)
    );
  }

  static String invalidEscape(char escape) {
    String shown = isPrintable(escape) ? String.valueOf(escape) : codePointName(escape);
    return (
      "\\" +
      shown +
      " is not a valid escape sequence; JSON allows \\\" \\\\ \\/ \\b \\f \\n \\r \\t and \\u followed by" +
      " four hex digits"
    );
  }

  static String invalidUnicodeEscape(String digits) {
    return "\\u" + digits + " is not a valid Unicode escape; \\u must be followed by four hex digits";
  }

  static String expectedMemberSeparator(int found) {
    return (
      "expected ',' or '}' after the member value" +
      BUT_FOUND +
      character(found) +
      "; separate members with commas and close the object with '}'"
    );
  }

  static String expectedElementSeparator(int found) {
    return (
      "expected ',' or ']' after the element" +
      BUT_FOUND +
      character(found) +
      "; separate elements with commas and close the array with ']'"
    );
  }

  static String expectedMemberName(int found) {
    return "expected a member name" + BUT_FOUND + character(found) + "; member names are strings in double quotes";
  }

  static String expectedColon(String name, int found) {
    return (
      "expected ':' after the member name " +
      quoted(name) +
      BUT_FOUND +
      character(found) +
      "; write " +
      quoted(name) +
      ": value"
    );
  }

  static String trailingText() {
    return "there is more text after the JSON value; a JSON text contains exactly one value, so wrap several values in an array";
  }

  public static String typeError(JsonValue value, String target) {
    return readAs(subject(value, THE_VALUE), target) + actual(value);
  }

  public static String memberTypeError(JsonValue value, String name) {
    return CANNOT_READ + member(name, subject(value, THE_VALUE)) + actual(value);
  }

  public static String elementTypeError(JsonValue value, int index) {
    return CANNOT_READ + element(index, subject(value, THE_VALUE)) + actual(value);
  }

  public static String missingMember(JsonObject object, String name) {
    return (
      CANNOT_READ +
      member(name, subject(object, "the object")) +
      "the object has no member " +
      quoted(name) +
      "; use tryGet(" +
      quoted(name) +
      ") when the member may be absent"
    );
  }

  public static String elementOutOfBounds(JsonArray array, int index, int size) {
    String extent = switch (size) {
      case 0 -> "the array is empty";
      case 1 -> "the array has 1 element (index 0)";
      default -> "the array has " + size + " elements (indices 0 to " + (size - 1) + ")";
    };
    return CANNOT_READ + element(index, subject(array, "the array")) + extent + "; check asList().size() first";
  }

  static String notWhole(JsonNumber number, String target) {
    return (
      readAs(subject(number, THE_VALUE), target) +
      cut(number.toString()) +
      " is not a whole number; use asDouble(), or " +
      EXACT_VALUE
    );
  }

  static String outsideIntRange(JsonNumber number) {
    return (
      readAs(subject(number, THE_VALUE), INT) +
      cut(number.toString()) +
      " is outside the int range; use asLong(), or " +
      EXACT_VALUE
    );
  }

  static String outsideLongRange(JsonNumber number) {
    return (
      readAs(subject(number, THE_VALUE), LONG) +
      cut(number.toString()) +
      " is outside the long range; use " +
      EXACT_VALUE
    );
  }

  static String outsideDoubleRange(JsonNumber number) {
    return (
      readAs(subject(number, THE_VALUE), DOUBLE) +
      cut(number.toString()) +
      " is outside the finite double range; use " +
      EXACT_VALUE
    );
  }

  static String duplicateInMap(String name) {
    return (
      "cannot build the JSON object: the member name " +
      quoted(name) +
      " appears twice in the map; JSON objects need unique member names, so drop one of the two entries"
    );
  }

  static String nonFiniteDouble(double number) {
    return (
      "cannot make a JSON number from " +
      number +
      ": JSON numbers must be finite; convert NaN and infinite values to null or a string first"
    );
  }

  static String notANumber(String text) {
    return (
      "cannot make a JSON number from " +
      quoted(text) +
      ": the text is not a valid JSON number; pass text such as 12, -3.5 or 1e10"
    );
  }

  public static String invalidIndent(String indent) {
    return (
      "cannot indent the JSON with " +
      quoted(indent) +
      ": an indent may contain only spaces, tabs, line feeds and carriage returns"
    );
  }

  static String path(SequencedCollection<? extends JsonFrame> frames) {
    int segments = frames.size();
    if (segments > 0 && frames.getLast().isObject() && frames.getLast().name() == null) {
      segments--;
    }
    List<Object> steps = new ArrayList<>(segments);
    for (JsonFrame frame : frames) {
      if (steps.size() == segments) {
        break;
      }
      steps.add(frame.isObject() ? frame.name() : frame.index());
    }
    return path(steps);
  }

  static String path(List<?> steps) {
    int segments = steps.size();
    StringBuilder path = new StringBuilder("$");
    for (int position = 0; position < segments; position++) {
      if (position == PATH_HEAD && segments > PATH_HEAD + PATH_TAIL) {
        path.append(ELLIPSIS);
        position = segments - PATH_TAIL;
      }
      appendSegment(path, steps.get(position));
    }
    return path.toString();
  }

  private static void appendSegment(StringBuilder path, Object step) {
    if (step instanceof Integer index) {
      path.append('[').append(index).append(']');
    } else if (step instanceof String name && isIdentifier(name)) {
      path.append('.').append(cut(name));
    } else {
      path
        .append('[')
        .append(quoted((String) step))
        .append(']');
    }
  }

  private static boolean isIdentifier(String name) {
    if (name.isEmpty() || !isIdentifierStart(name.charAt(0))) {
      return false;
    }
    for (int at = 1; at < name.length(); at++) {
      if (!isIdentifierStart(name.charAt(at)) && !JsonParser.isDigit(name.charAt(at))) {
        return false;
      }
    }
    return true;
  }

  private static boolean isIdentifierStart(char character) {
    return (character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z') || character == '_';
  }

  private static String subject(JsonValue value, String builtSubject) {
    if (value instanceof JsonSourced sourced && sourced.source() != null) {
      char[] source = sourced.source();
      JsonLocator.Position position = JsonLocator.position(source, sourced.offset());
      return (
        JsonLocator.path(source, sourced.offset()) +
        " (" +
        lineAndColumn(position.line() + 1, position.column() + 1) +
        ")"
      );
    }
    return builtSubject;
  }

  private static String readAs(String subject, String target) {
    return CANNOT_READ + subject + " as " + target + ": ";
  }

  private static String member(String name, String subject) {
    return "member " + quoted(name) + " of " + subject + ": ";
  }

  private static String element(int index, String subject) {
    return "element " + index + " of " + subject + ": ";
  }

  private static String actual(JsonValue value) {
    return switch (value) {
      case JsonObject _ -> "it is an object; read an object with get(String), tryGet(String) or asMap()";
      case JsonArray _ -> "it is an array; read an array with get(int) or asList()";
      case JsonString _ -> "it is a string; read a string with asString()";
      case JsonNumber _ -> "it is a number; read a number with asInt(), asLong() or asDouble()";
      case JsonBoolean _ -> "it is a boolean; read a boolean with asBoolean()";
      case JsonNull _ -> "it is null; check for null with tryValue() or a JsonNull pattern before reading";
    };
  }

  private static String kind(boolean object) {
    return object ? "object" : "array";
  }

  private static String quoted(String text) {
    StringBuilder quoted = new StringBuilder();
    JsonGenerator.appendQuoted(quoted, cut(text));
    return quoted.toString();
  }

  private static String cut(String text) {
    if (text.length() <= QUOTE_LIMIT) {
      return text;
    }
    int end = Character.isHighSurrogate(text.charAt(QUOTE_LIMIT - 1)) ? QUOTE_LIMIT - 1 : QUOTE_LIMIT;
    return text.substring(0, end) + ELLIPSIS;
  }

  private static String character(int codePoint) {
    return isPrintable(codePoint) ? "'" + (char) codePoint + "'" : codePointName(codePoint);
  }

  private static boolean isPrintable(int codePoint) {
    return codePoint >= FIRST_PRINTABLE && codePoint <= LAST_PRINTABLE;
  }

  private static String codePointName(int codePoint) {
    return String.format(Locale.ROOT, "U+%04X", codePoint);
  }
}
