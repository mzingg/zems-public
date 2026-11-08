package dev.zems.lib.value.convert;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.fixture.SlotBag;
import dev.zems.lib.value.fixture.SlotFixtures.InstanceSlots;
import dev.zems.lib.value.fixture.SlotFixtures.MisdeclaredSlots;
import dev.zems.lib.value.fixture.SlotFixtures.NullSlots;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("SlotShapes")
class SlotShapesTest {

  @Test
  @DisplayName("a SLOTS field whose type is not a SlotShape is a programming bug and throws")
  void misdeclaredSlotsFieldThrows() {
    Value<Person> source = Value.of(new Person("Ada"));

    assertThatThrownBy(() -> Converter.standard().convert(source, MisdeclaredSlots.class))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage(
        "dev.zems.lib.value.fixture.SlotFixtures$MisdeclaredSlots.SLOTS is declared as java.lang.String " +
          "which is not a SlotShape — declare it as SlotShape or rename the field"
      );
  }

  @Test
  @DisplayName("a SLOTS constant holding null is a programming bug and throws")
  void slotsHoldingNullThrows() {
    Value<Person> source = Value.of(new Person("Ada"));

    assertThatThrownBy(() -> Converter.standard().convert(source, NullSlots.class))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("dev.zems.lib.value.fixture.SlotFixtures$NullSlots.SLOTS holds null");
  }

  @Test
  @DisplayName("an instance field named SLOTS is no declaration — the pair has no conversion")
  void instanceSlotsFieldIsNoDeclaration() {
    Value<InstanceSlots> converted = Converter.standard().convert(Value.of(new Person("Ada")), InstanceSlots.class);

    assertThat(converted)
      .hasUnsupportedReason("Cannot convert Person to InstanceSlots: no conversion exists")
      .hasExpectedTypeName("dev.zems.lib.value.fixture.SlotFixtures$InstanceSlots");
  }

  @Test
  @DisplayName("a subtype named as target does not pick up the shape its supertype declares")
  void subtypeTargetDoesNotPickUpTheSupertypeShape() {
    Value<LabelledSlotBag> converted = Converter.standard().convert(Value.of(new Person("Ada")), LabelledSlotBag.class);

    assertThat(converted)
      .hasUnsupportedReason("Cannot convert Person to LabelledSlotBag: no conversion exists")
      .hasExpectedTypeName("dev.zems.lib.value.convert.SlotShapesTest$LabelledSlotBag");
  }

  record Person(String name) {}

  static class LabelledSlotBag extends SlotBag {

    LabelledSlotBag() {
      super(SlotBag.empty().slots());
    }
  }
}
