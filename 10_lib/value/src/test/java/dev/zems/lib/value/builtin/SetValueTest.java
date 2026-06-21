package dev.zems.lib.value.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.Value;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contract test for {@link SetValue} — the immutable set-of-Values wrapper. Pins down the compact-constructor
 * validation, internal-caller enforcement, and element-copy/dedupe behaviour that distinguish {@code SetValue} from raw
 * {@code Set<Value<?>>}.
 */
@ContractTest
@DisplayName("SetValue")
class SetValueTest {

  @Test
  @DisplayName("Value.setOf produces a SetValue with the expected elements")
  void setOfProducesSetValue() {
    Value<Set<Value<String>>> result = Value.setOf("a", "b");
    assertThat(result).isInstanceOf(SetValue.class);
    assertThat(Value.unbox(result)).containsExactlyInAnyOrder(Value.of("a"), Value.of("b"));
  }

  @Test
  @DisplayName("Value.setOf with an empty varargs returns an empty SetValue")
  void setOfEmpty() {
    Value<Set<Value<String>>> result = Value.setOf();
    assertThat(result).isInstanceOf(SetValue.class);
    assertThat(Value.unbox(result)).isEmpty();
  }

  @Test
  @DisplayName("Value.setOf dedupes equal raw elements (Value equality on copy)")
  void setOfDedupes() {
    Value<Set<Value<String>>> result = Value.setOf("a", "a");
    assertThat(Value.unbox(result)).hasSize(1).containsExactly(Value.of("a"));
  }

  @Test
  @DisplayName("setOf wraps a LinkedHashSet preserving element membership")
  void wrapsLinkedHashSet() {
    var src = new LinkedHashSet<Value<String>>();
    src.add(Value.of("c"));
    src.add(Value.of("a"));
    src.add(Value.of("b"));
    Value<Set<Value<String>>> result = Value.setOf(src);
    assertThat(Value.unbox(result)).containsExactlyInAnyOrder(Value.of("c"), Value.of("a"), Value.of("b"));
  }

  @Test
  @DisplayName("setOf rejects a pre-wrapped set containing a null element")
  void rejectsNullElementInPreWrapped() {
    var withNull = new HashSet<Value<String>>();
    withNull.add(Value.of("a"));
    withNull.add(null);
    Value<Set<Value<String>>> result = Value.setOf(withNull);
    assertThat(result).isInstanceOfAny(dev.zems.lib.value.ErrorValue.class);
  }

  @Test
  @DisplayName("Value.isSet / Value.asSet recognise SetValue")
  void isSetAndAsSet() {
    Value<Set<Value<String>>> v = Value.setOf("a");
    assertThat(v.isSet()).isTrue();
    assertThat(v.isList()).isFalse();
    assertThat(v.isMap()).isFalse();
    assertThat(v.asSet()).isPresent();
    assertThat(v.asSet())
      .get()
      .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.iterable(Value.class))
      .containsExactly(Value.of("a"));
  }

  @Test
  @DisplayName("Value.isSetOf returns true when every element wraps the given type")
  void isSetOfMatchesElementType() {
    Value<Set<Value<String>>> v = Value.setOf("a", "b");
    assertThat(v.isSetOf(String.class)).isTrue();
    assertThat(v.isSetOf(Integer.class)).isFalse();
  }
}
