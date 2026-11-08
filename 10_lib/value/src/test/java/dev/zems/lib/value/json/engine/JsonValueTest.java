package dev.zems.lib.value.json.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("JsonValue")
@ContractTest
class JsonValueTest {

  private static final String OBJECT_HINT =
    "it is an object; read an object with get(String), tryGet(String) or asMap()";
  private static final String ARRAY_HINT = "it is an array; read an array with get(int) or asList()";
  private static final String STRING_HINT = "it is a string; read a string with asString()";
  private static final String NUMBER_HINT = "it is a number; read a number with asInt(), asLong() or asDouble()";
  private static final String BOOLEAN_HINT = "it is a boolean; read a boolean with asBoolean()";
  private static final String NULL_HINT =
    "it is null; check for null with tryValue() or a JsonNull pattern before reading";

  @Nested
  @DisplayName("conversions")
  class Conversions {

    static Stream<ValueError> wrongTypes() {
      return Stream.of(
        new ValueError(
          "object as a string",
          () -> JsonObject.of(Map.of()).asString(),
          "cannot read the value as a string: " + OBJECT_HINT
        ),
        new ValueError(
          "array as an object",
          () -> JsonArray.of(List.of()).asMap(),
          "cannot read the value as an object: " + ARRAY_HINT
        ),
        new ValueError(
          "string as an int",
          () -> JsonString.of("x").asInt(),
          "cannot read the value as an int: " + STRING_HINT
        ),
        new ValueError(
          "number as a boolean",
          () -> JsonNumber.of(1).asBoolean(),
          "cannot read the value as a boolean: " + NUMBER_HINT
        ),
        new ValueError(
          "number as a string",
          () -> JsonNumber.of(1).asString(),
          "cannot read the value as a string: " + NUMBER_HINT
        ),
        new ValueError(
          "boolean as an array",
          () -> JsonBoolean.of(true).asList(),
          "cannot read the value as an array: " + BOOLEAN_HINT
        ),
        new ValueError("null as a long", () -> JsonNull.of().asLong(), "cannot read the value as a long: " + NULL_HINT),
        new ValueError(
          "null as a double",
          () -> JsonNull.of().asDouble(),
          "cannot read the value as a double: " + NULL_HINT
        ),
        new ValueError(
          "parsed element",
          () -> Json.parse("{\"a\": [true]}").get("a").get(0).asString(),
          "cannot read $.a[0] (line 1, column 8) as a string: " + BOOLEAN_HINT
        ),
        new ValueError(
          "parsed element on the second line",
          () -> Json.parse("[\n  null\n]").get(0).asInt(),
          "cannot read $[0] (line 2, column 3) as an int: " + NULL_HINT
        ),
        new ValueError(
          "parsed root after leading whitespace",
          () -> Json.parse("  true").asInt(),
          "cannot read $ (line 1, column 3) as an int: " + BOOLEAN_HINT
        ),
        new ValueError(
          "parsed root string",
          () -> Json.parse("\"x\"").asMap(),
          "cannot read $ (line 1, column 1) as an object: " + STRING_HINT
        ),
        new ValueError(
          "parsed member under a non-identifier name",
          () -> Json.parse("{\"a b\": {\"c\": 1}}").get("a b").get("c").asString(),
          "cannot read $[\"a b\"].c (line 1, column 15) as a string: " + NUMBER_HINT
        ),
        new ValueError(
          "parsed member under an escaped quote",
          () -> Json.parse("{\"a\\\"b\": 1}").get("a\"b").asString(),
          "cannot read $[\"a\\\"b\"] (line 1, column 10) as a string: " + NUMBER_HINT
        ),
        new ValueError(
          "parsed member under a Unicode escape",
          () -> Json.parse("{\"\\u0041\": 1}").get("A").asString(),
          "cannot read $.A (line 1, column 12) as a string: " + NUMBER_HINT
        ),
        new ValueError(
          "parsed element after strings holding brackets and commas",
          () -> Json.parse("{\"a\": \"[,{\", \"b\": [1, \"x,]\", true]}").get("b").get(2).asInt(),
          "cannot read $.b[2] (line 1, column 30) as an int: " + BOOLEAN_HINT
        ),
        new ValueError(
          "parsed member after a string holding an escaped quote",
          () -> Json.parse("{\"a\": \"\\\"[\", \"b\": true}").get("b").asInt(),
          "cannot read $.b (line 1, column 19) as an int: " + BOOLEAN_HINT
        )
      );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wrongTypes")
    @DisplayName("a conversion to another type names the subject, the target and how to read what it is (V1)")
    void refusesConversionToAnotherType(ValueError error) {
      assertThatThrownBy(error.access()).isInstanceOf(JsonValueException.class).hasMessage(error.message());
    }
  }

  @Nested
  @DisplayName("access")
  class Access {

    static Stream<ValueError> wrongContainers() {
      return Stream.of(
        new ValueError(
          "get(String) on a string",
          () -> JsonString.of("x").get("a"),
          "cannot read member \"a\" of the value: " + STRING_HINT
        ),
        new ValueError(
          "tryGet on null",
          () -> JsonNull.of().tryGet("a"),
          "cannot read member \"a\" of the value: " + NULL_HINT
        ),
        new ValueError(
          "get(String) with an escaped name",
          () -> JsonNumber.of(1).get("a\"b"),
          "cannot read member \"a\\\"b\" of the value: " + NUMBER_HINT
        ),
        new ValueError(
          "get(String) with a long name, cut at 40 characters",
          () -> JsonBoolean.of(true).get("m".repeat(41)),
          "cannot read member \"" + "m".repeat(40) + "…\" of the value: " + BOOLEAN_HINT
        ),
        new ValueError(
          "get(String) on a parsed array",
          () -> Json.parse("[1]").get("a"),
          "cannot read member \"a\" of $ (line 1, column 1): " + ARRAY_HINT
        ),
        new ValueError(
          "get(int) on an object",
          () -> JsonObject.of(Map.of()).get(0),
          "cannot read element 0 of the value: " + OBJECT_HINT
        ),
        new ValueError(
          "get(int) with a negative index on a boolean",
          () -> JsonBoolean.of(false).get(-1),
          "cannot read element -1 of the value: " + BOOLEAN_HINT
        ),
        new ValueError(
          "get(int) on a parsed string",
          () -> Json.parse("{\"a\": \"s\"}").get("a").get(2),
          "cannot read element 2 of $.a (line 1, column 7): " + STRING_HINT
        )
      );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wrongContainers")
    @DisplayName("get or tryGet on a value of another kind names the member or element and the kind (V1m, V1e)")
    void refusesAccessOnAnotherKind(ValueError error) {
      assertThatThrownBy(error.access()).isInstanceOf(JsonValueException.class).hasMessage(error.message());
    }

    @Test
    @DisplayName("a null name is refused before the kind is checked")
    void refusesNullName() {
      assertThatThrownBy(() -> JsonString.of("x").get((String) null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("name must not be null");
      assertThatThrownBy(() -> JsonString.of("x").tryGet(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("name must not be null");
    }

    @Test
    @DisplayName("tryValue answers the value itself, and empty for null, built or parsed")
    void tryValueIsEmptyOnlyForNull() {
      JsonValue number = JsonNumber.of(1);
      JsonValue parsedString = Json.parse("\"s\"");

      assertThat(number.tryValue()).containsSame(number);
      assertThat(parsedString.tryValue()).containsSame(parsedString);
      assertThat(JsonNull.of().tryValue()).isEmpty();
      assertThat(Json.parse("[null]").get(0).tryValue()).isEmpty();
    }

    @Test
    @DisplayName("a parsed value inside a built document reports its path in the document it was parsed from")
    void parsedValueKeepsItsOwnPathInsideABuiltDocument() {
      JsonValue parsed = Json.parse("{\"x\": [1, \"two\"]}").get("x").get(1);
      JsonObject built = JsonObject.of(Map.of("wrapper", JsonArray.of(List.of(parsed))));

      assertThatThrownBy(() -> built.get("wrapper").get(0).asInt())
        .isInstanceOf(JsonValueException.class)
        .hasMessage("cannot read $.x[1] (line 1, column 11) as an int: " + STRING_HINT);
    }
  }

  @Nested
  @DisplayName("identity")
  class Identity {

    @Test
    @DisplayName("values have no value equality: equal content is not equal")
    void equalContentIsNotEqual() {
      assertThat(Json.parse("1")).isNotEqualTo(Json.parse("1"));
      assertThat(JsonNumber.of(1)).isNotEqualTo(JsonNumber.of(1));
      assertThat(JsonString.of("x")).isNotEqualTo(JsonString.of("x"));
      assertThat(JsonArray.of(List.of())).isNotEqualTo(JsonArray.of(List.of()));
      assertThat(JsonObject.of(Map.of())).isNotEqualTo(JsonObject.of(Map.of()));
    }
  }

  @Nested
  @DisplayName("sealed hierarchy")
  class SealedHierarchy {

    @Test
    @DisplayName("a switch over the six kinds is exhaustive without a default arm")
    void switchOverTheSixKindsNeedsNoDefault() {
      List<JsonValue> values = List.of(
        JsonObject.of(Map.of()),
        JsonArray.of(List.of()),
        JsonString.of("s"),
        JsonNumber.of(1),
        JsonBoolean.of(true),
        JsonNull.of()
      );

      List<String> kinds = values.stream().map(JsonValueTest::kind).toList();

      assertThat(kinds).containsExactly("object", "array", "string", "number", "boolean", "null");
    }
  }

  @Nested
  @DisplayName("caller-implemented values")
  class CallerImplemented {

    @Test
    @DisplayName("a caller's JsonString prints through its own toString and fails as a built value")
    void callerValuePrintsThroughItsToStringAndFailsAsBuilt() {
      JsonArray array = JsonArray.of(List.of(new CallerString(), JsonNumber.of(1)));

      assertThat(array.toString()).isEqualTo("[\"CALLER\",1]");
      assertThat(Json.toDisplayString(array, " ")).isEqualTo("[\n \"CALLER\",\n 1\n]");
      assertThat(JsonObject.of(Map.of("a", new CallerString())).toString()).isEqualTo("{\"a\":\"CALLER\"}");
      assertThatThrownBy(() -> new CallerString().asInt())
        .isInstanceOf(JsonValueException.class)
        .hasMessage("cannot read the value as an int: " + STRING_HINT);
      assertThatThrownBy(() -> new CallerString().get("a"))
        .isInstanceOf(JsonValueException.class)
        .hasMessage("cannot read member \"a\" of the value: " + STRING_HINT);
    }
  }

  private static String kind(JsonValue value) {
    return switch (value) {
      case JsonObject _ -> "object";
      case JsonArray _ -> "array";
      case JsonString _ -> "string";
      case JsonNumber _ -> "number";
      case JsonBoolean _ -> "boolean";
      case JsonNull _ -> "null";
    };
  }

  record ValueError(String label, ThrowingCallable access, String message) {
    @Override
    public String toString() {
      return label;
    }
  }

  private static final class CallerString implements JsonString {

    @Override
    public String asString() {
      return "caller";
    }

    @Override
    public String toString() {
      return "\"CALLER\"";
    }
  }
}
