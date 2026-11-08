package dev.zems.lib.value.json.engine.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.json.engine.Json;
import dev.zems.lib.value.json.engine.JsonArray;
import dev.zems.lib.value.json.engine.JsonString;
import dev.zems.lib.value.json.engine.JsonValue;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("JsonLocations — renders where a value sits, in the words of the parse errors")
class JsonLocationsTest {

  private static final Duration AT_ONCE = Duration.ofSeconds(1);

  @Test
  @DisplayName("a path renders $, then .name for an identifier, [\"name\"] otherwise, and [i] per element")
  void aPathRendersEachStep() {
    assertThat(JsonLocations.path(List.of())).isEqualTo("$");
    assertThat(JsonLocations.path(List.of("threads", 0, "_tid2"))).isEqualTo("$.threads[0]._tid2");
    assertThat(JsonLocations.path(List.of("a b", "", "2x", 3))).isEqualTo("$[\"a b\"][\"\"][\"2x\"][3]");
    assertThat(JsonLocations.path(List.of("say \"hi\""))).isEqualTo("$[\"say \\\"hi\\\"\"]");
  }

  @Test
  @DisplayName("up to 64 steps render in full; more are elided to the first and the last 32")
  void moreThanSixtyFourStepsAreElided() {
    List<Object> sixtyFour = new ArrayList<>(Collections.nCopies(64, "a"));
    List<Object> sixtyFive = new ArrayList<>(Collections.nCopies(32, "a"));
    sixtyFive.add(7);
    sixtyFive.addAll(Collections.nCopies(32, "b"));

    assertThat(JsonLocations.path(sixtyFour)).isEqualTo("$" + ".a".repeat(64));
    assertThat(JsonLocations.path(sixtyFive)).isEqualTo("$" + ".a".repeat(32) + "…" + ".b".repeat(32));
  }

  @Test
  @DisplayName("a large index and a long path render at once")
  void largeIndicesAndLongPathsRenderAtOnce() {
    List<Object> deep = new ArrayList<>(Collections.nCopies(1_000_000, 0));

    assertTimeoutPreemptively(AT_ONCE, () -> {
      assertThat(JsonLocations.path(List.of(100_000))).isEqualTo("$[100000]");
      assertThat(JsonLocations.path(deep)).isEqualTo("$" + "[0]".repeat(32) + "…" + "[0]".repeat(32));
    });
  }

  @Test
  @DisplayName("a parsed value answers its one-based line and column; a built value answers none")
  void parsedValuesAnswerTheirLineAndColumn() {
    JsonValue document = Json.parse("{\n  \"a\": [true,\n    null]\n}");
    JsonValue array = document.get("a");

    assertThat(JsonLocations.lineAndColumn(document)).hasValue("line 1, column 1");
    assertThat(JsonLocations.lineAndColumn(array)).hasValue("line 2, column 8");
    assertThat(JsonLocations.lineAndColumn(array.get(1))).hasValue("line 3, column 5");
    assertThat(JsonLocations.lineAndColumn(JsonString.of("x"))).isEmpty();
    assertThat(JsonLocations.lineAndColumn(JsonArray.of(List.of(array)))).isEmpty();
  }
}
