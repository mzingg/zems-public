package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("Dimension")
class DimensionTest {

  @Test
  @DisplayName("NONE has exponent 0 for every base dimension")
  void noneIsAllZero() {
    for (BaseDimension base : BaseDimension.values()) {
      assertThat(Dimension.NONE.exponent(base)).as(base.name()).isZero();
    }
  }

  @Test
  @DisplayName("of(base) has exponent 1 for that base only")
  void ofHasExponentOneForItsBase() {
    Dimension length = Dimension.of(BaseDimension.LENGTH);

    assertThat(length.exponent(BaseDimension.LENGTH)).isEqualTo(1);
    assertThat(length.exponent(BaseDimension.TIME)).isZero();
    assertThat(length).isNotEqualTo(Dimension.of(BaseDimension.TIME));
  }

  @Test
  @DisplayName("with adds to the base's exponent")
  void withAdds() {
    Dimension acceleration = Dimension.of(BaseDimension.LENGTH).with(BaseDimension.TIME, -2);

    assertThat(acceleration.exponent(BaseDimension.LENGTH)).isEqualTo(1);
    assertThat(acceleration.exponent(BaseDimension.TIME)).isEqualTo(-2);
    assertThat(acceleration.with(BaseDimension.LENGTH, 2).exponent(BaseDimension.LENGTH)).isEqualTo(3);
    assertThat(acceleration.with(BaseDimension.TIME, 2)).isEqualTo(Dimension.of(BaseDimension.LENGTH));
  }

  @Test
  @DisplayName("multiply adds the exponents base by base")
  void multiplyAddsExponents() {
    Dimension velocity = Dimension.of(BaseDimension.LENGTH).with(BaseDimension.TIME, -1);
    Dimension time = Dimension.of(BaseDimension.TIME);

    assertThat(velocity.multiply(time)).isEqualTo(Dimension.of(BaseDimension.LENGTH));
    assertThat(velocity.multiply(velocity)).isEqualTo(
      Dimension.NONE.with(BaseDimension.LENGTH, 2).with(BaseDimension.TIME, -2)
    );
  }

  @Test
  @DisplayName("pow multiplies every exponent")
  void powMultipliesExponents() {
    Dimension velocity = Dimension.of(BaseDimension.LENGTH).with(BaseDimension.TIME, -1);

    assertThat(velocity.pow(3)).isEqualTo(Dimension.NONE.with(BaseDimension.LENGTH, 3).with(BaseDimension.TIME, -3));
    assertThat(velocity.pow(-1)).isEqualTo(Dimension.NONE.with(BaseDimension.LENGTH, -1).with(BaseDimension.TIME, 1));
    assertThat(Dimension.NONE.pow(5)).isEqualTo(Dimension.NONE);
  }

  @Test
  @DisplayName("equal exponents are an equal dimension with an equal hash, however they were built")
  void equalExponentsAreEqual() {
    Dimension built = Dimension.of(BaseDimension.MASS).with(BaseDimension.LENGTH, 1).with(BaseDimension.TIME, -2);
    Dimension multiplied = Dimension.of(BaseDimension.TIME)
      .pow(-2)
      .multiply(Dimension.of(BaseDimension.LENGTH))
      .multiply(Dimension.of(BaseDimension.MASS));

    assertThat(multiplied).isEqualTo(built).hasSameHashCodeAs(built);
  }

  @Test
  @DisplayName("an exponent overflowing int throws")
  void anOverflowingExponentThrows() {
    Dimension huge = Dimension.NONE.with(BaseDimension.LENGTH, Integer.MAX_VALUE);

    assertThatThrownBy(() -> huge.with(BaseDimension.LENGTH, 1))
      .isInstanceOf(ArithmeticException.class)
      .hasMessage("integer overflow");
    assertThatThrownBy(() -> huge.multiply(Dimension.of(BaseDimension.LENGTH)))
      .isInstanceOf(ArithmeticException.class)
      .hasMessage("integer overflow");
    assertThatThrownBy(() -> huge.pow(2))
      .isInstanceOf(ArithmeticException.class)
      .hasMessage("integer overflow");
  }
}
