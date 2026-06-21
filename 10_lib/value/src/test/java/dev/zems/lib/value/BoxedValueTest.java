package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contract test for {@link BoxedValue} — pins the compact-constructor null rejection, the inner-reference round-trip,
 * and the {@code valueType()} class-cache reuse.
 *
 * <p>
 * The end-to-end journey (`Value.of(record) → unbox → marshal → read → unbox again`) is exercised by
 * {@code RecursiveValueRoundTripTest.listOfPoints()} and the related cases in that file — no separate journey test
 * is added here.
 */
@ContractTest
@DisplayName("BoxedValue")
class BoxedValueTest {

  record Point(int x, int y) {}

  @Test
  @DisplayName("compact constructor rejects null inner with NPE")
  void rejectsNullInner() {
    assertThatThrownBy(() -> new BoxedValue<>(null))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("inner must not be null");
  }

  @Test
  @DisplayName("inner() returns the same reference handed to the constructor")
  void innerRoundTrips() {
    var point = new Point(1, 2);
    var boxed = new BoxedValue<>(point);
    assertThat(boxed.inner()).isSameAs(point);
  }

  @Test
  @DisplayName("valueType() is cached per-class: two BoxedValues over the same type share the descriptor identity")
  void valueTypeCachedPerClass() {
    var first = new BoxedValue<>(new Point(1, 2));
    var second = new BoxedValue<>(new Point(3, 4));
    assertThat(first.valueType()).isSameAs(second.valueType());
  }
}
