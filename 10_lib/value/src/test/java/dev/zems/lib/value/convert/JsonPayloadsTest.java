package dev.zems.lib.value.convert;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.json.engine.JsonArray;
import dev.zems.lib.value.json.engine.JsonBoolean;
import dev.zems.lib.value.json.engine.JsonNull;
import dev.zems.lib.value.json.engine.JsonNumber;
import dev.zems.lib.value.json.engine.JsonObject;
import dev.zems.lib.value.json.engine.JsonString;
import dev.zems.lib.value.json.engine.JsonValue;
import java.net.InetAddress;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("JsonPayloads — the table key and the scalar fallback for an engine payload")
class JsonPayloadsTest {

  @Nested
  @DisplayName("lookupKeyOf")
  class LookupKeyOf {

    @Test
    @DisplayName("an engine value answers its mirrored kind interface, never its implementation class")
    void anEngineValueAnswersItsKindInterface() {
      assertThat(JsonPayloads.lookupKeyOf(engineOf("{\"a\":1}"))).isEqualTo(JsonObject.class);
      assertThat(JsonPayloads.lookupKeyOf(engineOf("[1]"))).isEqualTo(JsonArray.class);
      assertThat(JsonPayloads.lookupKeyOf(engineOf("\"x\""))).isEqualTo(JsonString.class);
      assertThat(JsonPayloads.lookupKeyOf(engineOf("1.5"))).isEqualTo(JsonNumber.class);
      assertThat(JsonPayloads.lookupKeyOf(engineOf("true"))).isEqualTo(JsonBoolean.class);
      assertThat(JsonPayloads.lookupKeyOf(JsonNull.of())).isEqualTo(JsonNull.class);
    }

    @Test
    @DisplayName("a foreign implementation of a kind interface answers that kind interface")
    void aForeignImplementationAnswersItsKindInterface() {
      ForeignObject foreign = new ForeignObject(Map.of());

      assertThat(JsonPayloads.lookupKeyOf(foreign)).isEqualTo(JsonObject.class);
    }

    @Test
    @DisplayName("any other payload answers its own runtime class, so the superclass walk sees exactly that class")
    void anyOtherPayloadAnswersItsRuntimeClass() {
      InetAddress address = InetAddress.ofLiteral("192.168.1.1");

      assertThat(JsonPayloads.lookupKeyOf("x")).isEqualTo(String.class);
      assertThat(JsonPayloads.lookupKeyOf(42)).isEqualTo(Integer.class);
      assertThat(JsonPayloads.lookupKeyOf(address)).isEqualTo(address.getClass()).isNotEqualTo(InetAddress.class);
    }
  }

  @Nested
  @DisplayName("scalarOrNull")
  class ScalarOrNull {

    @Test
    @DisplayName("a JSON string answers its decoded text as a String value, a JSON boolean its Boolean value")
    void stringsAndBooleansAnswerTheirBuiltInValue() {
      assertThat(JsonPayloads.scalarOrNull(engineOf("\"\\u0041b\""))).isEqualTo(Value.of("Ab"));
      assertThat(JsonPayloads.scalarOrNull(engineOf("false"))).isEqualTo(Value.of(false));
    }

    @Test
    @DisplayName("a number, an object, an array, a JSON null and a payload that is not JSON answer null")
    void everythingElseAnswersNull() {
      assertThat(JsonPayloads.scalarOrNull(engineOf("1"))).isNull();
      assertThat(JsonPayloads.scalarOrNull(engineOf("{}"))).isNull();
      assertThat(JsonPayloads.scalarOrNull(engineOf("[]"))).isNull();
      assertThat(JsonPayloads.scalarOrNull(JsonNull.of())).isNull();
      assertThat(JsonPayloads.scalarOrNull("x")).isNull();
    }
  }

  private static JsonValue engineOf(String text) {
    return Value.parseJson(text).unbox();
  }

  record ForeignObject(Map<String, JsonValue> asMap) implements JsonObject {}
}
