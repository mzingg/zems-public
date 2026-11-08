package dev.zems.lib.value;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.json.engine.JsonParseException;
import dev.zems.lib.value.json.engine.JsonString;
import dev.zems.lib.value.json.engine.JsonValue;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("ValueJson — parses JSON text and builds JSON values; every failure is an error value")
class ValueJsonTest {

  @Nested
  @DisplayName("parse")
  class Parse {

    @Test
    @DisplayName("any JSON value parses as the root, and a root null is the null marker")
    void anyRootParses() {
      assertThat(Value.parseJson(" 42 ")).isEqualTo(Value.jsonOf(42));
      assertThat(Value.parseJson("\"x\"")).isEqualTo(Value.jsonOf("x"));
      assertThat(Value.parseJson("false")).isEqualTo(Value.jsonOf(false));
      assertThat(Value.parseJson("[]")).isEqualTo(Value.jsonOf(List.of()));
      assertThat(Value.parseJson("{}")).isEqualTo(Value.jsonOf(Map.of()));
      assertThat(Value.parseJson("\n null\t")).isNullValue();
    }

    @Test
    @DisplayName("a parse error keeps the engine's exception as its cause, with its zero-based line and position")
    void aParseErrorKeepsTheEngineException() {
      var broken = Value.parseJson("{\n  \"a\": tru\n}");

      assertThat(broken).isEqualTo(
        Value.errorMessage(
          "cannot parse the JSON at line 2, column 8 ($.a): tru is not valid JSON; the only literals are true, false and" +
            " null, written in lower case",
          Object.class
        )
      );
      assertThat(broken)
        .extractingThrowable()
        .isInstanceOf(IllegalStateException.class)
        .cause()
        .isInstanceOfSatisfying(JsonParseException.class, cause -> {
          assertThat(cause.getErrorLine()).isEqualTo(1);
          assertThat(cause.getErrorPosition()).isEqualTo(7);
        });
    }

    @Test
    @DisplayName("an empty or blank text is a parse error, and a null text answers P0")
    void emptyBlankAndNullTextsAreErrors() {
      assertThat(Value.parseJson("")).isEqualTo(
        Value.errorMessage(
          "cannot parse the JSON at line 1, column 1 ($): the text contains no JSON value; pass a value such as {}, [] or" +
            " \"text\"",
          Object.class
        )
      );
      assertThat(Value.parseJson(null)).isEqualTo(
        Value.errorMessage("cannot parse the JSON: the text is null", Object.class)
      );
    }
  }

  @Nested
  @DisplayName("object")
  class ObjectFactory {

    @Test
    @DisplayName("members keep the map's iteration order, and a null marker member emits null")
    void membersKeepTheMapOrder() {
      Map<String, Value<JsonValue>> members = new LinkedHashMap<>();
      members.put("z", Value.jsonOf(1));
      members.put("a", Value.nullValue());
      members.put("m", Value.parseJson("[1.50, \"\\u0041\"]"));

      Value<JsonValue> object = Value.jsonOf(members);

      assertThat(object.toString()).isEqualTo("{\"z\":1,\"a\":null,\"m\":[1.50,\"\\u0041\"]}");
      assertThat(object.unbox().asMap().keySet()).containsExactly("z", "a", "m");
    }

    @Test
    @DisplayName("a member that is another state marker or a boxed engine value is named with its type and article")
    void aNonJsonMemberIsNamedWithItsType() {
      Value<JsonValue> boxed = Value.of((JsonValue) JsonString.of("x"));
      Value<JsonValue> notYetResolved = Value.unresolved();

      assertThat(Value.jsonOf(Map.of("u", Value.undefined()))).isEqualTo(
        Value.errorMessage(
          "cannot build a JSON object: member 'u' holds an UndefinedValue, not a JSON value or the null marker",
          Object.class
        )
      );
      assertThat(Value.jsonOf(Map.of("t", Value.tombstone()))).isEqualTo(
        Value.errorMessage(
          "cannot build a JSON object: member 't' holds a TombstoneValue, not a JSON value or the null marker",
          Object.class
        )
      );
      assertThat(Value.jsonOf(Map.of("r", notYetResolved))).isEqualTo(
        Value.errorMessage(
          "cannot build a JSON object: member 'r' holds an UnresolvedValue, not a JSON value or the null marker",
          Object.class
        )
      );
      assertThat(Value.jsonOf(Map.of("b", boxed))).isEqualTo(
        Value.errorMessage(
          "cannot build a JSON object: member 'b' holds a BoxedValue, not a JSON value or the null marker",
          Object.class
        )
      );
    }

    @Test
    @DisplayName("an ErrorValue member is refused with its throwable kept as the cause")
    void anErrorMemberKeepsItsCause() {
      Value<JsonValue> failed = Value.errorOf(new IllegalArgumentException("bad input"), JsonValue.class);

      Value<JsonValue> refused = Value.jsonOf(Map.of("e", failed));

      assertThat(refused).isEqualTo(
        Value.errorMessage(
          "cannot build a JSON object: member 'e' holds an ErrorValue, not a JSON value or the null marker",
          Object.class
        )
      );
      assertThat(refused).extractingThrowable().cause().isSameAs(failed.error().orElseThrow());
    }

    @Test
    @DisplayName("a null map, a null name and a null member are refused as data, never thrown")
    void nullInputsAreRefused() {
      Map<String, Value<JsonValue>> nullName = new HashMap<>();
      nullName.put(null, Value.jsonOf(1));
      Map<String, Value<JsonValue>> nullMember = new HashMap<>();
      nullMember.put("a", null);

      assertThat(Value.jsonOf((Map<String, Value<JsonValue>>) null)).isEqualTo(
        Value.errorMessage("cannot build a JSON object: the members map is null", Object.class)
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
    }
  }

  @Nested
  @DisplayName("array")
  class ArrayFactory {

    @Test
    @DisplayName("elements keep the list's order, and a null marker element emits null")
    void elementsKeepTheListOrder() {
      Value<JsonValue> array = Value.jsonOf(List.of(Value.jsonOf("b"), Value.nullValue(), Value.jsonOf(-0.0d)));

      assertThat(array.toString()).isEqualTo("[\"b\",null,-0.0]");
    }

    @Test
    @DisplayName("an element that is not JSON-shaped is named by index, an ErrorValue element keeps its cause")
    void aNonJsonElementIsNamedByIndex() {
      Value<JsonValue> failed = Value.errorMessage("boom", JsonValue.class);
      Value<JsonValue> boxed = Value.of((JsonValue) JsonString.of("x"));

      Value<JsonValue> refused = Value.jsonOf(List.of(Value.jsonOf(1), Value.jsonOf(2), failed));

      assertThat(Value.jsonOf(List.of(boxed))).isEqualTo(
        Value.errorMessage(
          "cannot build a JSON array: element 0 holds a BoxedValue, not a JSON value or the null marker",
          Object.class
        )
      );
      assertThat(refused).isEqualTo(
        Value.errorMessage(
          "cannot build a JSON array: element 2 holds an ErrorValue, not a JSON value or the null marker",
          Object.class
        )
      );
      assertThat(refused).extractingThrowable().cause().isSameAs(failed.error().orElseThrow());
    }
  }

  @Nested
  @DisplayName("string, character, number and boolean")
  class LeafFactories {

    @Test
    @DisplayName("a string emits escaped, and a null text is refused")
    void aStringEmitsEscaped() {
      assertThat(Value.jsonOf("say \"hi\"\n").toString()).isEqualTo("\"say \\\"hi\\\"\\n\"");
      assertThat(Value.jsonOf((String) null)).isEqualTo(
        Value.errorMessage("cannot build a JSON string: the text is null", Object.class)
      );
    }

    @Test
    @DisplayName("a text is the string itself and is never parsed, even when it reads as a number, a literal or empty")
    void aTextIsNeverParsed() {
      assertThat(Value.jsonOf("1815")).isEqualTo(Value.parseJson("\"1815\""));
      assertThat(Value.jsonOf("1815")).isNotEqualTo(Value.jsonOf(1815));
      assertThat(Value.jsonOf("1815").toString()).isEqualTo("\"1815\"");
      assertThat(Value.jsonOf("null").toString()).isEqualTo("\"null\"");
      assertThat(Value.jsonOf("[1,").toString()).isEqualTo("\"[1,\"");
      assertThat(Value.jsonOf("").toString()).isEqualTo("\"\"");
    }

    @Test
    @DisplayName("a char, boxed or not, builds a one-character string, never its code as a number")
    void aCharBuildsAOneCharacterString() {
      assertThat(Value.jsonOf('x')).isEqualTo(Value.jsonOf("x"));
      assertThat(Value.jsonOf('x').toString()).isEqualTo("\"x\"");
      assertThat(Value.jsonOf('7')).isEqualTo(Value.parseJson("\"7\""));
      assertThat(Value.jsonOf('7')).isNotEqualTo(Value.jsonOf(55));
      assertThat(Value.jsonOf('"').toString()).isEqualTo("\"\\\"\"");
      assertThat(Value.jsonOf(Character.valueOf('y')).toString()).isEqualTo("\"y\"");
    }

    @Test
    @DisplayName("each number overload emits the JDK's own text for its type")
    void numbersEmitTheirText() {
      assertThat(Value.jsonOf(Long.MIN_VALUE).toString()).isEqualTo("-9223372036854775808");
      assertThat(Value.jsonOf(1e300d).toString()).isEqualTo("1.0E300");
      assertThat(Value.jsonOf(new BigInteger("123456789012345678901234567890")).toString()).isEqualTo(
        "123456789012345678901234567890"
      );
      assertThat(Value.jsonOf(new BigDecimal("1E+3")).toString()).isEqualTo("1E+3");
      assertThat(Value.jsonOf(new BigDecimal("1.50")).toString()).isEqualTo("1.50");
    }

    @Test
    @DisplayName("an int, a short and a boxed Integer build through the long overload, a float through the double one")
    void narrowerNumbersWidenToLongOrDouble() {
      assertThat(Value.jsonOf(1815).toString()).isEqualTo("1815");
      assertThat(Value.jsonOf(Integer.valueOf(1815)).toString()).isEqualTo("1815");
      assertThat(Value.jsonOf((short) 3).toString()).isEqualTo("3");
      assertThat(Value.jsonOf(2.5f).toString()).isEqualTo("2.5");
      assertThat(Value.jsonOf(Integer.valueOf(1815))).isEqualTo(Value.parseJson("1815"));
    }

    @Test
    @DisplayName("a non-finite double and a null big number are refused")
    void nonFiniteAndNullNumbersAreRefused() {
      assertThat(Value.jsonOf(Double.NaN)).isEqualTo(
        Value.errorMessage("cannot build a JSON number from NaN: JSON numbers must be finite", Object.class)
      );
      assertThat(Value.jsonOf(Double.POSITIVE_INFINITY)).isEqualTo(
        Value.errorMessage("cannot build a JSON number from Infinity: JSON numbers must be finite", Object.class)
      );
      assertThat(Value.jsonOf(Double.NEGATIVE_INFINITY)).isEqualTo(
        Value.errorMessage("cannot build a JSON number from -Infinity: JSON numbers must be finite", Object.class)
      );
      assertThat(Value.jsonOf((BigInteger) null)).isEqualTo(
        Value.errorMessage("cannot build a JSON number: the number is null", Object.class)
      );
      assertThat(Value.jsonOf((BigDecimal) null)).isEqualTo(
        Value.errorMessage("cannot build a JSON number: the number is null", Object.class)
      );
    }

    @Test
    @DisplayName("a boolean emits its literal and equals the parsed literal")
    void aBooleanEmitsItsLiteral() {
      assertThat(Value.jsonOf(true).toString()).isEqualTo("true");
      assertThat(Value.jsonOf(false)).isEqualTo(Value.parseJson("false"));
      assertThat(Value.jsonOf(true)).isNotEqualTo(Value.jsonOf(false));
    }
  }
}
