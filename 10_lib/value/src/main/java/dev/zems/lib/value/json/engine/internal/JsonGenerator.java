package dev.zems.lib.value.json.engine.internal;

import dev.zems.lib.value.json.engine.JsonArray;
import dev.zems.lib.value.json.engine.JsonObject;
import dev.zems.lib.value.json.engine.JsonValue;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Prints JSON values, compact or indented; it keeps open containers on a heap stack, never on the call stack. */
public final class JsonGenerator {

  private final StringBuilder out = new StringBuilder();
  private final ArrayDeque<Frame> frames = new ArrayDeque<>();
  private final boolean display;
  private final String indent;

  private JsonGenerator(boolean display, String indent) {
    this.display = display;
    this.indent = indent;
  }

  static String compact(JsonValue value) {
    return new JsonGenerator(false, "").print(value);
  }

  public static String display(JsonValue value, String indent) {
    return new JsonGenerator(true, indent).print(value);
  }

  static void appendQuoted(StringBuilder out, String text) {
    out.append('"');
    for (int at = 0; at < text.length(); at++) {
      char character = text.charAt(at);
      if (needsEscape(character)) {
        out.append(escape(character));
      } else {
        out.append(character);
      }
    }
    out.append('"');
  }

  static boolean needsEscape(char character) {
    return character == '"' || character == '\\' || character < ' ';
  }

  static String escape(char character) {
    return switch (character) {
      case '"' -> "\\\"";
      case '\\' -> "\\\\";
      case '\b' -> "\\b";
      case '\f' -> "\\f";
      case '\n' -> "\\n";
      case '\r' -> "\\r";
      case '\t' -> "\\t";
      default -> String.format(Locale.ROOT, "\\u%04X", (int) character);
    };
  }

  private String print(JsonValue root) {
    begin(root);
    while (!frames.isEmpty()) {
      Frame frame = frames.getLast();
      if (frame.hasNext()) {
        if (frame.isFirst()) {
          frame.started();
        } else {
          out.append(',');
        }
        lineBreak(frames.size());
        begin(next(frame));
      } else {
        frames.removeLast();
        lineBreak(frames.size());
        out.append(frame.closer());
      }
    }
    return out.toString();
  }

  private JsonValue next(Frame frame) {
    if (frame.members == null) {
      return frame.elements.next();
    }
    Map.Entry<String, JsonValue> member = frame.members.next();
    appendQuoted(out, member.getKey());
    out.append(display ? ": " : ":");
    return member.getValue();
  }

  private void begin(JsonValue value) {
    if (value instanceof JsonObject object) {
      Map<String, JsonValue> members = object.asMap();
      if (members.isEmpty()) {
        out.append("{}");
      } else {
        out.append('{');
        frames.addLast(new Frame(members.entrySet().iterator(), null));
      }
    } else if (value instanceof JsonArray array) {
      List<JsonValue> elements = array.asList();
      if (elements.isEmpty()) {
        out.append("[]");
      } else {
        out.append('[');
        frames.addLast(new Frame(null, elements.iterator()));
      }
    } else {
      out.append(value.toString());
    }
  }

  private void lineBreak(int depth) {
    if (display) {
      out.append('\n').repeat(indent, depth);
    }
  }

  private static final class Frame {

    private final Iterator<Map.Entry<String, JsonValue>> members;
    private final Iterator<JsonValue> elements;
    private boolean first = true;

    Frame(Iterator<Map.Entry<String, JsonValue>> members, Iterator<JsonValue> elements) {
      this.members = members;
      this.elements = elements;
    }

    boolean hasNext() {
      return members == null ? elements.hasNext() : members.hasNext();
    }

    boolean isFirst() {
      return first;
    }

    void started() {
      first = false;
    }

    char closer() {
      return members == null ? ']' : '}';
    }
  }
}
