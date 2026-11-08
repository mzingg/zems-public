package dev.zems.lib.value;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ContractTest
@DisplayName("IdentityNavigator — the navigator of a value with nothing to step into")
class IdentityNavigatorTest {

  static Stream<MarkerCase> stateMarkers() {
    return Stream.of(
      new MarkerCase("null", Value.nullValue()),
      new MarkerCase("undefined", Value.undefined()),
      new MarkerCase("unresolved", Value.unresolved()),
      new MarkerCase("tombstone", Value.tombstone()),
      new MarkerCase("error", Value.errorMessage("boom", Integer.class)),
      new MarkerCase("unsupported", Value.unsupported("not here", Integer.class))
    );
  }

  @Test
  @DisplayName("the exit answers the value itself, and convertTo converts it as the value would")
  void theExitAnswersTheValueItself() {
    Value<BigDecimal> amount = Value.bigDecimalOf("12.50");

    Navigator navigator = amount.navigate();

    assertThat(navigator.value()).isSameAs(amount);
    assertThat(navigator.convertTo(String.class)).isEqualTo(Value.of("12.50"));
    assertThat(navigator.convertTo(Integer.class)).isEqualTo(amount.convertTo(Integer.class));
  }

  @Test
  @DisplayName("a step by name or by index answers an UnsupportedValue naming the value's type as not navigable")
  void aStepAnswersAnUnsupportedValue() {
    Value<String> text = Value.of("x");

    assertThat(text.navigate().get("length").value()).isEqualTo(
      Value.unsupported("StringValue is not navigable", Object.class)
    );
    assertThat(text.navigate().get(0).value()).isEqualTo(
      Value.unsupported("StringValue is not navigable", Object.class)
    );
    assertThat(text.navigate().get(-1).value()).isEqualTo(
      Value.unsupported("StringValue is not navigable", Object.class)
    );
  }

  @Test
  @DisplayName("after the first step the unsupported value passes every later step and converts re-typed")
  void theFirstRefusalPassesEveryLaterStep() {
    Navigator refused = Value.of(42).navigate().get("a");

    assertThat(refused.get("b").get(3).get("c").value()).isSameAs(refused.value());
    assertThat(refused.convertTo(Long.class)).isEqualTo(Value.unsupported("IntegerValue is not navigable", Long.class));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("stateMarkers")
  @DisplayName("a state marker passes every step and value() by identity")
  void aStateMarkerPassesEveryStep(MarkerCase markerCase) {
    Navigator navigator = markerCase.marker().navigate();

    assertThat(navigator.get("a").get(0).get(-1).value()).isSameAs(markerCase.marker());
  }

  @Test
  @DisplayName("convertTo re-types an error to the target and keeps its throwable")
  void convertToReTypesAnErrorAndKeepsItsThrowable() {
    Value<Integer> failed = Value.errorOf(new IllegalStateException("boom"), Integer.class);

    Value<String> converted = failed.navigate().get("a").convertTo(String.class);

    assertThat(converted).isEqualTo(Value.errorMessage("boom", String.class));
    assertThat(converted.error()).containsSame(failed.error().orElseThrow());
  }

  @Test
  @DisplayName("a null member name is a caller bug and throws, even on a state marker")
  void aNullMemberNameThrows() {
    Navigator present = Value.of(42).navigate();
    Navigator marker = Value.nullValue().navigate();

    assertThatThrownBy(() -> present.get(null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Member name must not be null");
    assertThatThrownBy(() -> marker.get(null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Member name must not be null");
  }

  @Test
  @DisplayName("a null conversion target is a caller bug and throws")
  void aNullTargetThrows() {
    Navigator navigator = Value.of(42).navigate();

    assertThatThrownBy(() -> navigator.convertTo(null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Target class must not be null");
  }

  record MarkerCase(String label, Value<?> marker) {
    @Override
    public String toString() {
      return label;
    }
  }
}
