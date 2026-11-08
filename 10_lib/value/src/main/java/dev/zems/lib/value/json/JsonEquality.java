package dev.zems.lib.value.json;

import dev.zems.lib.value.json.engine.JsonArray;
import dev.zems.lib.value.json.engine.JsonBoolean;
import dev.zems.lib.value.json.engine.JsonNull;
import dev.zems.lib.value.json.engine.JsonNumber;
import dev.zems.lib.value.json.engine.JsonObject;
import dev.zems.lib.value.json.engine.JsonString;
import dev.zems.lib.value.json.engine.JsonValue;
import dev.zems.lib.value.json.engine.internal.NumberForm;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Value equality and hashing over engine trees, on a heap stack: numbers numerically, objects by members in any
 * order, arrays element by element.
 */
final class JsonEquality {

  private static final int EMPTY_OBJECT_HASH = 0;
  private static final int EMPTY_ARRAY_HASH = 1;
  private static final int ELEMENT_FACTOR = 31;

  private JsonEquality() {}

  static boolean equal(JsonValue left, JsonValue right) {
    ArrayDeque<JsonValue> pending = new ArrayDeque<>();
    pending.push(right);
    pending.push(left);
    while (!pending.isEmpty()) {
      JsonValue a = pending.pop();
      JsonValue b = pending.pop();
      if (!isEqualAtTop(a, b, pending)) {
        return false;
      }
    }
    return true;
  }

  static int hash(JsonValue root) {
    ArrayDeque<HashFrame> open = new ArrayDeque<>();
    JsonValue next = root;
    while (true) {
      HashFrame opened = HashFrame.openOrNull(next);
      if (opened != null) {
        open.push(opened);
        next = opened.nextChild();
        continue;
      }
      int hash = leafHash(next);
      while (true) {
        HashFrame top = open.peek();
        if (top == null) {
          return hash;
        }
        top.fold(hash);
        if (top.hasNext()) {
          next = top.nextChild();
          break;
        }
        open.pop();
        hash = top.result();
      }
    }
  }

  private static boolean isEqualAtTop(JsonValue a, JsonValue b, ArrayDeque<JsonValue> pending) {
    return switch (a) {
      case JsonObject left -> b instanceof JsonObject right && pushMembers(left.asMap(), right.asMap(), pending);
      case JsonArray left -> b instanceof JsonArray right && pushElements(left.asList(), right.asList(), pending);
      case JsonString left -> b instanceof JsonString right && left.asString().equals(right.asString());
      case JsonNumber left -> b instanceof JsonNumber right && form(left).isSameNumber(form(right));
      case JsonBoolean left -> b instanceof JsonBoolean right && left.asBoolean() == right.asBoolean();
      case JsonNull _ -> b instanceof JsonNull;
    };
  }

  private static boolean pushMembers(
    Map<String, JsonValue> left,
    Map<String, JsonValue> right,
    ArrayDeque<JsonValue> pending
  ) {
    if (left.size() != right.size()) {
      return false;
    }
    for (Map.Entry<String, JsonValue> member : left.entrySet()) {
      JsonValue other = right.get(member.getKey());
      if (other == null) {
        return false;
      }
      pending.push(other);
      pending.push(member.getValue());
    }
    return true;
  }

  private static boolean pushElements(List<JsonValue> left, List<JsonValue> right, ArrayDeque<JsonValue> pending) {
    if (left.size() != right.size()) {
      return false;
    }
    for (int index = 0; index < left.size(); index++) {
      pending.push(right.get(index));
      pending.push(left.get(index));
    }
    return true;
  }

  private static int leafHash(JsonValue value) {
    return switch (value) {
      case JsonObject _ -> EMPTY_OBJECT_HASH;
      case JsonArray _ -> EMPTY_ARRAY_HASH;
      case JsonString string -> string.asString().hashCode();
      case JsonNumber number -> form(number).numberHash();
      case JsonBoolean bool -> Boolean.hashCode(bool.asBoolean());
      case JsonNull _ -> 0;
    };
  }

  private static NumberForm form(JsonNumber number) {
    return NumberForm.of(number.toString());
  }

  /** A non-empty object or array whose children are being hashed. */
  private static final class HashFrame {

    private final Iterator<Map.Entry<String, JsonValue>> members;
    private final Iterator<JsonValue> elements;
    private String memberName;
    private int accumulated;

    private HashFrame(Iterator<Map.Entry<String, JsonValue>> members, Iterator<JsonValue> elements, int start) {
      this.members = members;
      this.elements = elements;
      this.accumulated = start;
    }

    static HashFrame openOrNull(JsonValue value) {
      if (value instanceof JsonObject object && !object.asMap().isEmpty()) {
        return new HashFrame(object.asMap().entrySet().iterator(), null, EMPTY_OBJECT_HASH);
      }
      if (value instanceof JsonArray array && !array.asList().isEmpty()) {
        return new HashFrame(null, array.asList().iterator(), EMPTY_ARRAY_HASH);
      }
      return null;
    }

    boolean hasNext() {
      return members == null ? elements.hasNext() : members.hasNext();
    }

    JsonValue nextChild() {
      if (members == null) {
        return elements.next();
      }
      Map.Entry<String, JsonValue> member = members.next();
      memberName = member.getKey();
      return member.getValue();
    }

    // Objects sum their members so that member order does not change the hash; arrays fold in order.
    void fold(int childHash) {
      if (members == null) {
        accumulated = ELEMENT_FACTOR * accumulated + childHash;
      } else {
        accumulated += memberName.hashCode() ^ childHash;
      }
    }

    int result() {
      return accumulated;
    }
  }
}
