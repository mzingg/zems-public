package dev.zems.lib.value.json;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;

import dev.zems.lib.common.junit.JourneyTest;
import dev.zems.lib.value.Navigator;
import dev.zems.lib.value.NullValue;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.json.engine.JsonArray;
import dev.zems.lib.value.json.engine.JsonBoolean;
import dev.zems.lib.value.json.engine.JsonNull;
import dev.zems.lib.value.json.engine.JsonNumber;
import dev.zems.lib.value.json.engine.JsonObject;
import dev.zems.lib.value.json.engine.JsonString;
import dev.zems.lib.value.json.engine.JsonValue;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("One JSON document value over an exported engine — parse, build, match, unbox and convert")
class JsonDocumentValueJourneyTest {

  @Test
  @JourneyTest(speakingId = "json-document-over-exported-engine", acceptance = "t1")
  @DisplayName(
    "parseJson answers a document of the engine family, a null root is the null marker, errors keep line and column"
  )
  void parseJsonAnswersADocumentOfTheEngineFamily() {
    String strict = "{\"a\":[1,-0.5e-3,true,\"x\\u00e9\",null],\"b\":{}}";
    Map<String, String> violations = new LinkedHashMap<>();
    violations.put(
      "{\n  \"a\": 1,\n  // note\n  \"b\": 2\n}",
      "cannot parse the JSON at line 3, column 3 ($): JSON does not allow comments; remove the comment"
    );
    violations.put(
      "{\"a\": /* c */ 1}",
      "cannot parse the JSON at line 1, column 7 ($.a): JSON does not allow comments; remove the comment"
    );
    violations.put(
      "{\n  \"servers\": [\n    \"a\",\n    \"b\",\n  ]\n}",
      "cannot parse the JSON at line 5, column 3 ($.servers[2]): the array ends with a trailing comma, which JSON does" +
        " not allow; remove the comma"
    );
    violations.put(
      "{\"a\":1,}",
      "cannot parse the JSON at line 1, column 8 ($): the object ends with a trailing comma, which JSON does not allow;" +
        " remove the comma"
    );
    violations.put(
      "{\"a\":1,\"a\":2}",
      "cannot parse the JSON at line 1, column 8 ($.a): the member name \"a\" appears twice in the same object; rename" +
        " or remove one of the two members"
    );
    violations.put(
      "[01]",
      "cannot parse the JSON at line 1, column 2 ($[0]): the number 01 has a leading zero, which JSON does not allow;" +
        " remove the zero, or write the value as a string if the zero matters"
    );
    violations.put(
      "[NaN]",
      "cannot parse the JSON at line 1, column 2 ($[0]): NaN is not a valid JSON number; use null, or a string such as" +
        " \"NaN\""
    );
    violations.put(
      "[Infinity]",
      "cannot parse the JSON at line 1, column 2 ($[0]): Infinity is not a valid JSON number; use null, or a string" +
        " such as \"Infinity\""
    );
    violations.put(
      "{\"a\":-Infinity}",
      "cannot parse the JSON at line 1, column 6 ($.a): -Infinity is not a valid JSON number; use null, or a string" +
        " such as \"-Infinity\""
    );

    Value<JsonValue> parsed = Value.parseJson(strict);
    Value<JsonValue> nullRoot = Value.parseJson("null");
    Value<JsonValue> nullText = Value.parseJson(null);

    assertThat(parsed).isInstanceOfSatisfying(JsonDocumentValue.class, document ->
      assertThat(document.root()).isInstanceOfSatisfying(JsonObject.class, root ->
        assertThat(root.asMap().keySet()).containsExactly("a", "b")
      )
    );
    assertThat(parsed.toString()).isEqualTo(strict);
    assertThat(nullRoot).isNullValue();
    assertThat(nullRoot).isEqualTo(Value.nullValue());
    violations.forEach((text, message) ->
      assertThat(Value.parseJson(text)).as(text).isEqualTo(Value.errorMessage(message, Object.class))
    );
    assertThat(nullText).isEqualTo(Value.errorMessage("cannot parse the JSON: the text is null", Object.class));
  }

  @Test
  @JourneyTest(speakingId = "json-document-over-exported-engine", acceptance = "t2")
  @DisplayName("one overloaded jsonOf builds every kind from Value<JsonValue> members and equals the same text parsed")
  void oneOverloadedJsonOfBuildsEveryKind() {
    Value<JsonValue> failed = Value.errorMessage("boom", JsonValue.class);
    Value<JsonValue> boxedEngineValue = Value.of((JsonValue) JsonString.of("x"));
    Map<String, Value<JsonValue>> nullName = new HashMap<>();
    nullName.put(null, Value.jsonOf(1));
    Map<String, Value<JsonValue>> nullMember = new HashMap<>();
    nullMember.put("a", null);

    Value<JsonValue> person = Value.jsonOf(
      Map.of(
        "name",
        Value.jsonOf("Ada"),
        "born",
        Value.jsonOf(1815),
        "tags",
        Value.jsonOf(List.of(Value.jsonOf("math"), Value.nullValue()))
      )
    );
    Value<JsonValue> refusedError = Value.jsonOf(Map.of("a", failed));

    assertThat(person).isEqualTo(Value.parseJson("{\"tags\":[\"math\",null],\"born\":1815,\"name\":\"Ada\"}"));
    assertThat(person.hashCode()).isEqualTo(
      Value.parseJson("{\"tags\":[\"math\",null],\"born\":1815,\"name\":\"Ada\"}").hashCode()
    );

    assertThat(Value.jsonOf(Map.of())).isEqualTo(Value.parseJson("{}"));
    assertThat(Value.jsonOf(List.of())).isEqualTo(Value.parseJson("[]"));
    assertThat(Value.jsonOf("Ada")).isEqualTo(Value.parseJson("\"Ada\""));
    assertThat(Value.jsonOf(1815)).isEqualTo(Value.parseJson("1815"));
    assertThat(Value.jsonOf(2.5d)).isEqualTo(Value.parseJson("2.5"));
    assertThat(Value.jsonOf(new BigInteger("123456789012345678901234567890"))).isEqualTo(
      Value.parseJson("123456789012345678901234567890")
    );
    assertThat(Value.jsonOf(new BigDecimal("1.50")).toString()).isEqualTo("1.50");
    assertThat(Value.jsonOf(new BigDecimal("1.50"))).isEqualTo(Value.parseJson("1.5"));
    assertThat(Value.jsonOf(true)).isEqualTo(Value.parseJson("true"));
    assertThat(Value.jsonOf(false)).isEqualTo(Value.parseJson("false"));

    assertThat(Value.jsonOf("1815")).isEqualTo(Value.parseJson("\"1815\""));
    assertThat(Value.jsonOf("1815")).isNotEqualTo(Value.parseJson("1815"));
    assertThat(Value.jsonOf("1815").toString()).isEqualTo("\"1815\"");
    assertThat(Value.jsonOf("{\"a\":1}").toString()).isEqualTo("\"{\\\"a\\\":1}\"");
    assertThat(Value.jsonOf('7')).isEqualTo(Value.parseJson("\"7\""));
    assertThat(Value.jsonOf('7')).isNotEqualTo(Value.parseJson("55"));
    assertThat(Value.jsonOf('x').toString()).isEqualTo("\"x\"");

    assertThat(Value.jsonOf(Map.of("z", Value.nullValue()))).isEqualTo(Value.parseJson("{\"z\":null}"));
    assertThat(Value.jsonOf(List.of(Value.nullValue())).toString()).isEqualTo("[null]");

    assertThat(refusedError).isEqualTo(
      Value.errorMessage(
        "cannot build a JSON object: member 'a' holds an ErrorValue, not a JSON value or the null marker",
        Object.class
      )
    );
    assertThat(refusedError).extractingThrowable().cause().isSameAs(failed.error().orElseThrow());
    assertThat(Value.jsonOf(Map.of("t", Value.tombstone()))).isEqualTo(
      Value.errorMessage(
        "cannot build a JSON object: member 't' holds a TombstoneValue, not a JSON value or the null marker",
        Object.class
      )
    );
    assertThat(Value.jsonOf(List.of(Value.jsonOf("math"), Value.undefined()))).isEqualTo(
      Value.errorMessage(
        "cannot build a JSON array: element 1 holds an UndefinedValue, not a JSON value or the null marker",
        Object.class
      )
    );
    assertThat(Value.jsonOf(List.of(boxedEngineValue))).isEqualTo(
      Value.errorMessage(
        "cannot build a JSON array: element 0 holds a BoxedValue, not a JSON value or the null marker",
        Object.class
      )
    );

    assertThat(Value.jsonOf((Map<String, Value<JsonValue>>) null)).isEqualTo(
      Value.errorMessage("cannot build a JSON object: the members map is null", Object.class)
    );
    assertThat(Value.jsonOf((List<Value<JsonValue>>) null)).isEqualTo(
      Value.errorMessage("cannot build a JSON array: the elements list is null", Object.class)
    );
    assertThat(Value.jsonOf(nullName)).isEqualTo(
      Value.errorMessage("cannot build a JSON object: a member name is null", Object.class)
    );
    assertThat(Value.jsonOf(nullMember)).isEqualTo(
      Value.errorMessage(
        "cannot build a JSON object: member 'a' is null; use Value.nullValue() for a JSON null",
        Object.class
      )
    );
    assertThat(Value.jsonOf(Arrays.asList(Value.jsonOf(1), null))).isEqualTo(
      Value.errorMessage(
        "cannot build a JSON array: element 1 is null; use Value.nullValue() for a JSON null",
        Object.class
      )
    );
    assertThat(Value.jsonOf((String) null)).isEqualTo(
      Value.errorMessage("cannot build a JSON string: the text is null", Object.class)
    );
    assertThat(Value.jsonOf((BigInteger) null)).isEqualTo(
      Value.errorMessage("cannot build a JSON number: the number is null", Object.class)
    );
    assertThat(Value.jsonOf((BigDecimal) null)).isEqualTo(
      Value.errorMessage("cannot build a JSON number: the number is null", Object.class)
    );
    assertThat(Value.jsonOf(Double.NaN)).isEqualTo(
      Value.errorMessage("cannot build a JSON number from NaN: JSON numbers must be finite", Object.class)
    );
  }

  @Test
  @JourneyTest(speakingId = "json-document-over-exported-engine", acceptance = "t3")
  @DisplayName(
    "JSON is one record branch of Value: record patterns reach each kind, an engine switch walks the payload"
  )
  void jsonIsOneRecordBranchReachedByRecordPatterns() {
    String text = "{\"o\":{},\"a\":[],\"s\":\"x\",\"n\":1,\"b\":true,\"z\":null}";
    String nested = "{\"a\":[1,{\"b\":[true,null,\"x\"],\"c\":{}}],\"d\":{\"e\":{\"f\":2.5}},\"g\":[]}";

    Value<JsonValue> document = Value.parseJson(text);
    Map<String, String> memberKinds = switch (document) {
      case JsonDocumentValue(JsonObject root) -> root
        .asMap()
        .entrySet()
        .stream()
        .collect(
          Collectors.toMap(
            Map.Entry::getKey,
            member -> engineKindOf(member.getValue()),
            (left, right) -> left,
            LinkedHashMap::new
          )
        );
      default -> Map.of();
    };
    int leaves = switch (Value.parseJson(nested)) {
      case JsonDocumentValue(JsonValue root) -> leavesOf(root);
      default -> -1;
    };
    Navigator nullMember = document.navigate().get("z");

    assertThat(Value.class.getPermittedSubclasses())
      .contains(JsonDocumentValue.class)
      .extracting(Class::getName)
      .doesNotContain("dev.zems.lib.value.json.JsonValue");
    assertThat(JsonDocumentValue.class.isRecord()).isTrue();
    assertThat(JsonDocumentValue.class.getRecordComponents())
      .singleElement()
      .extracting(RecordComponent::getType)
      .isEqualTo(JsonValue.class);

    assertThat(kindOf(document)).isEqualTo("object");
    assertThat(kindOf(Value.parseJson("[]"))).isEqualTo("array");
    assertThat(kindOf(Value.parseJson("\"x\""))).isEqualTo("string");
    assertThat(kindOf(Value.parseJson("1"))).isEqualTo("number");
    assertThat(kindOf(Value.parseJson("true"))).isEqualTo("boolean");
    assertThat(kindOf(Value.parseJson("null"))).isEqualTo("null");
    assertThat(kindOf(Value.parseJson("{"))).isEqualTo("broken");
    assertThat(kindOf(Value.jsonOf(List.of(Value.jsonOf(1))))).isEqualTo("array");

    assertThat(memberKinds).containsExactly(
      Map.entry("o", "object"),
      Map.entry("a", "array"),
      Map.entry("s", "string"),
      Map.entry("n", "number"),
      Map.entry("b", "boolean"),
      Map.entry("z", "JSON null")
    );
    assertThat(leaves).isEqualTo(5);
    assertThat(nullMember.value()).isNullValue();
    assertThat(nullMember.value()).isEqualTo(Value.nullValue());
  }

  @Test
  @JourneyTest(speakingId = "json-document-over-exported-engine", acceptance = "t4")
  @DisplayName("unbox answers the engine value itself; members and elements are neither copied nor wrapped")
  void unboxAnswersTheEngineValueItself() {
    String text = "{\"name\":\"zems\",\"tags\":[\"a\",{\"b\":1}]}";
    Value<JsonValue> inner = Value.parseJson("{\"x\":[1,2]}");

    Value<JsonValue> document = Value.parseJson(text);
    JsonValue payload = document.unbox();
    JsonValue tags = payload.get("tags");
    JsonNavigator navigator = ((JsonDocumentValue) document).navigate();
    Value<JsonValue> built = Value.jsonOf(Map.of("inner", inner));
    Value<JsonValue> builtArray = Value.jsonOf(List.of(inner, inner));

    assertThat(payload).isSameAs(((JsonDocumentValue) document).root());
    assertThat(document.unbox()).isSameAs(payload);
    assertThat(payload).isInstanceOf(JsonObject.class);
    assertThat(navigator.get("tags").value().unbox()).isSameAs(tags);
    assertThat(navigator.get("tags").get(1).value().unbox()).isSameAs(tags.asList().getLast());
    assertThat(navigator.get("name").value().unbox()).isSameAs(payload.asMap().get("name"));
    assertThat(built.unbox().get("inner")).isSameAs(inner.unbox());
    assertThat(builtArray.unbox().asList()).containsExactly(inner.unbox(), inner.unbox());
    assertThat(builtArray.unbox().asList().getFirst()).isSameAs(inner.unbox());
  }

  @Test
  @JourneyTest(speakingId = "json-document-over-exported-engine", acceptance = "t5")
  @DisplayName("leaves convert as String, number rows and Boolean do; an object or an array refuses a scalar target")
  void leavesConvertAndContainersRefuseScalars() {
    String text = """
    {"text": "42", "word": "hello", "flag": true, "whole": 123.0, "fraction": 345.6, "beyondInt": 2147483648, \
    "object": {"a": 1}, "array": [1, 2]}""";

    Navigator root = Value.parseJson(text).navigate();

    assertThat(root.get("text").convertTo(Integer.class)).isEqualTo(Value.of("42").convertTo(Integer.class));
    assertThat(root.get("text").convertTo(Integer.class)).isEqualTo(Value.of(42));
    assertThat(root.get("text").convertTo(String.class)).isEqualTo(Value.of("42"));
    assertThat(root.get("word").convertTo(Integer.class)).isEqualTo(Value.of("hello").convertTo(Integer.class));
    assertThat(root.get("word").convertTo(Integer.class)).isEqualTo(
      Value.errorMessage("Cannot convert String to Integer: Not a number: hello", Integer.class)
    );
    assertThat(root.get("flag").convertTo(Boolean.class)).isEqualTo(Value.of(true).convertTo(Boolean.class));
    assertThat(root.get("flag").convertTo(Boolean.class)).isEqualTo(Value.of(true));
    assertThat(root.get("flag").convertTo(String.class)).isEqualTo(Value.of(true).convertTo(String.class));
    assertThat(root.get("flag").convertTo(String.class)).isEqualTo(Value.of("true"));

    assertThat(root.get("whole").convertTo(Integer.class)).isEqualTo(Value.of(123));
    assertThat(root.get("whole").convertTo(BigDecimal.class)).isEqualTo(Value.of(new BigDecimal("123.0")));
    assertThat(root.get("fraction").convertTo(Double.class)).isEqualTo(Value.of(345.6d));
    assertThat(root.get("fraction").convertTo(Integer.class)).isEqualTo(
      Value.errorMessage("Cannot convert JsonNumber to Integer: 345.6 is not a whole number", Integer.class)
    );
    assertThat(root.get("beyondInt").convertTo(Integer.class)).isEqualTo(
      Value.errorMessage("Cannot convert JsonNumber to Integer: 2147483648 overflows Integer", Integer.class)
    );
    assertThat(root.get("beyondInt").convertTo(Long.class)).isEqualTo(Value.of(2_147_483_648L));

    assertThat(root.get("object").convertTo(Integer.class)).isEqualTo(
      Value.unsupported("Cannot convert JsonObject to Integer: no conversion exists", Integer.class)
    );
    assertThat(root.get("array").convertTo(Integer.class)).isEqualTo(
      Value.unsupported("Cannot convert JsonArray to Integer: no conversion exists", Integer.class)
    );
    assertThat(root.get("array").convertTo(String.class)).isEqualTo(
      Value.unsupported("Cannot convert JsonArray to String: no conversion exists", String.class)
    );
  }

  private static String kindOf(Value<JsonValue> value) {
    return switch (value) {
      case JsonDocumentValue(JsonObject _) -> "object";
      case JsonDocumentValue(JsonArray _) -> "array";
      case JsonDocumentValue(JsonString _) -> "string";
      case JsonDocumentValue(JsonNumber _) -> "number";
      case JsonDocumentValue(JsonBoolean _) -> "boolean";
      case NullValue<?> _ -> "null";
      default -> value.isError() ? "broken" : "other";
    };
  }

  private static String engineKindOf(JsonValue value) {
    return switch (value) {
      case JsonObject _ -> "object";
      case JsonArray _ -> "array";
      case JsonString _ -> "string";
      case JsonNumber _ -> "number";
      case JsonBoolean _ -> "boolean";
      case JsonNull _ -> "JSON null";
    };
  }

  private static int leavesOf(JsonValue value) {
    return switch (value) {
      case JsonObject object -> object.asMap().values().stream().mapToInt(JsonDocumentValueJourneyTest::leavesOf).sum();
      case JsonArray array -> array.asList().stream().mapToInt(JsonDocumentValueJourneyTest::leavesOf).sum();
      case JsonString _, JsonNumber _, JsonBoolean _, JsonNull _ -> 1;
    };
  }
}
