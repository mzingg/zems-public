package dev.zems.lib.value.marshal.descriptor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.JourneyTest;
import dev.zems.lib.value.builtin.StringValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The no-nesting rule holds on every descriptor path. {@link StringValue} is a built-in record that implements
 * {@code Value}, so before this guard the record-synthesis path quietly built a descriptor for it; now both direct
 * construction and discovery reject it with the same guidance error the scalar path already gave.
 */
@DisplayName("Value-nesting guard across descriptor paths")
class ValueNestingGuardJourneyTest {

  @Test
  @JourneyTest(speakingId = "record-synthesis-value-nesting-guard", acceptance = "guard")
  @DisplayName("of() and find() both reject a Value type with the same guidance error")
  void rejectsValueTypeOnEveryPath() {
    assertThatThrownBy(() -> TypeDescriptor.of(StringValue.class))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Cannot describe Value types - use the Value directly");
    assertThatThrownBy(() -> TypeDescriptor.find(StringValue.class))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Cannot describe Value types - use the Value directly");
  }
}
