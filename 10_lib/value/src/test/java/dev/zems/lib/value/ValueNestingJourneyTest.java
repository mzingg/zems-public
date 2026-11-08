package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.JourneyTest;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("No Value nesting — BoxedValue and descriptor building refuse a Value inside a value")
class ValueNestingJourneyTest {

  @Test
  @JourneyTest(speakingId = "instance-unbox-on-value", acceptance = "boxnest")
  @DisplayName("constructing a BoxedValue around a Value throws IAE telling the caller to use the Value directly")
  void boxedValueRejectsValueInner() {
    Value<String> inner = Value.of("x");

    assertThatThrownBy(() -> new BoxedValue<>(inner))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Cannot box a Value (StringValue) - use the Value directly");
  }

  @Test
  @JourneyTest(speakingId = "instance-unbox-on-value", acceptance = "boxnest")
  @DisplayName("Value.of on a Value answers that same Value instead of boxing it")
  void valueOfOnValueAnswersSameInstance() {
    Value<String> inner = Value.of("x");

    Value<Object> outer = Value.of((Object) inner);

    assertThat(outer).isSameAs(inner);
  }

  @Test
  @JourneyTest(speakingId = "instance-unbox-on-value", acceptance = "recordnest")
  @DisplayName("a record component typed Value<String> is rejected, naming the record and the component")
  void recordWithValueComponentRejected() {
    assertThatThrownBy(() -> TypeDescriptor.of(WithValue.class))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage(
        "Cannot describe " +
          WithValue.class.getName() +
          ": component 'name' is declared as dev.zems.lib.value.Value<java.lang.String>" +
          " - declare the plain type, not a Value"
      );
  }

  @Test
  @JourneyTest(speakingId = "instance-unbox-on-value", acceptance = "recordnest")
  @DisplayName("a record component typed List<Value<String>> is rejected, naming the record and the component")
  void recordWithValueListElementRejected() {
    assertThatThrownBy(() -> TypeDescriptor.of(WithValueList.class))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage(
        "Cannot describe " +
          WithValueList.class.getName() +
          ": component 'names' is declared as java.util.List<dev.zems.lib.value.Value<java.lang.String>>" +
          " - declare the plain type, not a Value"
      );
  }

  @Test
  @JourneyTest(speakingId = "instance-unbox-on-value", acceptance = "recordnest")
  @DisplayName("a record component typed Map<String, Value<?>> is rejected, naming the record and the component")
  void recordWithWildcardValueMapValueRejected() {
    assertThatThrownBy(() -> TypeDescriptor.of(WithValueMap.class))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage(
        "Cannot describe " +
          WithValueMap.class.getName() +
          ": component 'attrs' is declared as java.util.Map<java.lang.String, dev.zems.lib.value.Value<?>>" +
          " - declare the plain type, not a Value"
      );
  }

  public record WithValue(Value<String> name) {}

  public record WithValueList(List<Value<String>> names) {}

  public record WithValueMap(Map<String, Value<?>> attrs) {}
}
