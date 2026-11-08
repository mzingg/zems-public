package dev.zems.lib.value.json;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.json.engine.JsonString;
import dev.zems.lib.value.json.engine.JsonValue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("JsonText — emits JSON documents as text")
class JsonTextTest {

  @Nested
  @DisplayName("compact")
  class Compact {

    @Test
    @DisplayName("a parsed document emits its strings and numbers exactly as written, without whitespace")
    void aParsedDocumentEmitsAsWritten() {
      var document = Value.parseJson("{ \"a\" : [ 1.50 , \"\\u0041\" ] , \"b\" : 1E+2 }");

      assertThat(JsonText.compact(document)).isEqualTo(Value.of("{\"a\":[1.50,\"\\u0041\"],\"b\":1E+2}"));
    }

    @Test
    @DisplayName("a leaf emits as a JSON scalar, and the null marker emits null")
    void leavesAndTheNullMarkerEmit() {
      assertThat(JsonText.compact(Value.jsonOf("x"))).isEqualTo(Value.of("\"x\""));
      assertThat(JsonText.compact(Value.jsonOf(7))).isEqualTo(Value.of("7"));
      assertThat(JsonText.compact(Value.jsonOf(true))).isEqualTo(Value.of("true"));
      assertThat(JsonText.compact(Value.nullValue())).isEqualTo(Value.of("null"));
    }

    @Test
    @DisplayName("any other state marker answers itself re-typed to String, an error with its throwable kept")
    void otherStateMarkersAnswerThemselvesReTyped() {
      Value<Integer> failed = Value.errorMessage("boom", Integer.class);

      Value<String> emitted = JsonText.compact(failed);

      assertThat(emitted).isEqualTo(Value.errorMessage("boom", String.class));
      assertThat(emitted.error()).containsSame(failed.error().orElseThrow());
      assertThat(JsonText.compact(Value.undefined())).isUndefined();
      assertThat(JsonText.compact(Value.unsupported("not here", Integer.class))).isEqualTo(
        Value.unsupported("not here", String.class)
      );
    }

    @Test
    @DisplayName("a value that is not JSON answers an UnsupportedValue naming its type")
    void aNonJsonValueIsUnsupported() {
      Value<JsonValue> boxed = Value.of((JsonValue) JsonString.of("x"));

      assertThat(JsonText.compact(boxed)).isEqualTo(Value.unsupported("BoxedValue is not a JSON value", String.class));
      assertThat(JsonText.compact(Value.of(42))).isEqualTo(
        Value.unsupported("IntegerValue is not a JSON value", String.class)
      );
      assertThat(JsonText.compact(Value.listOf(1, 2))).isEqualTo(
        Value.unsupported("ListValue is not a JSON value", String.class)
      );
    }

    @Test
    @DisplayName("a null document is a caller bug and throws")
    void aNullDocumentThrows() {
      assertThatThrownBy(() -> JsonText.compact(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Document must not be null");
    }
  }

  @Nested
  @DisplayName("indented")
  class Indented {

    @Test
    @DisplayName("each member and element sits on its own line under the given indent; empty containers stay inline")
    void eachMemberSitsOnItsOwnLine() {
      Value<JsonValue> document = Value.jsonOf(
        List.of(Value.jsonOf(Map.of("a", Value.jsonOf(1))), Value.jsonOf(List.of()))
      );

      assertThat(JsonText.indented(document, "\t")).isEqualTo(Value.of("[\n\t{\n\t\t\"a\": 1\n\t},\n\t[]\n]"));
      assertThat(JsonText.indented(Value.jsonOf("x"), "  ")).isEqualTo(Value.of("\"x\""));
      assertThat(JsonText.indented(Value.nullValue(), "  ")).isEqualTo(Value.of("null"));
    }

    @Test
    @DisplayName("state markers and non-JSON values answer as for compact")
    void markersAndNonJsonValuesAnswerAsForCompact() {
      assertThat(JsonText.indented(Value.errorMessage("boom", Integer.class), "  ")).isEqualTo(
        Value.errorMessage("boom", String.class)
      );
      assertThat(JsonText.indented(Value.of(42), "  ")).isEqualTo(
        Value.unsupported("IntegerValue is not a JSON value", String.class)
      );
    }

    @Test
    @DisplayName("an indent that is not JSON whitespace throws the engine's message, before the document is looked at")
    void anInvalidIndentThrows() {
      var document = Value.parseJson("[1]");
      Value<Integer> notJson = Value.of(42);

      assertThatThrownBy(() -> JsonText.indented(document, "x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
          "cannot indent the JSON with \"x\": an indent may contain only spaces, tabs, line feeds and carriage returns"
        );
      assertThatThrownBy(() -> JsonText.indented(notJson, "-"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
          "cannot indent the JSON with \"-\": an indent may contain only spaces, tabs, line feeds and carriage returns"
        );
    }

    @Test
    @DisplayName("a null indent or a null document is a caller bug and throws")
    void nullArgumentsThrow() {
      var document = Value.parseJson("[1]");

      assertThatThrownBy(() -> JsonText.indented(document, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("indent must not be null");
      assertThatThrownBy(() -> JsonText.indented(null, "  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Document must not be null");
    }
  }
}
