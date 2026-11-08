package dev.zems.lib.value.json;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.json.engine.JsonNull;
import dev.zems.lib.value.json.engine.JsonNumber;
import dev.zems.lib.value.json.engine.JsonObject;
import dev.zems.lib.value.json.engine.JsonString;
import dev.zems.lib.value.json.engine.JsonValue;
import dev.zems.lib.value.valueio.JournalingStateWriter;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("JsonDocumentValue — a JSON document held as its engine value, equal by value")
class JsonDocumentValueTest {

  @Nested
  @DisplayName("construction")
  class Construction {

    @Test
    @DisplayName("a null root is refused with a NullPointerException")
    void aNullRootIsRefused() {
      assertThatThrownBy(() -> new JsonDocumentValue(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("root must not be null");
    }

    @Test
    @DisplayName("a JSON null root is refused: at the value level a JSON null is the null marker")
    void aJsonNullRootIsRefused() {
      JsonNull jsonNull = JsonNull.of();

      assertThatThrownBy(() -> new JsonDocumentValue(jsonNull))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("a JSON null is Value.nullValue(), not a JSON document value");
    }

    @Test
    @DisplayName("a foreign implementation of a kind interface is accepted and equals the same parsed document")
    void aForeignKindImplementationIsAccepted() {
      Map<String, JsonValue> members = new LinkedHashMap<>();
      members.put("b", JsonString.of("x"));
      members.put("a", JsonNumber.of(1));

      JsonDocumentValue foreign = new JsonDocumentValue(new ForeignObject(members));

      assertThat(foreign).isEqualTo(Value.parseJson("{\"a\":1.0,\"b\":\"x\"}"));
      assertThat(Value.parseJson("{\"a\":1.0,\"b\":\"x\"}")).isEqualTo(foreign);
      assertThat(foreign.hashCode()).isEqualTo(Value.parseJson("{\"a\":1.0,\"b\":\"x\"}").hashCode());
      assertThat(JsonText.compact(Value.jsonOf(Map.of("inner", foreign)))).isEqualTo(
        Value.of("{\"inner\":{\"b\":\"x\",\"a\":1}}")
      );
    }

    @Test
    @DisplayName("a foreign implementation that throws makes hashing and equality throw its exception unchanged")
    void aThrowingForeignImplementationPropagates() {
      JsonDocumentValue broken = new JsonDocumentValue(new ThrowingObject());
      Value<JsonValue> parsed = Value.parseJson("{}");

      assertThatThrownBy(broken::hashCode).isInstanceOf(IllegalStateException.class).hasMessage("broken engine");
      assertThatThrownBy(() -> broken.equals(parsed))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("broken engine");
    }
  }

  @Nested
  @DisplayName("equality and hashing")
  class Equality {

    @Test
    @DisplayName("numbers written differently but equal in value are equal and hash alike")
    void equalNumbersAreEqualHoweverWritten() {
      assertThat(Value.parseJson("1.50")).isEqualTo(Value.parseJson("1.5"));
      assertThat(Value.parseJson("1.50").hashCode()).isEqualTo(Value.parseJson("1.5").hashCode());
      assertThat(Value.parseJson("0.0")).isEqualTo(Value.parseJson("-0e7"));
      assertThat(Value.parseJson("0.0").hashCode()).isEqualTo(Value.parseJson("-0e7").hashCode());
      assertThat(Value.parseJson("12e-1")).isEqualTo(Value.parseJson("1.2"));
      assertThat(Value.jsonOf(1L)).isEqualTo(Value.jsonOf(BigInteger.ONE));
      assertThat(Value.jsonOf(1L)).isEqualTo(Value.jsonOf(new BigDecimal("1.000")));
    }

    @Test
    @DisplayName("different numbers are not equal, down to the last unit of a huge exponent")
    void differentNumbersAreNotEqual() {
      assertThat(Value.parseJson("1e99999999999999999999")).isNotEqualTo(Value.parseJson("1e99999999999999999998"));
      assertThat(Value.parseJson("1")).isNotEqualTo(Value.parseJson("-1"));
      assertThat(Value.parseJson("1")).isNotEqualTo(Value.parseJson("10"));
      assertThat(Value.parseJson("0.1")).isNotEqualTo(Value.parseJson("0.01"));
    }

    @Test
    @DisplayName("a parsed string equals the built string of the same text, each rendering as it came")
    void parsedAndBuiltStringsCompareByText() {
      Value<JsonValue> plain = Value.parseJson("\"A\"");
      Value<JsonValue> escaped = Value.parseJson("\"\\u0041\"");
      Value<JsonValue> built = Value.jsonOf("A");

      assertThat(plain).isEqualTo(built);
      assertThat(escaped).isEqualTo(built);
      assertThat(escaped.hashCode()).isEqualTo(built.hashCode());
      assertThat(plain.toString()).isEqualTo("\"A\"");
      assertThat(escaped.toString()).isEqualTo("\"\\u0041\"");
      assertThat(built.toString()).isEqualTo("\"A\"");
      assertThat(built).isNotEqualTo(Value.jsonOf("a"));
    }

    @Test
    @DisplayName("objects with the same members in another order are equal and hash alike; a differing value is not")
    void memberOrderDoesNotAffectEquality() {
      Value<JsonValue> first = Value.parseJson("{\"a\":{\"x\":true,\"y\":false},\"b\":[]}");
      Value<JsonValue> second = Value.parseJson("{\"b\":[],\"a\":{\"y\":false,\"x\":true}}");

      assertThat(first).isEqualTo(second);
      assertThat(first.hashCode()).isEqualTo(second.hashCode());
      assertThat(first).isNotEqualTo(Value.parseJson("{\"a\":{\"x\":true,\"y\":true},\"b\":[]}"));
      assertThat(first).isNotEqualTo(Value.parseJson("{\"a\":{\"x\":true,\"y\":false}}"));
    }

    @Test
    @DisplayName("arrays compare element by element: order and length count, a null element equals only a null")
    void arraysCompareElementByElement() {
      assertThat(Value.parseJson("[1,[true],null]")).isEqualTo(
        Value.jsonOf(List.of(Value.jsonOf(1.0d), Value.jsonOf(List.of(Value.jsonOf(true))), Value.nullValue()))
      );
      assertThat(Value.parseJson("[1,[true],null]").hashCode()).isEqualTo(
        Value.parseJson("[1.0,[true],null]").hashCode()
      );
      assertThat(Value.parseJson("[null]")).isNotEqualTo(Value.parseJson("[0]"));
      assertThat(Value.parseJson("[1,2]")).isNotEqualTo(Value.parseJson("[2,1]"));
      assertThat(Value.parseJson("[1]")).isNotEqualTo(Value.parseJson("[1,1]"));
    }

    @Test
    @DisplayName("a document equals only a document of the same kind, never a built-in value with the same content")
    void aDocumentNeverEqualsABuiltInValue() {
      assertThat(Value.jsonOf(1)).isNotEqualTo(Value.of(1L));
      assertThat(Value.of(BigDecimal.ONE)).isNotEqualTo(Value.jsonOf(BigDecimal.ONE));
      assertThat(Value.jsonOf("x")).isNotEqualTo(Value.of("x"));
      assertThat(Value.of("x")).isNotEqualTo(Value.jsonOf("x"));
      assertThat(Value.jsonOf(true)).isNotEqualTo(Value.of(true));
      assertThat(Value.jsonOf("1")).isNotEqualTo(Value.jsonOf(1));
      assertThat(Value.parseJson("[]")).isNotEqualTo(Value.parseJson("{}"));
    }

    @Test
    @DisplayName("the hash is recomputed from the root on every call: the record holds no field but root")
    void theHashIsRecomputedWithoutACache() {
      Value<JsonValue> document = Value.parseJson("{\"a\":[1,2,{\"b\":\"c\"}]}");

      int first = document.hashCode();
      int second = document.hashCode();

      assertThat(second).isEqualTo(first);
      assertThat(JsonDocumentValue.class.getDeclaredFields()).extracting(Field::getName).containsExactly("root");
    }
  }

  @Nested
  @DisplayName("payload, emission and navigation")
  class Payload {

    @Test
    @DisplayName("a number renders exactly as written, its engine value is the payload")
    void aNumberRendersAsWritten() {
      Value<JsonValue> number = Value.parseJson("1e999999999999");

      assertThat(number.toString()).isEqualTo("1e999999999999");
      assertThat(number.unbox()).isInstanceOfSatisfying(JsonNumber.class, engine ->
        assertThat(engine.toString()).isEqualTo("1e999999999999")
      );
    }

    @Test
    @DisplayName("a string's payload answers the decoded text")
    void aStringsPayloadAnswersTheDecodedText() {
      Value<JsonValue> parsed = Value.parseJson("\"caf\\u00e9 \\\"x\\\"\\n\"");

      assertThat(parsed.unbox().asString()).isEqualTo("café \"x\"\n");
    }

    @Test
    @DisplayName("the payload keeps member order, and its members map is unmodifiable")
    void thePayloadKeepsMemberOrder() {
      Value<JsonValue> object = Value.parseJson("{\"z\":{\"in\":1},\"a\":null,\"m\":[2]}");
      Map<String, JsonValue> members = object.unbox().asMap();
      JsonValue two = JsonNumber.of(2);

      assertThat(members.keySet()).containsExactly("z", "a", "m");
      assertThat(members.get("a")).isInstanceOf(JsonNull.class);
      assertThatThrownBy(() -> members.put("b", two)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("a document names no type descriptor, so a ValueIo write needs an explicit one")
    void aDocumentNamesNoTypeDescriptor() {
      Value<JsonValue> object = Value.parseJson("{\"a\":1}");

      assertThat(object.valueType()).isNull();
      assertThatThrownBy(() -> new JournalingStateWriter().write(object))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
          "Cannot infer TypeDescriptor for JsonDocumentValue — valueType() returned null. Pass an explicit descriptor" +
            " via write(value, descriptor)."
        );
    }

    @Test
    @DisplayName("the navigator's root exit is the document itself, and a leaf has nothing to step into")
    void theNavigatorStartsAtTheDocument() {
      JsonDocumentValue number = (JsonDocumentValue) Value.jsonOf(5);
      JsonDocumentValue text = (JsonDocumentValue) Value.jsonOf("abc");

      assertThat(number.navigate().value()).isEqualTo(number);
      assertThat(number.navigate().get(0).value()).isEqualTo(
        Value.errorMessage("$: cannot step to element 0 of a number", Object.class)
      );
      assertThat(text.navigate().get("length").value()).isEqualTo(
        Value.errorMessage("$: cannot step to member 'length' of a string", Object.class)
      );
    }
  }

  record ForeignObject(Map<String, JsonValue> asMap) implements JsonObject {}

  static final class ThrowingObject implements JsonObject {

    @Override
    public Map<String, JsonValue> asMap() {
      throw new IllegalStateException("broken engine");
    }

    @Override
    public String toString() {
      return "{broken}";
    }
  }
}
