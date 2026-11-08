package dev.zems.lib.value.json.engine.internal;

import java.util.ArrayDeque;

/** Finds where a parsed value sits by reading its valid source again, up to the value's first character. */
final class JsonLocator {

  private static final int ESCAPE_PREFIX_LENGTH = 2;
  private static final int HEX_DIGITS = 4;
  private static final int HEX_RADIX = 16;

  private JsonLocator() {}

  /** A zero-based line and column; a line ends at a line feed, columns count UTF-16 code units. */
  record Position(int line, int column) {}

  static Position position(char[] source, int index) {
    int line = 0;
    int lineStart = 0;
    for (int at = 0; at < index; at++) {
      if (source[at] == '\n') {
        line++;
        lineStart = at + 1;
      }
    }
    return new Position(line, index - lineStart);
  }

  static String path(char[] source, int offset) {
    ArrayDeque<JsonFrame> frames = new ArrayDeque<>();
    int at = 0;
    while (at < offset) {
      switch (source[at]) {
        case '{' -> frames.addLast(new JsonFrame(true));
        case '[' -> frames.addLast(new JsonFrame(false));
        case '}', ']' -> frames.removeLast();
        case ',' -> frames.getLast().next();
        case '"' -> at = string(source, at, frames.peekLast());
        default -> {
        }
      }
      at++;
    }
    return JsonMessages.path(frames);
  }

  private static int string(char[] source, int quote, JsonFrame top) {
    int closingQuote = quote + 1;
    while (source[closingQuote] != '"') {
      closingQuote += source[closingQuote] == '\\' ? 2 : 1;
    }
    boolean isMemberName = top != null && top.isObject() && top.name() == null;
    if (isMemberName) {
      top.name(decode(source, quote + 1, closingQuote));
    }
    return closingQuote;
  }

  private static String decode(char[] source, int start, int end) {
    StringBuilder decoded = new StringBuilder(end - start);
    int at = start;
    while (at < end) {
      char character = source[at];
      if (character != '\\') {
        decoded.append(character);
        at++;
      } else if (source[at + 1] == 'u') {
        decoded.append((char) Integer.parseInt(new String(source, at + ESCAPE_PREFIX_LENGTH, HEX_DIGITS), HEX_RADIX));
        at += ESCAPE_PREFIX_LENGTH + HEX_DIGITS;
      } else {
        decoded.append((char) JsonParser.simpleEscape(source[at + 1]));
        at += ESCAPE_PREFIX_LENGTH;
      }
    }
    return decoded.toString();
  }
}
