package dev.zems.lib.value.json.engine.internal;

import dev.zems.lib.value.json.engine.JsonParseException;
import dev.zems.lib.value.json.engine.JsonValue;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strict RFC 8259 parser over a text it owns; it keeps open containers on a heap stack, never on the call stack. */
public final class JsonParser {

  private static final int UNICODE_ESCAPE_DIGITS = 4;
  private static final int HEX_RADIX = 16;
  private static final int NOT_AN_ESCAPE = -1;
  private static final String INFINITY = "Infinity";

  private final char[] source;
  private final ArrayDeque<Container> containers = new ArrayDeque<>();
  private int index;

  private JsonParser(char[] source) {
    this.source = source;
  }

  public static JsonValue parse(char[] source) {
    return new JsonParser(source).document();
  }

  public static boolean isWhitespace(int character) {
    return character == ' ' || character == '\t' || character == '\n' || character == '\r';
  }

  static boolean isDigit(char character) {
    return character >= '0' && character <= '9';
  }

  static int simpleEscape(char escape) {
    return switch (escape) {
      case '"', '\\', '/' -> escape;
      case 'b' -> '\b';
      case 'f' -> '\f';
      case 'n' -> '\n';
      case 'r' -> '\r';
      case 't' -> '\t';
      default -> NOT_AN_ESCAPE;
    };
  }

  private JsonValue document() {
    JsonValue value = valueOrOpen();
    while (value == null || !containers.isEmpty()) {
      value = value == null ? firstMemberOrClose() : nextMemberOrClose(value);
    }
    skipWhitespace();
    if (index < source.length) {
      throw error(index, source[index] == '/' ? JsonMessages.comment() : JsonMessages.trailingText());
    }
    return value;
  }

  private JsonValue firstMemberOrClose() {
    Container top = containers.getLast();
    skipWhitespace();
    requireMoreText();
    if (source[index] == top.closer()) {
      return close();
    }
    if (top.isObject()) {
      memberName(top);
    }
    return valueOrOpen();
  }

  private JsonValue nextMemberOrClose(JsonValue value) {
    Container top = containers.getLast();
    top.add(value);
    skipWhitespace();
    requireMoreText();
    char character = source[index];
    if (character == top.closer()) {
      return close();
    }
    if (character != ',') {
      throw error(index, character == '/' ? JsonMessages.comment() : missingSeparator(top));
    }
    index++;
    top.next();
    skipWhitespace();
    requireMoreText();
    if (source[index] == top.closer()) {
      throw error(index, JsonMessages.trailingComma(top.isObject()));
    }
    if (top.isObject()) {
      memberName(top);
    }
    return valueOrOpen();
  }

  private String missingSeparator(Container top) {
    int found = Character.codePointAt(source, index);
    return top.isObject() ? JsonMessages.expectedMemberSeparator(found) : JsonMessages.expectedElementSeparator(found);
  }

  private void memberName(Container top) {
    int quote = index;
    if (source[quote] != '"') {
      throw error(quote, source[quote] == '/' ? JsonMessages.comment() : JsonMessages.expectedMemberName(codePoint()));
    }
    String name = string();
    top.name(name);
    if (top.hasMember(name)) {
      throw error(quote, JsonMessages.duplicateMember(name));
    }
    skipWhitespace();
    requireMoreText();
    if (source[index] != ':') {
      throw error(index, source[index] == '/' ? JsonMessages.comment() : JsonMessages.expectedColon(name, codePoint()));
    }
    index++;
  }

  private JsonValue valueOrOpen() {
    skipWhitespace();
    requireMoreText();
    int start = index;
    char character = source[start];
    return switch (character) {
      case '{', '[' -> open(character == '{');
      case '"' -> {
        String text = string();
        yield new JsonStringImpl(text, source, start, index);
      }
      case '\'' -> throw error(start, JsonMessages.singleQuote());
      case '/' -> throw error(start, JsonMessages.comment());
      default -> {
        if (character == '-' || isDigit(character)) {
          yield number();
        }
        if (isLetter(character)) {
          yield literal();
        }
        throw error(start, JsonMessages.unexpectedValueStart(codePoint()));
      }
    };
  }

  private JsonValue open(boolean object) {
    containers.addLast(new Container(object, index));
    index++;
    return null;
  }

  private JsonValue close() {
    Container closed = containers.removeLast();
    index++;
    return closed.toValue(source);
  }

  private JsonValue literal() {
    int start = index;
    int end = letterRunEnd(start);
    String run = new String(source, start, end - start);
    JsonValue value = switch (run) {
      case "true" -> new JsonBooleanImpl(true, source, start);
      case "false" -> new JsonBooleanImpl(false, source, start);
      case "null" -> new JsonNullImpl(source, start);
      case "NaN", INFINITY -> throw error(start, JsonMessages.nonFiniteNumber(run));
      default -> throw error(start, JsonMessages.unknownLiteral(run));
    };
    index = end;
    return value;
  }

  private JsonValue number() {
    int start = index;
    int cursor = start;
    if (source[cursor] == '-') {
      cursor++;
      if (isInfinity(cursor)) {
        throw error(start, JsonMessages.nonFiniteNumber("-" + INFINITY));
      }
      requireDigit(start, cursor, '-');
    }
    if (source[cursor] == '0') {
      cursor++;
      if (isDigitAt(cursor)) {
        throw error(start, JsonMessages.leadingZero(text(start, numberRunEnd(start))));
      }
    } else {
      cursor = digitsEnd(cursor);
    }
    if (cursor < source.length && source[cursor] == '.') {
      cursor++;
      requireDigit(start, cursor, '.');
      cursor = digitsEnd(cursor);
    }
    if (cursor < source.length && (source[cursor] == 'e' || source[cursor] == 'E')) {
      char mark = source[cursor];
      cursor++;
      if (cursor < source.length && (source[cursor] == '+' || source[cursor] == '-')) {
        cursor++;
      }
      requireDigit(start, cursor, mark);
      cursor = digitsEnd(cursor);
    }
    index = cursor;
    return new JsonNumberImpl(text(start, cursor), source, start);
  }

  private void requireDigit(int numberStart, int cursor, char mark) {
    if (!isDigitAt(cursor)) {
      throw error(cursor, JsonMessages.incompleteNumber(text(numberStart, cursor), mark));
    }
  }

  private boolean isInfinity(int start) {
    return text(start, letterRunEnd(start)).equals(INFINITY);
  }

  private String string() {
    int quote = index;
    int cursor = quote + 1;
    int runStart = cursor;
    StringBuilder decoded = null;
    while (true) {
      if (cursor == source.length) {
        throw error(cursor, JsonMessages.unclosedString(lineAndColumn(quote)));
      }
      char character = source[cursor];
      if (character == '"') {
        break;
      }
      if (character < ' ') {
        throw error(cursor, JsonMessages.controlCharacter(character));
      }
      if (character == '\\') {
        if (decoded == null) {
          decoded = new StringBuilder();
        }
        decoded.append(source, runStart, cursor - runStart);
        cursor = escape(quote, cursor, decoded);
        runStart = cursor;
      } else {
        cursor++;
      }
    }
    index = cursor + 1;
    if (decoded == null) {
      return new String(source, quote + 1, cursor - quote - 1);
    }
    return decoded.append(source, runStart, cursor - runStart).toString();
  }

  private int escape(int quote, int backslash, StringBuilder decoded) {
    int escapeAt = backslash + 1;
    if (escapeAt == source.length) {
      throw error(escapeAt, JsonMessages.unclosedString(lineAndColumn(quote)));
    }
    char escape = source[escapeAt];
    if (escape == 'u') {
      decoded.append(unicodeEscape(backslash));
      return escapeAt + 1 + UNICODE_ESCAPE_DIGITS;
    }
    int simple = simpleEscape(escape);
    if (simple == NOT_AN_ESCAPE) {
      throw error(backslash, JsonMessages.invalidEscape(escape));
    }
    decoded.append((char) simple);
    return escapeAt + 1;
  }

  private char unicodeEscape(int backslash) {
    int digitsStart = backslash + 2;
    int value = 0;
    for (int at = digitsStart; at < digitsStart + UNICODE_ESCAPE_DIGITS; at++) {
      int digit = at < source.length ? hexDigit(source[at]) : -1;
      if (digit < 0) {
        int shownEnd = Math.min(digitsStart + UNICODE_ESCAPE_DIGITS, source.length);
        throw error(backslash, JsonMessages.invalidUnicodeEscape(text(digitsStart, shownEnd)));
      }
      value = value * HEX_RADIX + digit;
    }
    return (char) value;
  }

  private static int hexDigit(char character) {
    if (isDigit(character)) {
      return character - '0';
    }
    if (character >= 'a' && character <= 'f') {
      return character - 'a' + 10;
    }
    if (character >= 'A' && character <= 'F') {
      return character - 'A' + 10;
    }
    return -1;
  }

  private void skipWhitespace() {
    while (index < source.length && isWhitespace(source[index])) {
      index++;
    }
  }

  private void requireMoreText() {
    if (index < source.length) {
      return;
    }
    if (containers.isEmpty()) {
      throw error(index, JsonMessages.noValue());
    }
    Container open = containers.getLast();
    throw error(index, JsonMessages.unclosedContainer(open.isObject(), lineAndColumn(open.start())));
  }

  private boolean isDigitAt(int at) {
    return at < source.length && isDigit(source[at]);
  }

  private static boolean isLetter(char character) {
    return (character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z');
  }

  private int letterRunEnd(int start) {
    int end = start;
    while (end < source.length && isLetter(source[end])) {
      end++;
    }
    return end;
  }

  private int digitsEnd(int start) {
    int end = start;
    while (isDigitAt(end)) {
      end++;
    }
    return end;
  }

  private int numberRunEnd(int start) {
    int end = start;
    while (end < source.length && (isDigit(source[end]) || "+-.eE".indexOf(source[end]) >= 0)) {
      end++;
    }
    return end;
  }

  private String text(int start, int end) {
    return new String(source, start, end - start);
  }

  private int codePoint() {
    return Character.codePointAt(source, index);
  }

  private String lineAndColumn(int at) {
    JsonLocator.Position position = JsonLocator.position(source, at);
    return JsonMessages.lineAndColumn(position.line() + 1, position.column() + 1);
  }

  private JsonParseException error(int at, String detail) {
    JsonLocator.Position position = JsonLocator.position(source, at);
    String message = JsonMessages.parseError(
      JsonMessages.lineAndColumn(position.line() + 1, position.column() + 1),
      JsonMessages.path(containers),
      detail
    );
    return new JsonParseException(message, position.line(), position.column());
  }

  private static final class Container extends JsonFrame {

    private final int start;
    private final Map<String, JsonValue> members;
    private final List<JsonValue> elements;

    Container(boolean object, int start) {
      super(object);
      this.start = start;
      this.members = object ? new LinkedHashMap<>() : null;
      this.elements = object ? null : new ArrayList<>();
    }

    int start() {
      return start;
    }

    char closer() {
      return isObject() ? '}' : ']';
    }

    boolean hasMember(String name) {
      return members.containsKey(name);
    }

    void add(JsonValue value) {
      if (isObject()) {
        members.put(name(), value);
      } else {
        elements.add(value);
      }
    }

    JsonValue toValue(char[] source) {
      if (isObject()) {
        return new JsonObjectImpl(Collections.unmodifiableMap(members), source, start);
      }
      return new JsonArrayImpl(Collections.unmodifiableList(elements), source, start);
    }
  }
}
