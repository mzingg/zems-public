package dev.zems.lib.value.json;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.zems.lib.common.junit.JourneyTest;
import dev.zems.lib.value.Navigator;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.json.engine.JsonArray;
import dev.zems.lib.value.json.engine.JsonString;
import dev.zems.lib.value.json.engine.JsonValue;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JSON documents as values — parse, navigate, convert, compare and emit through the Value API")
class JsonDocumentValuesJourneyTest {

  private static final Duration AT_ONCE = Duration.ofSeconds(1);
  private static final Duration DEEP_WALK = Duration.ofSeconds(10);
  private static final int DEPTH = 100_000;

  private static final String THREAD_DUMP = """
  {
    "threadDump": {
      "processId": "4711",
      "threadContainers": [
        {
          "container": "<root>",
          "parent": null,
          "threads": [
            {
              "tid": "1",
              "name": "main",
              "virtual": false,
              "stack": ["java.base/java.lang.Thread.sleep(Thread.java:509)"]
            },
            {
              "tid": "21",
              "name": "Reference Handler",
              "virtual": false,
              "stack": []
            }
          ],
          "threadCount": 2
        }
      ]
    }
  }
  """;

  @Test
  @JourneyTest(speakingId = "json-documents-as-values", acceptance = "v3")
  @DisplayName(
    "a JSON navigator reaches nested values and ends a failing chain in its first failure, path and location"
  )
  void jsonNavigatorReachesNestedValuesAndCarriesTheFirstFailure() {
    int containersAt = THREAD_DUMP.indexOf('[', THREAD_DUMP.indexOf("\"threadContainers\""));
    int parentAt = THREAD_DUMP.indexOf("null", THREAD_DUMP.indexOf("\"parent\""));
    int threadsAt = THREAD_DUMP.indexOf('[', THREAD_DUMP.indexOf("\"threads\""));
    int tidAt = THREAD_DUMP.indexOf("\"1\"");
    int mainThreadAt = THREAD_DUMP.lastIndexOf('{', tidAt);
    int virtualAt = THREAD_DUMP.indexOf("false");
    int emptyStackAt = THREAD_DUMP.indexOf("[]");
    int threadCountAt = THREAD_DUMP.indexOf('2', THREAD_DUMP.indexOf("\"threadCount\""));
    String withoutThreads = "{\"threadDump\":{\"threadContainers\":[{\"container\":\"<root>\"}]}}";
    Value<JsonValue> built = Value.jsonOf(Map.of("a", Value.jsonOf(List.of(Value.jsonOf(1)))));

    var document = Value.parseJson(THREAD_DUMP);
    Navigator container = document.navigate().get("threadDump").get("threadContainers").get(0);
    Navigator mainThread = container.get("threads").get(0);
    JsonNavigator typed = ((JsonDocumentValue) document).navigate().get("threadDump").get("threadContainers").get(0);
    Navigator missing = mainThread.get("state");
    var nested = container.value();

    assertThat(mainThread.get("name").value()).isEqualTo(Value.jsonOf("main"));
    assertThat(mainThread.get("stack").get(0).value()).isEqualTo(
      Value.jsonOf("java.base/java.lang.Thread.sleep(Thread.java:509)")
    );
    assertThat(((JsonArray) container.get("threads").value().unbox()).asList()).hasSize(2);
    assertThat(typed.get("threadCount").value()).isEqualTo(Value.jsonOf(2));
    assertThat(mainThread.get("tid").convertTo(Long.class)).isEqualTo(Value.of(1L));
    assertThat(container.get("threadCount").convertTo(Integer.class)).isEqualTo(Value.of(2));

    assertThat(missing.value()).isEqualTo(
      navigationError(
        "$.threadDump.threadContainers[0].threads[0]: member 'state' is missing (" +
          lineAndColumnOf(THREAD_DUMP, mainThreadAt) +
          ")"
      )
    );
    assertThat(
      Value.parseJson(withoutThreads).navigate().get("threadDump").get("threadContainers").get(0).get("threads").value()
    ).isEqualTo(
      navigationError(
        "$.threadDump.threadContainers[0]: member 'threads' is missing (" +
          lineAndColumnOf(withoutThreads, withoutThreads.indexOf("{\"container\"")) +
          ")"
      )
    );
    assertThat(document.navigate().get("threadDump").get("threadContainers").get(3).value()).isEqualTo(
      navigationError(
        "$.threadDump.threadContainers: index 3 is out of range for an array of 1 element (" +
          lineAndColumnOf(THREAD_DUMP, containersAt) +
          ")"
      )
    );
    assertThat(container.get("threads").get(2).value()).isEqualTo(
      navigationError(
        "$.threadDump.threadContainers[0].threads: index 2 is out of range for an array of 2 elements (" +
          lineAndColumnOf(THREAD_DUMP, threadsAt) +
          ")"
      )
    );
    assertThat(container.get("threads").get(1).get("stack").get(0).value()).isEqualTo(
      navigationError(
        "$.threadDump.threadContainers[0].threads[1].stack: index 0 is out of range for an empty array (" +
          lineAndColumnOf(THREAD_DUMP, emptyStackAt) +
          ")"
      )
    );
    assertThat(document.navigate().get(0).value()).isEqualTo(
      navigationError("$: cannot step to element 0 of an object (line 1, column 1)")
    );
    assertThat(document.navigate().get("threadDump").get("threadContainers").get("first").value()).isEqualTo(
      navigationError(
        "$.threadDump.threadContainers: cannot step to member 'first' of an array (" +
          lineAndColumnOf(THREAD_DUMP, containersAt) +
          ")"
      )
    );
    assertThat(mainThread.get("tid").get("value").value()).isEqualTo(
      navigationError(
        "$.threadDump.threadContainers[0].threads[0].tid: cannot step to member 'value' of a string (" +
          lineAndColumnOf(THREAD_DUMP, tidAt) +
          ")"
      )
    );
    assertThat(container.get("threadCount").get(0).value()).isEqualTo(
      navigationError(
        "$.threadDump.threadContainers[0].threadCount: cannot step to element 0 of a number (" +
          lineAndColumnOf(THREAD_DUMP, threadCountAt) +
          ")"
      )
    );
    assertThat(mainThread.get("virtual").get("x").value()).isEqualTo(
      navigationError(
        "$.threadDump.threadContainers[0].threads[0].virtual: cannot step to member 'x' of a boolean (" +
          lineAndColumnOf(THREAD_DUMP, virtualAt) +
          ")"
      )
    );
    assertThat(container.get("parent").get("name").value()).isEqualTo(
      navigationError(
        "$.threadDump.threadContainers[0].parent: cannot step to member 'name' of null (" +
          lineAndColumnOf(THREAD_DUMP, parentAt) +
          ")"
      )
    );
    assertThat(container.get("parent").get(0).value()).isEqualTo(
      navigationError(
        "$.threadDump.threadContainers[0].parent: cannot step to element 0 of null (" +
          lineAndColumnOf(THREAD_DUMP, parentAt) +
          ")"
      )
    );

    assertThat(built.navigate().get("b").value()).isEqualTo(navigationError("$: member 'b' is missing"));
    assertThat(built.navigate().get("a").get(1).value()).isEqualTo(
      navigationError("$.a: index 1 is out of range for an array of 1 element")
    );
    assertThat(built.navigate().get("a").get(0).get("x").value()).isEqualTo(
      navigationError("$.a[0]: cannot step to member 'x' of a number")
    );

    assertThat(missing.get("next").get(0).get("further").value()).isSameAs(missing.value());
    assertThat(missing.convertTo(Long.class)).isEqualTo(
      Value.errorMessage(
        "$.threadDump.threadContainers[0].threads[0]: member 'state' is missing (" +
          lineAndColumnOf(THREAD_DUMP, mainThreadAt) +
          ")",
        Long.class
      )
    );
    assertThat(container.get("parent").value()).isNullValue();
    assertThat(container.get("parent").convertTo(String.class)).isNullValue();
    assertThat(nested.navigate().get("threads").get(0).get("state").value()).isEqualTo(
      navigationError("$.threads[0]: member 'state' is missing (" + lineAndColumnOf(THREAD_DUMP, mainThreadAt) + ")")
    );
  }

  @Test
  @JourneyTest(speakingId = "json-documents-as-values", acceptance = "v4")
  @DisplayName("every other value answers an identity navigator, and a state marker passes every step and exit")
  void everyOtherValueAnswersTheIdentityNavigator() {
    String brokenMessage =
      "cannot parse the JSON at line 1, column 4 ($[1]): the text ends before the array that starts at line 1, column" +
      " 1 is closed; add the closing ']'";
    Value<URI> url = Value.urlOf("https://zems.dev");
    Value<String> nothing = Value.nullValue();
    Value<String> notYetDefined = Value.undefined();
    Value<Integer> failed = Value.errorMessage("boom", Integer.class);

    Navigator host = url.navigate().get("host");
    var broken = Value.parseJson("[1,");

    assertThat(url.navigate().value()).isSameAs(url);
    assertThat(url.navigate().convertTo(String.class)).isEqualTo(Value.of("https://zems.dev"));
    assertThat(host.value()).isEqualTo(Value.unsupported("UrlValue is not navigable", Object.class));
    assertThat(url.navigate().get(0).value()).isEqualTo(Value.unsupported("UrlValue is not navigable", Object.class));
    assertThat(Value.of(42).navigate().get("a").value()).isEqualTo(
      Value.unsupported("IntegerValue is not navigable", Object.class)
    );
    assertThat(host.get("port").get(1).value()).isSameAs(host.value());
    assertThat(host.convertTo(String.class)).isEqualTo(Value.unsupported("UrlValue is not navigable", String.class));

    assertThat(broken).isEqualTo(Value.errorMessage(brokenMessage, Object.class));
    assertThat(broken.navigate().value()).isSameAs(broken);
    assertThat(broken.navigate().get(0).get("a").value()).isSameAs(broken);
    assertThat(broken.navigate().get("a").convertTo(Long.class)).isEqualTo(
      Value.errorMessage(brokenMessage, Long.class)
    );
    assertThat(failed.navigate().get("a").get(0).value()).isSameAs(failed);
    assertThat(failed.navigate().convertTo(Long.class)).isEqualTo(Value.errorMessage("boom", Long.class));
    assertThat(failed.navigate().convertTo(Long.class).error()).containsSame(failed.error().orElseThrow());

    assertThat(nothing.navigate().get("a").get(0).value()).isSameAs(nothing);
    assertThat(nothing.navigate().convertTo(Integer.class)).isNullValue();
    assertThat(Value.parseJson("null").navigate().get("a").value()).isNullValue();
    assertThat(notYetDefined.navigate().get("a").value()).isSameAs(notYetDefined);
    assertThat(notYetDefined.navigate().convertTo(Integer.class)).isUndefined();
  }

  @Test
  @JourneyTest(speakingId = "json-documents-as-values", acceptance = "v5")
  @DisplayName("strings, numbers and booleans read through convertTo, decided on the number text, refusals framed")
  void leavesReadThroughTheConversionApi() {
    String text = """
    {"text": "42", "word": "hello", "flag": true, "whole": 123.0, "exponent": 1.23e2, "fraction": 345.6, \
    "beyondInt": 2147483648, "pi": 3.141592653589793238462643383279, "huge": 1e999999999999, \
    "beyondDouble": 1e999, "tiny": 1e-400}""";

    Navigator root = Value.parseJson(text).navigate();

    assertThat(root.get("text").convertTo(Integer.class)).isEqualTo(Value.of(42));
    assertThat(root.get("text").convertTo(Integer.class)).isEqualTo(Value.of("42").convertTo(Integer.class));
    assertThat(root.get("text").convertTo(String.class).unbox()).isEqualTo("42");
    assertThat(root.get("word").convertTo(Integer.class)).isEqualTo(
      Value.errorMessage("Cannot convert String to Integer: Not a number: hello", Integer.class)
    );
    assertThat(root.get("flag").convertTo(String.class)).isEqualTo(Value.of("true"));
    assertThat(root.get("flag").convertTo(Boolean.class).unbox()).isTrue();

    assertThat(root.get("whole").convertTo(Integer.class)).isEqualTo(Value.of(123));
    assertThat(root.get("whole").convertTo(Long.class)).isEqualTo(Value.of(123L));
    assertThat(root.get("whole").convertTo(BigInteger.class)).isEqualTo(Value.of(BigInteger.valueOf(123)));
    assertThat(root.get("whole").convertTo(BigDecimal.class)).isEqualTo(Value.of(new BigDecimal("123.0")));
    assertThat(root.get("whole").convertTo(Double.class)).isEqualTo(Value.of(123.0d));
    assertThat(root.get("exponent").convertTo(Integer.class)).isEqualTo(Value.of(123));
    assertThat(root.get("exponent").convertTo(BigDecimal.class)).isEqualTo(Value.of(new BigDecimal("1.23e2")));

    assertThat(root.get("fraction").convertTo(Integer.class)).isEqualTo(
      refusal("Integer", "345.6 is not a whole number", Integer.class)
    );
    assertThat(root.get("fraction").convertTo(Long.class)).isEqualTo(
      refusal("Long", "345.6 is not a whole number", Long.class)
    );
    assertThat(root.get("fraction").convertTo(BigInteger.class)).isEqualTo(
      refusal("BigInteger", "345.6 is not a whole number", BigInteger.class)
    );
    assertThat(root.get("fraction").convertTo(BigDecimal.class)).isEqualTo(Value.of(new BigDecimal("345.6")));
    assertThat(root.get("fraction").convertTo(Double.class)).isEqualTo(Value.of(345.6d));
    assertThat(root.get("beyondInt").convertTo(Integer.class)).isEqualTo(
      refusal("Integer", "2147483648 overflows Integer", Integer.class)
    );
    assertThat(root.get("beyondInt").convertTo(Long.class)).isEqualTo(Value.of(2_147_483_648L));

    assertThat(root.get("pi").convertTo(Double.class)).isEqualTo(Value.of(3.141592653589793d));
    assertThat(root.get("pi").convertTo(BigDecimal.class)).isEqualTo(
      Value.of(new BigDecimal("3.141592653589793238462643383279"))
    );
    assertThat(root.get("beyondDouble").convertTo(Double.class)).isEqualTo(
      refusal("Double", "1e999 overflows Double", Double.class)
    );
    assertThat(root.get("tiny").convertTo(Double.class)).isEqualTo(
      refusal("Double", "1e-400 underflows Double", Double.class)
    );

    assertTimeoutPreemptively(AT_ONCE, () ->
      assertThat(root.get("huge").convertTo(Integer.class)).isEqualTo(
        refusal("Integer", "1e999999999999 overflows Integer", Integer.class)
      )
    );
    assertTimeoutPreemptively(AT_ONCE, () ->
      assertThat(root.get("huge").convertTo(Long.class)).isEqualTo(
        refusal("Long", "1e999999999999 overflows Long", Long.class)
      )
    );
    assertTimeoutPreemptively(AT_ONCE, () ->
      assertThat(root.get("huge").convertTo(BigInteger.class)).isEqualTo(
        refusal("BigInteger", "1e999999999999 has more than 1000 digits", BigInteger.class)
      )
    );
    assertTimeoutPreemptively(AT_ONCE, () ->
      assertThat(root.get("huge").convertTo(BigDecimal.class)).isEqualTo(
        refusal("BigDecimal", "1e999999999999 is outside the BigDecimal range", BigDecimal.class)
      )
    );
    assertTimeoutPreemptively(AT_ONCE, () ->
      assertThat(root.get("huge").convertTo(Double.class)).isEqualTo(
        refusal("Double", "1e999999999999 overflows Double", Double.class)
      )
    );
  }

  @Test
  @JourneyTest(speakingId = "json-documents-as-values", acceptance = "v6")
  @DisplayName("numbers compare numerically and keep their text, objects ignore member order, arrays do not")
  void equalityIsByValue() {
    var one = Value.parseJson("1");
    var oneDotZero = Value.parseJson("1.0");
    var hundred = Value.parseJson("100");
    var hundredAsExponent = Value.parseJson("1e2");
    var zero = Value.parseJson("0");
    var negativeZero = Value.parseJson("-0");
    var ordered = Value.parseJson("{\"a\":1,\"b\":2}");
    var reordered = Value.parseJson("{\"b\":2,\"a\":1.0}");
    var parsed = Value.parseJson(
      "{\"name\":\"zems\",\"tags\":[\"a\",\"b\"],\"version\":2,\"stable\":true,\"parent\":null}"
    );
    Map<String, Value<JsonValue>> members = new LinkedHashMap<>();
    members.put("parent", Value.nullValue());
    members.put("stable", Value.jsonOf(true));
    members.put("version", Value.jsonOf(new BigDecimal("2.00")));
    members.put("tags", Value.jsonOf(List.of(Value.jsonOf("a"), Value.jsonOf("b"))));
    members.put("name", Value.jsonOf("zems"));
    Value<JsonValue> built = Value.jsonOf(members);

    assertThat(oneDotZero).isEqualTo(one);
    assertThat(oneDotZero.hashCode()).isEqualTo(one.hashCode());
    assertThat(oneDotZero.toString()).isEqualTo("1.0");
    assertThat(one.toString()).isEqualTo("1");
    assertThat(hundredAsExponent).isEqualTo(hundred);
    assertThat(hundredAsExponent.hashCode()).isEqualTo(hundred.hashCode());
    assertThat(hundredAsExponent.toString()).isEqualTo("1e2");
    assertThat(negativeZero).isEqualTo(zero);
    assertThat(negativeZero.hashCode()).isEqualTo(zero.hashCode());
    assertThat(negativeZero.toString()).isEqualTo("-0");
    assertThat(Value.jsonOf(1)).isEqualTo(one);
    assertThat(Value.jsonOf(1.0d)).isEqualTo(one);
    assertThat(one).isNotEqualTo(Value.parseJson("2"));
    assertThat(one).isNotEqualTo(Value.parseJson("\"1\""));

    assertThat(reordered).isEqualTo(ordered);
    assertThat(reordered.hashCode()).isEqualTo(ordered.hashCode());
    assertThat(ordered.toString()).isEqualTo("{\"a\":1,\"b\":2}");
    assertThat(reordered.toString()).isEqualTo("{\"b\":2,\"a\":1.0}");
    assertThat(ordered).isNotEqualTo(Value.parseJson("{\"a\":1}"));
    assertThat(ordered).isNotEqualTo(Value.parseJson("{\"a\":1,\"c\":2}"));

    assertThat(Value.parseJson("[1,2]")).isEqualTo(Value.parseJson("[1.0,2e0]"));
    assertThat(Value.parseJson("[1,2]").hashCode()).isEqualTo(Value.parseJson("[1.0,2e0]").hashCode());
    assertThat(Value.parseJson("[1,2]")).isNotEqualTo(Value.parseJson("[2,1]"));
    assertThat(Value.parseJson("[1]")).isNotEqualTo(Value.parseJson("[1,1]"));

    assertThat(parsed).isEqualTo(built);
    assertThat(built).isEqualTo(parsed);
    assertThat(parsed.hashCode()).isEqualTo(built.hashCode());
  }

  @Test
  @JourneyTest(speakingId = "json-documents-as-values", acceptance = "v7")
  @DisplayName("built and parsed documents emit compact and indented JSON in member order, and both forms parse back")
  void documentsEmitCompactAndIndentedAndParseBack() {
    Map<String, Value<JsonValue>> members = new LinkedHashMap<>();
    members.put("name", Value.jsonOf("say \"hi\"\n"));
    members.put("count", Value.jsonOf(42L));
    members.put("ratio", Value.jsonOf(0.25d));
    members.put("price", Value.jsonOf(new BigDecimal("1.50")));
    members.put("big", Value.jsonOf(new BigInteger("123456789012345678901234567890")));
    members.put("active", Value.jsonOf(true));
    members.put("owner", Value.nullValue());
    members.put("tags", Value.jsonOf(List.of(Value.jsonOf("a"), Value.jsonOf(List.of()), Value.jsonOf(Map.of()))));
    members.put("nested", Value.jsonOf(Map.of("deep", Value.jsonOf(false))));
    String builtCompact = """
    {"name":"say \\"hi\\"\\n","count":42,"ratio":0.25,"price":1.50,"big":123456789012345678901234567890,\
    "active":true,"owner":null,"tags":["a",[],{}],"nested":{"deep":false}}""";
    String builtIndented = """
    {
      "name": "say \\"hi\\"\\n",
      "count": 42,
      "ratio": 0.25,
      "price": 1.50,
      "big": 123456789012345678901234567890,
      "active": true,
      "owner": null,
      "tags": [
        "a",
        [],
        {}
      ],
      "nested": {
        "deep": false
      }
    }""";
    String spacedText = """
     {
    \t"b" : [ 1E+2 ,\t-0.0 ] ,\r
      "a":"caf\\u00e9 \\/" ,
    \t"c" : { "z" : null , "y" : true } ,
      "d" : [ ] }
    """;
    String spacedCompact = """
    {"b":[1E+2,-0.0],"a":"caf\\u00e9 \\/","c":{"z":null,"y":true},"d":[]}""";
    String spacedIndented = """
    {
      "b": [
        1E+2,
        -0.0
      ],
      "a": "caf\\u00e9 \\/",
      "c": {
        "z": null,
        "y": true
      },
      "d": []
    }""";
    List<String> unorderedKeys = List.of("zulu", "alpha", "yankee", "bravo", "xray", "charlie", "whiskey", "delta");
    Map<String, Value<JsonValue>> manyMembers = new LinkedHashMap<>();
    unorderedKeys.forEach(key -> manyMembers.put(key, Value.jsonOf(true)));
    String manyText = unorderedKeys
      .stream()
      .map(key -> "\"" + key + "\":true")
      .collect(Collectors.joining(",", "{", "}"));
    Map<String, Value<JsonValue>> nullName = new HashMap<>();
    nullName.put(null, Value.jsonOf(1));
    Map<String, Value<JsonValue>> nullMember = new HashMap<>();
    nullMember.put("a", null);

    Value<JsonValue> built = Value.jsonOf(members);
    var spaced = Value.parseJson(spacedText);
    Value<JsonValue> many = Value.jsonOf(manyMembers);

    assertThat(built.toString()).isEqualTo(builtCompact);
    assertThat(JsonText.compact(built)).isEqualTo(Value.of(builtCompact));
    assertThat(JsonText.indented(built, "  ")).isEqualTo(Value.of(builtIndented));
    assertThat(Value.parseJson(builtCompact)).isEqualTo(built);
    assertThat(Value.parseJson(builtCompact).toString()).isEqualTo(builtCompact);
    assertThat(Value.parseJson(JsonText.indented(built, "  ").unbox())).isEqualTo(built);
    assertThat(Value.parseJson(JsonText.indented(built, "  ").unbox()).toString()).isEqualTo(builtCompact);

    assertThat(spaced.toString()).isEqualTo(spacedCompact);
    assertThat(JsonText.compact(spaced)).isEqualTo(Value.of(spacedCompact));
    assertThat(JsonText.indented(spaced, "  ")).isEqualTo(Value.of(spacedIndented));
    assertThat(Value.parseJson(spacedCompact)).isEqualTo(spaced);
    assertThat(Value.parseJson(spacedIndented)).isEqualTo(spaced);
    assertThat(Value.parseJson(spacedIndented).toString()).isEqualTo(spacedCompact);

    assertThat(many.toString()).isEqualTo(manyText);
    assertThat(Value.parseJson(manyText).toString()).isEqualTo(manyText);
    assertThat(Value.parseJson(JsonText.indented(many, "\t").unbox()).toString()).isEqualTo(manyText);

    assertThat(Value.jsonOf(Map.of("a", Value.of((JsonValue) JsonString.of("x"))))).isEqualTo(
      Value.errorMessage(
        "cannot build a JSON object: member 'a' holds a BoxedValue, not a JSON value or the null marker",
        Object.class
      )
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
    assertThat(Value.jsonOf((Map<String, Value<JsonValue>>) null)).isEqualTo(
      Value.errorMessage("cannot build a JSON object: the members map is null", Object.class)
    );
    assertThat(Value.jsonOf(List.of(Value.jsonOf(1), Value.tombstone()))).isEqualTo(
      Value.errorMessage(
        "cannot build a JSON array: element 1 holds a TombstoneValue, not a JSON value or the null marker",
        Object.class
      )
    );
    assertThat(Value.jsonOf(Arrays.asList(Value.jsonOf(1), null))).isEqualTo(
      Value.errorMessage(
        "cannot build a JSON array: element 1 is null; use Value.nullValue() for a JSON null",
        Object.class
      )
    );
    assertThat(Value.jsonOf((List<Value<JsonValue>>) null)).isEqualTo(
      Value.errorMessage("cannot build a JSON array: the elements list is null", Object.class)
    );
  }

  @Test
  @JourneyTest(speakingId = "json-documents-as-values", acceptance = "v8")
  @DisplayName(
    "documents nested 100 000 levels deep parse, navigate, compare, hash and emit without a StackOverflowError"
  )
  void pathologicalNestingNeverOverflowsTheStack() {
    String nestedArrays = "[".repeat(DEPTH) + "]".repeat(DEPTH);
    String nestedArraysIndented = "[\n".repeat(DEPTH - 1) + "[]" + "\n]".repeat(DEPTH - 1);
    String innermostOne = "[".repeat(DEPTH - 1) + "[1]" + "]".repeat(DEPTH - 1);
    String nestedObjects = "{\"a\":".repeat(DEPTH) + "null" + "}".repeat(DEPTH);
    String nestedObjectsIndented = "{\n" + "\"a\": {\n".repeat(DEPTH - 1) + "\"a\": null" + "\n}".repeat(DEPTH);
    String elidedArrayPath = "$" + "[0]".repeat(32) + "…" + "[0]".repeat(32);
    String elidedObjectPath = "$" + ".a".repeat(32) + "…" + ".a".repeat(32);

    var parsedArrays = Value.parseJson(nestedArrays);
    Value<JsonValue> builtArrays = Value.jsonOf(List.of());
    for (int level = 1; level < DEPTH; level++) {
      builtArrays = Value.jsonOf(List.of(builtArrays));
    }
    var parsedObjects = Value.parseJson(nestedObjects);
    Value<JsonValue> builtObjects = Value.jsonOf(Map.of("a", Value.nullValue()));
    for (int level = 1; level < DEPTH; level++) {
      builtObjects = Value.jsonOf(Map.of("a", builtObjects));
    }
    var unclosed = Value.parseJson("[".repeat(DEPTH));

    assertThat(parsedArrays).isEqualTo(builtArrays);
    assertThat(parsedArrays.hashCode()).isEqualTo(builtArrays.hashCode());
    assertThat(parsedArrays).isNotEqualTo(Value.parseJson(innermostOne));
    assertThat(parsedArrays.toString()).isEqualTo(nestedArrays);
    assertThat(builtArrays.toString()).isEqualTo(nestedArrays);
    assertThat(JsonText.compact(builtArrays)).isEqualTo(Value.of(nestedArrays));
    assertThat(JsonText.indented(parsedArrays, "")).isEqualTo(Value.of(nestedArraysIndented));
    assertThat(JsonText.indented(builtArrays, "")).isEqualTo(Value.of(nestedArraysIndented));
    assertThat(parsedObjects).isEqualTo(builtObjects);
    assertThat(parsedObjects.hashCode()).isEqualTo(builtObjects.hashCode());
    assertThat(parsedObjects.toString()).isEqualTo(nestedObjects);
    assertThat(builtObjects.toString()).isEqualTo(nestedObjects);
    assertThat(JsonText.indented(parsedObjects, "")).isEqualTo(Value.of(nestedObjectsIndented));
    assertThat(unclosed)
      .extractingThrowable()
      .hasMessageStartingWith("cannot parse the JSON at line 1, column 100001 (")
      .hasMessageEndingWith(
        "the text ends before the array that starts at line 1, column 100000 is closed; add the closing ']'"
      );

    Value<JsonValue> builtArraysRoot = builtArrays;
    assertTimeoutPreemptively(DEEP_WALK, () -> {
      Navigator parsedStep = parsedArrays.navigate();
      Navigator builtStep = builtArraysRoot.navigate();
      for (int level = 1; level < DEPTH; level++) {
        parsedStep = parsedStep.get(0);
        builtStep = builtStep.get(0);
      }
      assertThat(parsedStep.get(0).value()).isEqualTo(
        navigationError(elidedArrayPath + ": index 0 is out of range for an empty array (line 1, column 100000)")
      );
      assertThat(builtStep.get(0).value()).isEqualTo(
        navigationError(elidedArrayPath + ": index 0 is out of range for an empty array")
      );
    });
    assertTimeoutPreemptively(DEEP_WALK, () -> {
      Navigator step = parsedObjects.navigate();
      for (int level = 0; level < DEPTH; level++) {
        step = step.get("a");
      }
      assertThat(step.value()).isNullValue();
      assertThat(step.get("a").value()).isEqualTo(
        navigationError(elidedObjectPath + ": cannot step to member 'a' of null (line 1, column 500001)")
      );
    });
  }

  private static Value<Object> navigationError(String message) {
    return Value.errorMessage(message, Object.class);
  }

  private static <T> Value<T> refusal(String target, String reason, Class<T> targetType) {
    return Value.errorMessage("Cannot convert JsonNumber to " + target + ": " + reason, targetType);
  }

  private static String lineAndColumnOf(String text, int index) {
    String before = text.substring(0, index);
    long line =
      before
        .chars()
        .filter(character -> character == '\n')
        .count() + 1;
    int column = index - before.lastIndexOf('\n');
    return "line " + line + ", column " + column;
  }
}
