package dev.zems.lib.value.builtin;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.Units;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("MeasurableValue")
class MeasurableValueTest {

  @Test
  @DisplayName("reports QUANTITY as its value type, for a physical quantity and for money alike")
  void valueTypeIsQuantity() {
    assertThat(Value.of(5, Units.METRE).valueType()).isSameAs(BuiltinTypeDescriptors.QUANTITY);
    assertThat(Value.of(new BigDecimal("9.99"), Currency.getInstance("EUR")).valueType()).isSameAs(
      BuiltinTypeDescriptors.QUANTITY
    );
  }

  @Test
  @DisplayName("convertTo(String) is refused as an UnsupportedValue: no conversion exists yet")
  void convertToStringIsUnsupported() {
    assertThat(Value.of(5, Units.METRE).convertTo(String.class))
      .isUnsupported()
      .hasUnsupportedReason("Cannot convert Quantity to String: no conversion exists")
      .hasExpectedTypeName("java.lang.String");
  }

  @Test
  @DisplayName("a null quantity is refused by the constructor")
  void aNullQuantityIsRefused() {
    assertThatThrownBy(() -> new MeasurableValue(null))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("Quantity must not be null");
  }
}
