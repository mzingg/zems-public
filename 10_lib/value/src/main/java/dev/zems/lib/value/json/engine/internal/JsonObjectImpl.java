package dev.zems.lib.value.json.engine.internal;

import dev.zems.lib.value.json.engine.JsonObject;
import dev.zems.lib.value.json.engine.JsonValue;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** The library's JSON object. */
public final class JsonObjectImpl implements JsonObject, JsonSourced {

  private final Map<String, JsonValue> members;
  private final char[] source;
  private final int offset;

  JsonObjectImpl(Map<String, JsonValue> members, char[] source, int offset) {
    this.members = members;
    this.source = source;
    this.offset = offset;
  }

  public static JsonObject of(Map<String, ? extends JsonValue> members) {
    Objects.requireNonNull(members, JsonMessages.MEMBERS_NULL);
    LinkedHashMap<String, JsonValue> copy = LinkedHashMap.newLinkedHashMap(members.size());
    for (Map.Entry<String, ? extends JsonValue> member : members.entrySet()) {
      String name = Objects.requireNonNull(member.getKey(), JsonMessages.MEMBER_NAMES_NULL);
      JsonValue value = Objects.requireNonNull(member.getValue(), () -> JsonMessages.memberValueNull(name));
      if (copy.putIfAbsent(name, value) != null) {
        throw new IllegalArgumentException(JsonMessages.duplicateInMap(name));
      }
    }
    // Map.copyOf would drop the member order the object promises.
    return new JsonObjectImpl(Collections.unmodifiableMap(copy), null, -1);
  }

  @Override
  public Map<String, JsonValue> asMap() {
    return members;
  }

  @Override
  public String toString() {
    return JsonGenerator.compact(this);
  }

  @Override
  public char[] source() {
    return source;
  }

  @Override
  public int offset() {
    return offset;
  }
}
