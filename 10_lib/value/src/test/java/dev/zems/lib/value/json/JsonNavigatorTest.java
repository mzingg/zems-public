package dev.zems.lib.value.json;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.json.engine.JsonValue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("JsonNavigator — steps through members and elements, carrying the first failure")
class JsonNavigatorTest {

  @Test
  @DisplayName("the root exit answers the value navigate() was called on, and convertTo converts it")
  void theRootExitAnswersTheValue() {
    Value<JsonValue> number = Value.jsonOf(42);

    JsonNavigator navigator = ((JsonDocumentValue) number).navigate();
    Value<JsonValue> exit = navigator.value();

    assertThat(exit).isEqualTo(number);
    assertThat(navigator.convertTo(Long.class)).isEqualTo(Value.of(42L));
  }

  @Test
  @DisplayName("a negative index is out of range, and the message names the size")
  void aNegativeIndexIsOutOfRange() {
    var array = Value.parseJson("[1]");

    assertThat(array.navigate().get(-1).value()).isEqualTo(
      Value.errorMessage("$: index -1 is out of range for an array of 1 element (line 1, column 1)", Object.class)
    );
  }

  @Test
  @DisplayName("a member name that is not an identifier is bracketed and quoted in the path")
  void aNonIdentifierNameIsBracketedInThePath() {
    var document = Value.parseJson("{\"a b\":{},\"_x1\":[],\"\":1}");

    assertThat(document.navigate().get("a b").get("x").value()).isEqualTo(
      Value.errorMessage("$[\"a b\"]: member 'x' is missing (line 1, column 8)", Object.class)
    );
    assertThat(document.navigate().get("_x1").get(0).value()).isEqualTo(
      Value.errorMessage("$._x1: index 0 is out of range for an empty array (line 1, column 17)", Object.class)
    );
    assertThat(document.navigate().get("").get(0).value()).isEqualTo(
      Value.errorMessage("$[\"\"]: cannot step to element 0 of a number (line 1, column 23)", Object.class)
    );
  }

  @Test
  @DisplayName("location follows the value a step leaves from: a parsed member of a built document is located")
  void locationFollowsTheValueTheStepLeavesFrom() {
    Value<JsonValue> built = Value.jsonOf(
      Map.of("parsed", Value.parseJson("{\"x\":1}"), "made", Value.jsonOf(List.of()))
    );

    assertThat(built.navigate().get("parsed").get("y").value()).isEqualTo(
      Value.errorMessage("$.parsed: member 'y' is missing (line 1, column 1)", Object.class)
    );
    assertThat(built.navigate().get("made").get(0).value()).isEqualTo(
      Value.errorMessage("$.made: index 0 is out of range for an empty array", Object.class)
    );
  }

  @Test
  @DisplayName("a reached JSON null exits as the null marker, and converts as the null marker")
  void aReachedNullExitsAsTheNullMarker() {
    var document = Value.parseJson("{\"a\":null}");

    assertThat(document.navigate().get("a").value()).isNullValue();
    assertThat(document.navigate().get("a").convertTo(Integer.class)).isNullValue();
  }

  @Test
  @DisplayName("a failure converts re-typed to the target, its message kept")
  void aFailureConvertsReTyped() {
    var document = Value.parseJson("{}");

    assertThat(document.navigate().get("a").convertTo(String.class)).isEqualTo(
      Value.errorMessage("$: member 'a' is missing (line 1, column 1)", String.class)
    );
  }

  @Test
  @DisplayName("a null member name is a caller bug and throws, even after a failure")
  void aNullMemberNameThrows() {
    JsonNavigator root = ((JsonDocumentValue) Value.parseJson("{}")).navigate();
    JsonNavigator failed = root.get("missing");

    assertThatThrownBy(() -> root.get(null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Member name must not be null");
    assertThatThrownBy(() -> failed.get(null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Member name must not be null");
  }

  @Test
  @DisplayName("a null conversion target is a caller bug and throws")
  void aNullTargetThrows() {
    JsonNavigator root = ((JsonDocumentValue) Value.parseJson("{}")).navigate();

    assertThatThrownBy(() -> root.convertTo(null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Target class must not be null");
  }
}
