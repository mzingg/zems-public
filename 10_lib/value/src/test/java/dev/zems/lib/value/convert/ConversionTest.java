package dev.zems.lib.value.convert;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("Conversion")
class ConversionTest {

  @Test
  @DisplayName("of(...) names the two types it connects and runs the body it was given")
  void ofNamesItsTypesAndRunsItsBody() {
    Conversion<Double, Celsius> fromKelvin = Conversion.of(Double.class, Celsius.class, kelvin ->
      Value.of(new Celsius(kelvin - 273.15d))
    );

    assertThat(fromKelvin.sourceType()).isEqualTo(Double.class);
    assertThat(fromKelvin.targetType()).isEqualTo(Celsius.class);
    assertThat(fromKelvin.convert(300.0d)).isEqualTo(Value.of(new Celsius(300.0d - 273.15d)));
  }

  @Test
  @DisplayName("a body may refuse one payload with its own reason, and that refusal is passed on untouched")
  void aBodyMayRefuseWithItsOwnReason() {
    Conversion<Double, Celsius> fromKelvin = Conversion.of(Double.class, Celsius.class, kelvin ->
      kelvin < 0.0d ? Value.errorMessage("below absolute zero", Celsius.class) : Value.of(new Celsius(kelvin - 273.15d))
    );

    assertThat(fromKelvin.convert(-1.0d))
      .hasErrorMessage("below absolute zero")
      .hasExpectedTypeName("dev.zems.lib.value.convert.ConversionTest$Celsius");
  }

  @Test
  @DisplayName("of(...) rejects each missing argument where it is built")
  void ofRejectsNullArguments() {
    assertThatThrownBy(() ->
      Conversion.of((Class<Double>) null, Celsius.class, kelvin -> Value.of(new Celsius(kelvin)))
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Conversion source type must not be null");
    assertThatThrownBy(() ->
      Conversion.of(Double.class, (Class<Celsius>) null, kelvin -> Value.of(new Celsius(kelvin)))
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Conversion target type must not be null");
    assertThatThrownBy(() -> Conversion.of(Double.class, Celsius.class, null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Conversion body must not be null");
  }

  @Test
  @DisplayName("a conversion says which pair it covers when it is printed")
  void toStringNamesBothTypes() {
    Conversion<Double, Celsius> fromKelvin = Conversion.of(Double.class, Celsius.class, kelvin ->
      Value.of(new Celsius(kelvin - 273.15d))
    );

    assertThat(fromKelvin).hasToString(
      "Conversion[java.lang.Double -> dev.zems.lib.value.convert.ConversionTest$Celsius]"
    );
  }

  record Celsius(double degrees) {}
}
