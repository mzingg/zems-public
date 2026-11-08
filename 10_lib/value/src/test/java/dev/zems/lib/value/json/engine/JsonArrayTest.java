package dev.zems.lib.value.json.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("JsonArray")
@ContractTest
class JsonArrayTest {

  @Nested
  @DisplayName("of")
  class Of {

    @Test
    @DisplayName("later changes to the source list do not reach the array")
    void copiesTheSourceList() {
      var elements = new ArrayList<JsonValue>();
      elements.add(JsonNumber.of(1));
      JsonArray array = JsonArray.of(elements);

      elements.add(JsonNumber.of(2));
      elements.set(0, JsonNull.of());

      assertThat(array.toString()).isEqualTo("[1]");
      assertThat(array.asList()).hasSize(1);
    }

    @Test
    @DisplayName("a null list or element is refused with NullPointerException naming the index")
    void refusesNulls() {
      assertThatThrownBy(() -> JsonArray.of(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("elements must not be null");
      assertThatThrownBy(() -> JsonArray.of(Arrays.asList(JsonNull.of(), null)))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("element 1 must not be null");
    }
  }

  @Nested
  @DisplayName("asList")
  class AsList {

    @Test
    @DisplayName("cannot be changed, built or parsed")
    void isUnmodifiable() {
      JsonArray built = JsonArray.of(List.of(JsonNumber.of(1)));
      JsonValue parsed = Json.parse("[1]");

      assertThatThrownBy(() -> built.asList().add(JsonNull.of())).isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(() -> built.asList().set(0, JsonNull.of())).isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(() -> parsed.asList().add(JsonNull.of())).isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(() -> parsed.asList().remove(0)).isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("get")
  class Get {

    @Test
    @DisplayName("answers the element at the index")
    void answersTheElement() {
      JsonArray array = JsonArray.of(List.of(JsonString.of("a"), JsonString.of("b")));

      assertThat(array.get(0).asString()).isEqualTo("a");
      assertThat(array.get(1).asString()).isEqualTo("b");
    }

    @Test
    @DisplayName("an index outside the array names the valid indices (V3)")
    void refusesAnIndexOutside() {
      JsonArray three = JsonArray.of(List.of(JsonNumber.of(1), JsonNumber.of(2), JsonNumber.of(3)));

      assertThatThrownBy(() -> three.get(3))
        .isInstanceOf(JsonValueException.class)
        .hasMessage(
          "cannot read element 3 of the array: the array has 3 elements (indices 0 to 2); check asList().size() first"
        );
      assertThatThrownBy(() -> three.get(-1))
        .isInstanceOf(JsonValueException.class)
        .hasMessage(
          "cannot read element -1 of the array: the array has 3 elements (indices 0 to 2); check asList().size() first"
        );
      assertThatThrownBy(() -> JsonArray.of(List.of(JsonNull.of())).get(1))
        .isInstanceOf(JsonValueException.class)
        .hasMessage(
          "cannot read element 1 of the array: the array has 1 element (index 0); check asList().size() first"
        );
      assertThatThrownBy(() -> JsonArray.of(List.of()).get(0))
        .isInstanceOf(JsonValueException.class)
        .hasMessage("cannot read element 0 of the array: the array is empty; check asList().size() first");
      assertThatThrownBy(() -> Json.parse("[[1, 2]]").get(0).get(2))
        .isInstanceOf(JsonValueException.class)
        .hasMessage(
          "cannot read element 2 of $[0] (line 1, column 2): the array has 2 elements (indices 0 to 1); check" +
            " asList().size() first"
        );
    }
  }

  @Nested
  @DisplayName("toString")
  class ToString {

    @Test
    @DisplayName("prints compact JSON")
    void printsCompactJson() {
      JsonArray array = JsonArray.of(
        List.of(JsonNumber.of(1), JsonString.of("a"), JsonNull.of(), JsonArray.of(List.of()), JsonObject.of(Map.of()))
      );

      assertThat(array.toString()).isEqualTo("[1,\"a\",null,[],{}]");
    }
  }
}
