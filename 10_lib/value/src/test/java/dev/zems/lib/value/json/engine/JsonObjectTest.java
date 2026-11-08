package dev.zems.lib.value.json.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("JsonObject")
@ContractTest
class JsonObjectTest {

  @Nested
  @DisplayName("of")
  class Of {

    @Test
    @DisplayName("keeps the source map's iteration order")
    void keepsTheSourceMapOrder() {
      var sorted = new TreeMap<String, JsonValue>();
      sorted.put("b", JsonNumber.of(2));
      sorted.put("c", JsonNumber.of(3));
      sorted.put("a", JsonNumber.of(1));

      JsonObject object = JsonObject.of(sorted);

      assertThat(object.asMap().keySet()).containsExactly("a", "b", "c");
      assertThat(object.toString()).isEqualTo("{\"a\":1,\"b\":2,\"c\":3}");
    }

    @Test
    @DisplayName("later changes to the source map do not reach the object")
    void copiesTheSourceMap() {
      var members = new LinkedHashMap<String, JsonValue>();
      members.put("a", JsonNumber.of(1));
      JsonObject object = JsonObject.of(members);

      members.put("b", JsonNumber.of(2));
      members.remove("a");

      assertThat(object.toString()).isEqualTo("{\"a\":1}");
      assertThat(object.asMap().keySet()).containsExactly("a");
    }

    @Test
    @DisplayName("a map yielding a name twice is refused (F1)")
    void refusesARepeatedName() {
      var members = new IdentityHashMap<String, JsonValue>();
      members.put(new String("a"), JsonNumber.of(1));
      members.put(new String("a"), JsonNumber.of(2));

      assertThatThrownBy(() -> JsonObject.of(members))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
          "cannot build the JSON object: the member name \"a\" appears twice in the map; JSON objects need unique member" +
            " names, so drop one of the two entries"
        );
    }

    @Test
    @DisplayName("a null map, member name or member value is refused with NullPointerException")
    void refusesNulls() {
      var nullName = new HashMap<String, JsonValue>();
      nullName.put(null, JsonNull.of());
      var nullValue = new HashMap<String, JsonValue>();
      nullValue.put("a", null);

      assertThatThrownBy(() -> JsonObject.of(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("members must not be null");
      assertThatThrownBy(() -> JsonObject.of(nullName))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("member names must not be null");
      assertThatThrownBy(() -> JsonObject.of(nullValue))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("the value of member \"a\" must not be null");
    }
  }

  @Nested
  @DisplayName("asMap")
  class AsMap {

    @Test
    @DisplayName("keeps document order for a parsed object")
    void keepsDocumentOrder() {
      assertThat(Json.parse("{\"b\":1,\"a\":2,\"c\":3}").asMap().keySet()).containsExactly("b", "a", "c");
    }

    @Test
    @DisplayName("cannot be changed, built or parsed")
    void isUnmodifiable() {
      JsonObject built = JsonObject.of(Map.of("a", JsonNumber.of(1)));
      JsonValue parsed = Json.parse("{\"a\":1}");

      assertThatThrownBy(() -> built.asMap().put("b", JsonNull.of())).isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(() -> built.asMap().remove("a")).isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(() -> parsed.asMap().put("b", JsonNull.of())).isInstanceOf(
        UnsupportedOperationException.class
      );
      assertThatThrownBy(() -> parsed.asMap().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("get and tryGet")
  class GetAndTryGet {

    @Test
    @DisplayName("answer the member of that name")
    void answerTheMember() {
      JsonObject object = JsonObject.of(Map.of("a", JsonNumber.of(1)));

      assertThat(object.get("a").asInt()).isEqualTo(1);
      assertThat(object.tryGet("a")).hasValueSatisfying(member -> assertThat(member.asInt()).isEqualTo(1));
      assertThat(object.tryGet("b")).isEmpty();
      assertThat(Json.parse("{\"a\":null}").tryGet("a")).hasValueSatisfying(member ->
        assertThat(member).isInstanceOf(JsonNull.class)
      );
    }

    @Test
    @DisplayName("get on a missing member names the member and points to tryGet (V2)")
    void getRefusesAMissingMember() {
      assertThatThrownBy(() -> JsonObject.of(Map.of()).get("a"))
        .isInstanceOf(JsonValueException.class)
        .hasMessage(
          "cannot read member \"a\" of the object: the object has no member \"a\"; use tryGet(\"a\") when the member may be" +
            " absent"
        );
      assertThatThrownBy(() -> Json.parse("{\"b\": {}}").get("b").get("a"))
        .isInstanceOf(JsonValueException.class)
        .hasMessage(
          "cannot read member \"a\" of $.b (line 1, column 7): the object has no member \"a\"; use tryGet(\"a\") when the" +
            " member may be absent"
        );
      assertThatThrownBy(() -> JsonObject.of(Map.of()).get("a\"b"))
        .isInstanceOf(JsonValueException.class)
        .hasMessage(
          "cannot read member \"a\\\"b\" of the object: the object has no member \"a\\\"b\"; use tryGet(\"a\\\"b\") when" +
            " the member may be absent"
        );
    }

    @Test
    @DisplayName("a null name is refused with NullPointerException")
    void refuseNullName() {
      JsonObject object = JsonObject.of(Map.of());

      assertThatThrownBy(() -> object.get((String) null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("name must not be null");
      assertThatThrownBy(() -> object.tryGet(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("name must not be null");
    }
  }

  @Nested
  @DisplayName("toString")
  class ToString {

    @Test
    @DisplayName("prints compact JSON with member names minimally escaped")
    void printsCompactJson() {
      var members = new LinkedHashMap<String, JsonValue>();
      members.put("a\"b\n", JsonNull.of());
      members.put("list", JsonArray.of(List.of(JsonNumber.of(1), JsonObject.of(Map.of()))));

      assertThat(JsonObject.of(members).toString()).isEqualTo("{\"a\\\"b\\n\":null,\"list\":[1,{}]}");
    }

    @Test
    @DisplayName("prints a parsed member name from its decoded text, not as written")
    void printsParsedNamesDecoded() {
      assertThat(Json.parse("{ \"\\u0041\\/\" : 1 }").toString()).isEqualTo("{\"A/\":1}");
    }
  }
}
