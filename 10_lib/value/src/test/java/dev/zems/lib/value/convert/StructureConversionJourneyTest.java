package dev.zems.lib.value.convert;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import dev.zems.lib.common.junit.JourneyTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.fixture.SlotBag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Structure-object conversion — records to named-slot structures and back, under the one verb")
class StructureConversionJourneyTest {

  private static final String PERSON_NAME = "dev.zems.lib.value.convert.StructureConversionJourneyTest$Person";

  @Test
  @JourneyTest(speakingId = "structure-object-conversion", acceptance = "toobject")
  @DisplayName("a structure whose slots match a package-private record's components converts into that record")
  void structureConvertsIntoRecordThroughItsCanonicalConstructor() {
    SlotBag bag = SlotBag.empty().with("name", Value.of("Ada")).with("age", Value.of(36));

    Value<Person> person = Value.of(bag).convertTo(Person.class);

    assertThat(person)
      .isPresent()
      .isEqualTo(Value.of(new Person("Ada", 36)));
  }

  @Test
  @JourneyTest(speakingId = "structure-object-conversion", acceptance = "tostructure")
  @DisplayName("a record converts into the structure type the caller names, one slot per component in component order")
  void recordConvertsIntoTheNamedStructureType() {
    Value<SlotBag> bag = Value.of(new Person("Ada", 36)).convertTo(SlotBag.class);

    assertThat(bag).isPresent();
    Assertions.assertThat(bag.unbox().slots()).containsExactly(
      entry("name", Value.of("Ada")),
      entry("age", Value.of(36))
    );
  }

  @Test
  @JourneyTest(speakingId = "structure-object-conversion", acceptance = "roundtrip")
  @DisplayName("record to structure and back yields an equal record")
  void recordToStructureAndBackYieldsAnEqualRecord() {
    Person original = new Person("Ada", 36);

    Value<Person> back = Value.of(original).convertTo(SlotBag.class).convertTo(Person.class);

    assertThat(back).isPresent().isEqualTo(Value.of(original));
    Assertions.assertThat(back.unbox()).isEqualTo(original);
  }

  @Test
  @JourneyTest(speakingId = "structure-object-conversion", acceptance = "seam")
  @DisplayName("a structure declared outside the conversion package takes part through its declared constant alone")
  void structureOutsideTheConversionPackageParticipatesThroughItsConstant() {
    SlotBag bag = SlotBag.empty().with("name", Value.of("Grace")).with("age", Value.of(85));

    Value<Person> person = Value.of(bag).convertTo(Person.class);
    Value<SlotBag> structure = person.convertTo(SlotBag.class);

    Assertions.assertThat(SlotBag.class.getPackageName()).isEqualTo("dev.zems.lib.value.fixture");
    assertThat(person).isEqualTo(Value.of(new Person("Grace", 85)));
    assertThat(structure).isPresent();
    Assertions.assertThat(structure.unbox().slots()).containsExactly(
      entry("name", Value.of("Grace")),
      entry("age", Value.of(85))
    );
  }

  @Test
  @JourneyTest(speakingId = "structure-object-conversion", acceptance = "map")
  @DisplayName("a map-shaped structure with runtime slot names converts by name lookup in both directions")
  void mapShapedStructureConvertsByNameInBothDirections() {
    List<Map.Entry<String, Value<?>>> incoming = List.of(
      Map.entry("nick", Value.of("Countess")),
      Map.entry("age", Value.of(36)),
      Map.entry("name", Value.of("Ada"))
    );
    Map<String, Value<?>> runtimeSlots = new LinkedHashMap<>();
    for (Map.Entry<String, Value<?>> slot : incoming) {
      runtimeSlots.put(slot.getKey(), slot.getValue());
    }
    SlotBag bag = new SlotBag(runtimeSlots);

    Value<Person> person = Value.of(bag).convertTo(Person.class);
    Value<SlotBag> reverse = person.convertTo(SlotBag.class);

    assertThat(person).isEqualTo(Value.of(new Person("Ada", 36)));
    assertThat(reverse).isPresent();
    Assertions.assertThat(reverse.unbox().slots()).containsOnlyKeys("name", "age");
    Assertions.assertThat(reverse.unbox().slots()).containsExactly(
      entry("name", Value.of("Ada")),
      entry("age", Value.of(36))
    );
  }

  @Test
  @JourneyTest(speakingId = "structure-object-conversion", acceptance = "coerce")
  @DisplayName("component fills go through the scalar engine: lossless applies, lossy fails, a stated policy applies")
  void componentFillsFollowTheScalarEngineAndItsPolicies() {
    SlotBag intBalance = SlotBag.empty().with("owner", Value.of("Ada")).with("balance", Value.of(42));
    SlotBag fractionalAge = SlotBag.empty().with("name", Value.of("Ada")).with("age", Value.of(3.7d));

    Value<Account> widened = Value.of(intBalance).convertTo(Account.class);
    Value<Person> refused = Value.of(fractionalAge).convertTo(Person.class);
    Value<Person> rounded = Converter.standard()
      .with(Conversions.doubleToInt(Rounding.DOWN))
      .convert(Value.of(fractionalAge), Person.class);

    assertThat(widened).isEqualTo(Value.of(new Account("Ada", 42L)));
    assertThat(refused)
      .isError()
      .hasErrorMessage(
        "Cannot convert SlotBag to Person: slot 'age' cannot fill component 'age': " +
          "Cannot convert Double to Integer: 3.7 is not a whole number"
      );
    assertThat(rounded)
      .isPresent()
      .isEqualTo(Value.of(new Person("Ada", 3)));
    Assertions.assertThat(rounded.unbox().age()).isEqualTo(3);
  }

  @Test
  @JourneyTest(speakingId = "structure-object-conversion", acceptance = "fail")
  @DisplayName("a missing slot and a type mismatch are distinct structured failures naming target, component and slot")
  void missingSlotAndTypeMismatchAreToldApart() {
    SlotBag missingAge = SlotBag.empty().with("name", Value.of("Ada"));
    SlotBag fractionalAge = SlotBag.empty().with("name", Value.of("Ada")).with("age", Value.of(3.7d));

    Value<Person> missing = Value.of(missingAge).convertTo(Person.class);
    Value<Person> mismatch = Value.of(fractionalAge).convertTo(Person.class);

    assertThat(missing)
      .isError()
      .hasErrorMessage("Cannot convert SlotBag to Person: slot 'age' for component 'age' is missing")
      .hasExpectedTypeName(PERSON_NAME);
    assertThat(mismatch)
      .isError()
      .hasErrorMessage(
        "Cannot convert SlotBag to Person: slot 'age' cannot fill component 'age': " +
          "Cannot convert Double to Integer: 3.7 is not a whole number"
      )
      .hasExpectedTypeName(PERSON_NAME);
    assertThat(missing).isNotEqualTo(mismatch);
  }

  record Person(String name, int age) {}

  record Account(String owner, long balance) {}
}
