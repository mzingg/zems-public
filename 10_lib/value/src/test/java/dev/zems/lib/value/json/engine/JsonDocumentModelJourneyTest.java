package dev.zems.lib.value.json.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.JourneyTest;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("JSON document model — parse, navigate, convert, build and emit like the JDK's Simple JSON API")
class JsonDocumentModelJourneyTest {

  private static final String THREAD_DUMP = """
  {
    "threadDump": {
      "processId": "4711",
      "time": "2026-10-22T10:15:30Z",
      "runtimeVersion": "25.0.4.1+1",
      "threadContainers": [
        {
          "container": "<root>",
          "parent": null,
          "owner": null,
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

  static Stream<RejectedInput> rejectedInputs() {
    return Stream.of(
      new RejectedInput(
        "comment",
        "{\"a\":1} // note",
        "cannot parse the JSON at line 1, column 9 ($): JSON does not allow comments; remove the comment",
        0,
        8
      ),
      new RejectedInput(
        "trailing comma in an array",
        "[1,2,]",
        "cannot parse the JSON at line 1, column 6 ($[2]): the array ends with a trailing comma, which JSON does not allow;" +
          " remove the comma",
        0,
        5
      ),
      new RejectedInput(
        "trailing comma in an object",
        "{\"a\":1,}",
        "cannot parse the JSON at line 1, column 8 ($): the object ends with a trailing comma, which JSON does not allow;" +
          " remove the comma",
        0,
        7
      ),
      new RejectedInput(
        "duplicate member name",
        "{\n  \"providers\": [],\n  \"providers\": []\n}",
        "cannot parse the JSON at line 3, column 3 ($.providers): the member name \"providers\" appears twice in the same" +
          " object; rename or remove one of the two members",
        2,
        2
      ),
      new RejectedInput(
        "leading zero",
        "[01]",
        "cannot parse the JSON at line 1, column 2 ($[0]): the number 01 has a leading zero, which JSON does not allow;" +
          " remove the zero, or write the value as a string if the zero matters",
        0,
        1
      ),
      new RejectedInput(
        "NaN",
        "{\"x\": NaN}",
        "cannot parse the JSON at line 1, column 7 ($.x): NaN is not a valid JSON number; use null, or a string such as" +
          " \"NaN\"",
        0,
        6
      ),
      new RejectedInput(
        "-Infinity",
        "[-Infinity]",
        "cannot parse the JSON at line 1, column 2 ($[0]): -Infinity is not a valid JSON number; use null, or a string" +
          " such as \"-Infinity\"",
        0,
        1
      )
    );
  }

  @Test
  @JourneyTest(speakingId = "json-value-document-model", acceptance = "j1")
  @DisplayName("strict RFC 8259 documents parse: any root value, JSON whitespace, every escape, every number form")
  void strictRfc8259DocumentsParse() {
    String escapes = "\"\\u00e9\\u00C9 é \\n \\\" \\\\ \\/ \\b\\f\\r\\t\"";
    String nested = "{\"a\":{\"b\":[1,{\"c\":null}],\"d\":\"e\"}}";

    JsonValue string = Json.parse("\"x\"");
    JsonValue number = Json.parse("42");
    JsonValue bool = Json.parse("true");
    JsonValue nullValue = Json.parse("null");
    JsonValue emptyObject = Json.parse("{}");
    JsonValue emptyArray = Json.parse("[]");
    JsonValue spaced = Json.parse("\t\r\n {\r\n\t\"a\" :\t[ 1 ,\n 2 ]\r\n}\n\t");
    JsonValue escaped = Json.parse(escapes);
    JsonValue negativeZero = Json.parse("-0");
    JsonValue upperExponent = Json.parse("1E+2");
    JsonValue fractionWithExponent = Json.parse("0.5e-3");
    JsonValue document = Json.parse(nested);

    assertThat(string.asString()).isEqualTo("x");
    assertThat(number.asInt()).isEqualTo(42);
    assertThat(bool.asBoolean()).isTrue();
    assertThat(nullValue).isInstanceOf(JsonNull.class);
    assertThat(nullValue.tryValue()).isEmpty();
    assertThat(emptyObject.asMap()).isEmpty();
    assertThat(emptyObject.toString()).isEqualTo("{}");
    assertThat(emptyArray.asList()).isEmpty();
    assertThat(emptyArray.toString()).isEqualTo("[]");
    assertThat(spaced.toString()).isEqualTo("{\"a\":[1,2]}");
    assertThat(escaped.asString()).isEqualTo("éÉ é \n \" \\ / \b\f\r\t");
    assertThat(escaped.toString()).isEqualTo(escapes);
    assertThat(negativeZero.asInt()).isZero();
    assertThat(Double.toString(negativeZero.asDouble())).isEqualTo("-0.0");
    assertThat(negativeZero.toString()).isEqualTo("-0");
    assertThat(upperExponent.asInt()).isEqualTo(100);
    assertThat(upperExponent.toString()).isEqualTo("1E+2");
    assertThat(fractionWithExponent.asDouble()).isEqualTo(0.0005);
    assertThat(fractionWithExponent.toString()).isEqualTo("0.5e-3");
    assertThat(document.toString()).isEqualTo(nested);
    assertThat(document.get("a").get("b").get(1).get("c")).isInstanceOf(JsonNull.class);
    assertThat(document.get("a").get("d").asString()).isEqualTo("e");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rejectedInputs")
  @JourneyTest(speakingId = "json-value-document-model", acceptance = "j1")
  @DisplayName("non-strict input is rejected with a message carrying line, column and path, and zero-based getters")
  void nonStrictInputIsRejectedWithLocatedMessage(RejectedInput input) {
    assertThatThrownBy(() -> Json.parse(input.text())).isInstanceOfSatisfying(JsonParseException.class, error -> {
      assertThat(error).hasMessage(input.message());
      assertThat(error.getErrorLine()).isEqualTo(input.line());
      assertThat(error.getErrorPosition()).isEqualTo(input.position());
    });
  }

  @Test
  @JourneyTest(speakingId = "json-value-document-model", acceptance = "j2")
  @DisplayName("a parsed thread dump navigates through get, tryGet and tryValue and converts its leaves")
  void navigatesParsedDocument() {
    JsonValue root = Json.parse(THREAD_DUMP);

    JsonValue dump = root.get("threadDump");
    JsonValue container = dump.get("threadContainers").get(0);
    JsonValue mainThread = container.get("threads").get(0);

    assertThat(dump.asMap().keySet()).containsExactly("processId", "time", "runtimeVersion", "threadContainers");
    assertThat(container.get("threads").asList()).hasSize(2);
    assertThat(container.get("threadCount").asLong()).isEqualTo(2L);
    assertThat(container.get("parent").tryValue()).isEmpty();
    assertThat(container.get("container").tryValue()).hasValueSatisfying(name ->
      assertThat(name.asString()).isEqualTo("<root>")
    );
    assertThat(mainThread.get("name").asString()).isEqualTo("main");
    assertThat(mainThread.get("virtual").asBoolean()).isFalse();
    assertThat(mainThread.get("stack").get(0).asString()).isEqualTo(
      "java.base/java.lang.Thread.sleep(Thread.java:509)"
    );
    assertThat(mainThread.tryGet("state")).isEmpty();
    assertThat(mainThread.tryGet("tid")).hasValueSatisfying(tid -> assertThat(tid.asString()).isEqualTo("1"));
    assertThat(
      root.get("threadDump").get("threadContainers").get(0).get("threads").get(1).get("name").asString()
    ).isEqualTo("Reference Handler");
  }

  @Test
  @JourneyTest(speakingId = "json-value-document-model", acceptance = "j2")
  @DisplayName("a wrong-type, missing-member or out-of-bounds access names the path from the root and its location")
  void accessErrorsCarryThePathFromTheRoot() {
    JsonValue root = Json.parse(THREAD_DUMP);
    JsonValue mainThread = root.get("threadDump").get("threadContainers").get(0).get("threads").get(0);
    int tidValueAt = THREAD_DUMP.indexOf("\"tid\": \"1\"") + "\"tid\": ".length();
    int mainThreadAt = THREAD_DUMP.lastIndexOf('{', tidValueAt);
    int threadContainersAt = THREAD_DUMP.indexOf('[', THREAD_DUMP.indexOf("\"threadContainers\""));

    assertThatThrownBy(() ->
      root.get("threadDump").get("threadContainers").get(0).get("threads").get(0).get("tid").asLong()
    )
      .isInstanceOf(JsonValueException.class)
      .hasMessage(
        "cannot read $.threadDump.threadContainers[0].threads[0].tid (" +
          lineAndColumnOf(THREAD_DUMP, tidValueAt) +
          ") as a long: it is a string; read a string with asString()"
      );
    assertThatThrownBy(() -> mainThread.get("state"))
      .isInstanceOf(JsonValueException.class)
      .hasMessage(
        "cannot read member \"state\" of $.threadDump.threadContainers[0].threads[0] (" +
          lineAndColumnOf(THREAD_DUMP, mainThreadAt) +
          "): the object has no member \"state\"; use tryGet(\"state\") when the member may be absent"
      );
    assertThatThrownBy(() -> root.get("threadDump").get("threadContainers").get(3))
      .isInstanceOf(JsonValueException.class)
      .hasMessage(
        "cannot read element 3 of $.threadDump.threadContainers (" +
          lineAndColumnOf(THREAD_DUMP, threadContainersAt) +
          "): the array has 1 element (index 0); check asList().size() first"
      );
    assertThatThrownBy(() -> root.get(0))
      .isInstanceOf(JsonValueException.class)
      .hasMessage(
        "cannot read element 0 of $ (line 1, column 1): it is an object; read an object with get(String)," +
          " tryGet(String) or asMap()"
      );
    assertThatThrownBy(() -> JsonString.of("x").asInt())
      .isInstanceOf(JsonValueException.class)
      .hasMessage("cannot read the value as an int: it is a string; read a string with asString()");
  }

  @Test
  @JourneyTest(speakingId = "json-value-document-model", acceptance = "j3")
  @DisplayName("asInt and asLong accept only exact values, asDouble refuses infinity, the text form is lossless")
  void numbersConvertExactly() {
    String text = """
    {"whole": 123.0, "exponent": 1.23e2, "fraction": 345.6, "beyondInt": 2147483648, \
    "pi": 3.141592653589793238462643383279, "beyondDouble": 1.8E309}""";

    JsonValue numbers = Json.parse(text);

    assertThat(numbers.get("whole").asInt()).isEqualTo(123);
    assertThat(numbers.get("whole").asLong()).isEqualTo(123L);
    assertThat(numbers.get("exponent").asInt()).isEqualTo(123);
    assertThatThrownBy(() -> numbers.get("fraction").asInt())
      .isInstanceOf(JsonValueException.class)
      .hasMessage(
        "cannot read $.fraction (" +
          lineAndColumnOf(text, text.indexOf("345.6")) +
          ") as an int: 345.6 is not a whole number; use asDouble(), or new BigDecimal(toString()) for the exact value"
      );
    assertThatThrownBy(() -> numbers.get("fraction").asLong())
      .isInstanceOf(JsonValueException.class)
      .hasMessage(
        "cannot read $.fraction (" +
          lineAndColumnOf(text, text.indexOf("345.6")) +
          ") as a long: 345.6 is not a whole number; use asDouble(), or new BigDecimal(toString()) for the exact value"
      );
    assertThat(numbers.get("fraction").asDouble()).isEqualTo(345.6);
    assertThatThrownBy(() -> numbers.get("beyondInt").asInt())
      .isInstanceOf(JsonValueException.class)
      .hasMessage(
        "cannot read $.beyondInt (" +
          lineAndColumnOf(text, text.indexOf("2147483648")) +
          ") as an int: 2147483648 is outside the int range; use asLong(), or new BigDecimal(toString()) for the exact" +
          " value"
      );
    assertThat(numbers.get("beyondInt").asLong()).isEqualTo(2_147_483_648L);
    assertThat(numbers.get("pi").asDouble()).isEqualTo(3.141592653589793);
    assertThat(numbers.get("pi").toString()).isEqualTo("3.141592653589793238462643383279");
    assertThat(new BigDecimal(numbers.get("pi").toString())).isEqualTo(
      new BigDecimal("3.141592653589793238462643383279")
    );
    assertThatThrownBy(() -> numbers.get("beyondDouble").asDouble())
      .isInstanceOf(JsonValueException.class)
      .hasMessage(
        "cannot read $.beyondDouble (" +
          lineAndColumnOf(text, text.indexOf("1.8E309")) +
          ") as a double: 1.8E309 is outside the finite double range; use new BigDecimal(toString()) for the exact" +
          " value"
      );
    assertThat(new BigDecimal(numbers.get("beyondDouble").toString())).isEqualTo(new BigDecimal("1.8E309"));
  }

  @Test
  @JourneyTest(speakingId = "json-value-document-model", acceptance = "j4")
  @DisplayName("built and parsed documents emit compact and indented JSON that parses back with the same member order")
  void builtAndParsedDocumentsEmitTheSameJson() {
    var members = new LinkedHashMap<String, JsonValue>();
    members.put("text", JsonString.of("say \"hi\"\n"));
    members.put("int", JsonNumber.of(42));
    members.put("long", JsonNumber.of(9_007_199_254_740_993L));
    members.put("double", JsonNumber.of(0.25));
    members.put("decimal", JsonNumber.of("1.50"));
    members.put("yes", JsonBoolean.of(true));
    members.put("no", JsonBoolean.of(false));
    members.put("nothing", JsonNull.of());
    members.put("list", JsonArray.of(List.of(JsonString.of("a"), JsonArray.of(List.of()), JsonObject.of(Map.of()))));
    members.put("object", JsonObject.of(new LinkedHashMap<>()));
    String builtCompact = """
    {"text":"say \\"hi\\"\\n","int":42,"long":9007199254740993,"double":0.25,"decimal":1.50,\
    "yes":true,"no":false,"nothing":null,"list":["a",[],{}],"object":{}}""";
    String builtDisplay = """
    {
      "text": "say \\"hi\\"\\n",
      "int": 42,
      "long": 9007199254740993,
      "double": 0.25,
      "decimal": 1.50,
      "yes": true,
      "no": false,
      "nothing": null,
      "list": [
        "a",
        [],
        {}
      ],
      "object": {}
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
    String spacedDisplay = """
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
    List<String> unorderedKeys = List.of(
      "zulu",
      "alpha",
      "yankee",
      "bravo",
      "xray",
      "charlie",
      "whiskey",
      "delta",
      "victor",
      "echo",
      "uniform",
      "foxtrot",
      "tango",
      "golf",
      "sierra",
      "hotel",
      "romeo",
      "india",
      "quebec",
      "juliett"
    );
    var manyMembers = new LinkedHashMap<String, JsonValue>();
    unorderedKeys.forEach(key -> manyMembers.put(key, JsonBoolean.of(true)));
    String manyMembersText = unorderedKeys
      .stream()
      .map(key -> "\"" + key + "\":true")
      .collect(Collectors.joining(",", "{", "}"));

    JsonObject built = JsonObject.of(members);
    JsonValue builtFromCompact = Json.parse(built.toString());
    JsonValue builtFromDisplay = Json.parse(Json.toDisplayString(built, "  "));
    JsonValue spaced = Json.parse(spacedText);
    JsonValue spacedFromCompact = Json.parse(spaced.toString());
    JsonValue spacedFromDisplay = Json.parse(Json.toDisplayString(spaced, "  "));
    JsonObject manyBuilt = JsonObject.of(manyMembers);
    JsonValue manyParsed = Json.parse(manyMembersText);

    assertThat(built.toString()).isEqualTo(builtCompact);
    assertThat(Json.toDisplayString(built, "  ")).isEqualTo(builtDisplay);
    assertThat(builtFromCompact.toString()).isEqualTo(builtCompact);
    assertThat(builtFromDisplay.toString()).isEqualTo(builtCompact);
    assertThat(builtFromCompact.asMap().keySet()).containsExactlyElementsOf(members.keySet());
    assertThat(builtFromDisplay.asMap().keySet()).containsExactlyElementsOf(members.keySet());
    assertThat(builtFromDisplay.get("text").asString()).isEqualTo("say \"hi\"\n");
    assertThat(builtFromDisplay.get("long").asLong()).isEqualTo(9_007_199_254_740_993L);
    assertThat(builtFromDisplay.get("decimal").toString()).isEqualTo("1.50");

    assertThat(spaced.toString()).isEqualTo(spacedCompact);
    assertThat(Json.toDisplayString(spaced, "  ")).isEqualTo(spacedDisplay);
    assertThat(spacedFromCompact.toString()).isEqualTo(spacedCompact);
    assertThat(spacedFromDisplay.toString()).isEqualTo(spacedCompact);
    assertThat(spacedFromCompact.asMap().keySet()).containsExactly("b", "a", "c", "d");
    assertThat(spacedFromDisplay.asMap().keySet()).containsExactly("b", "a", "c", "d");
    assertThat(spacedFromDisplay.get("c").asMap().keySet()).containsExactly("z", "y");
    assertThat(spacedFromDisplay.get("a").asString()).isEqualTo("café /");

    assertThat(manyBuilt.asMap().keySet()).containsExactlyElementsOf(unorderedKeys);
    assertThat(manyParsed.asMap().keySet()).containsExactlyElementsOf(unorderedKeys);
    assertThat(manyBuilt.toString()).isEqualTo(manyMembersText);
    assertThat(manyParsed.toString()).isEqualTo(manyMembersText);
    assertThat(Json.parse(Json.toDisplayString(manyBuilt, "  ")).asMap().keySet()).containsExactlyElementsOf(
      unorderedKeys
    );
  }

  @Test
  @JourneyTest(speakingId = "json-value-document-model", acceptance = "j5")
  @DisplayName("documents nested 100 000 levels deep parse, print and locate errors without a StackOverflowError")
  void deepNestingNeverOverflowsTheStack() {
    int depth = 100_000;
    String nestedArrays = "[".repeat(depth) + "]".repeat(depth);
    String nestedArraysDisplay = "[\n".repeat(depth - 1) + "[]" + "\n]".repeat(depth - 1);
    String nestedObjects = "{\"a\":".repeat(depth) + "null" + "}".repeat(depth);
    String nestedObjectsDisplay = "{\n" + "\"a\": {\n".repeat(depth - 1) + "\"a\": null" + "\n}".repeat(depth);
    String elidedArrayPath = "$" + "[0]".repeat(32) + "…" + "[0]".repeat(32);
    String elidedObjectPath = "$" + ".a".repeat(32) + "…" + ".a".repeat(32);

    JsonValue parsedArrays = Json.parse(nestedArrays);
    JsonArray builtArrays = JsonArray.of(List.of());
    for (int level = 1; level < depth; level++) {
      builtArrays = JsonArray.of(List.of(builtArrays));
    }
    JsonValue arrayNode = parsedArrays;
    for (int level = 1; level < depth; level++) {
      arrayNode = arrayNode.get(0);
    }
    JsonValue innermostArray = arrayNode;
    JsonValue parsedObjects = Json.parse(nestedObjects);
    JsonValue builtObjects = JsonNull.of();
    for (int level = 0; level < depth; level++) {
      builtObjects = JsonObject.of(Map.of("a", builtObjects));
    }
    JsonValue objectNode = parsedObjects;
    for (int level = 0; level < depth; level++) {
      objectNode = objectNode.get("a");
    }
    JsonValue innermostNull = objectNode;

    assertThat(parsedArrays.toString()).isEqualTo(nestedArrays);
    assertThat(Json.toDisplayString(parsedArrays, "")).isEqualTo(nestedArraysDisplay);
    assertThat(Json.parse(nestedArraysDisplay).toString()).isEqualTo(nestedArrays);
    assertThat(builtArrays.toString()).isEqualTo(nestedArrays);
    assertThat(Json.toDisplayString(builtArrays, "")).isEqualTo(nestedArraysDisplay);
    assertThatThrownBy(innermostArray::asInt)
      .isInstanceOf(JsonValueException.class)
      .hasMessage(
        "cannot read " +
          elidedArrayPath +
          " (line 1, column 100000) as an int: it is an array; read an array with get(int) or asList()"
      );
    assertThatThrownBy(() -> Json.parse("[".repeat(depth))).isInstanceOfSatisfying(JsonParseException.class, error -> {
      assertThat(error.getMessage())
        .startsWith("cannot parse the JSON at line 1, column 100001 (")
        .endsWith("the text ends before the array that starts at line 1, column 100000 is closed; add the closing ']'");
      assertThat(error.getErrorLine()).isZero();
      assertThat(error.getErrorPosition()).isEqualTo(100_000);
    });

    assertThat(parsedObjects.toString()).isEqualTo(nestedObjects);
    assertThat(Json.toDisplayString(parsedObjects, "")).isEqualTo(nestedObjectsDisplay);
    assertThat(Json.parse(nestedObjectsDisplay).toString()).isEqualTo(nestedObjects);
    assertThat(builtObjects.toString()).isEqualTo(nestedObjects);
    assertThat(Json.toDisplayString(builtObjects, "")).isEqualTo(nestedObjectsDisplay);
    assertThatThrownBy(innermostNull::asInt)
      .isInstanceOf(JsonValueException.class)
      .hasMessage(
        "cannot read " +
          elidedObjectPath +
          " (line 1, column 500001) as an int: it is null; check for null with tryValue() or a JsonNull pattern" +
          " before reading"
      );
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

  record RejectedInput(String label, String text, String message, int line, int position) {
    @Override
    public String toString() {
      return label;
    }
  }
}
