package dev.zems.lib.value.convert;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.fixture.SlotBag;
import dev.zems.lib.value.fixture.SlotFixtures.RefusingBag;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ContractTest
@DisplayName("StructuralConversion")
class StructuralConversionTest {

  private static final String PERSON_NAME = "dev.zems.lib.value.convert.StructuralConversionTest$Person";

  static Stream<Arguments> absentSlots() {
    return Stream.of(Arguments.of("undefined", Value.undefined()), Arguments.of("tombstone", Value.tombstone()));
  }

  static Stream<Arguments> refusedSlots() {
    return Stream.of(
      Arguments.of("error", Value.errorMessage("age was never recorded", Integer.class), "age was never recorded"),
      Arguments.of("unsupported", Value.unsupported("age is not offered", Integer.class), "age is not offered")
    );
  }

  static Stream<Arguments> wideComponentsHoldingACollection() {
    return Stream.of(
      Arguments.of("type variable", new Holder<>(new ArrayList<>(List.of("a"))), "Holder", "Object"),
      Arguments.of("Object", new Loose(new ArrayList<>(List.of("a"))), "Loose", "Object"),
      Arguments.of("Serializable", new Portable(new ArrayList<>(List.of("a"))), "Portable", "Serializable")
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("absentSlots")
  @DisplayName("an undefined or tombstoned slot counts as missing")
  void absentSlotStatesCountAsMissing(String label, Value<Integer> absent) {
    SlotBag bag = SlotBag.empty().with("name", Value.of("Ada")).with("age", absent);

    Value<Person> converted = Converter.standard().convert(Value.of(bag), Person.class);

    assertThat(converted)
      .hasErrorMessage("Cannot convert SlotBag to Person: slot 'age' for component 'age' is missing")
      .hasExpectedTypeName(PERSON_NAME);
  }

  @Test
  @DisplayName("a null slot fills a reference component with null")
  void nullSlotFillsAReferenceComponentWithNull() {
    SlotBag bag = SlotBag.empty().with("name", Value.nullValue()).with("age", Value.of(36));

    Value<Person> converted = Converter.standard().convert(Value.of(bag), Person.class);

    assertThat(converted).isEqualTo(Value.of(new Person(null, 36)));
  }

  @Test
  @DisplayName("a null slot cannot fill a primitive component")
  void nullSlotCannotFillAPrimitiveComponent() {
    SlotBag bag = SlotBag.empty().with("name", Value.of("Ada")).with("age", Value.nullValue());

    Value<Person> converted = Converter.standard().convert(Value.of(bag), Person.class);

    assertThat(converted)
      .hasErrorMessage(
        "Cannot convert SlotBag to Person: slot 'age' cannot fill component 'age': null cannot fill a primitive component"
      )
      .hasExpectedTypeName(PERSON_NAME);
  }

  @Test
  @DisplayName("an unresolved slot cannot fill a component")
  void unresolvedSlotCannotFillAComponent() {
    SlotBag bag = SlotBag.empty().with("name", Value.of("Ada")).with("age", Value.unresolved());

    Value<Person> converted = Converter.standard().convert(Value.of(bag), Person.class);

    assertThat(converted)
      .hasErrorMessage(
        "Cannot convert SlotBag to Person: slot 'age' cannot fill component 'age': the slot is unresolved"
      )
      .hasExpectedTypeName(PERSON_NAME);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("refusedSlots")
  @DisplayName("an error or unsupported marker in a slot is a mismatch carrying the marker's own reason")
  void markerInASlotIsAMismatchCarryingItsReason(String label, Value<Integer> marker, String reason) {
    SlotBag bag = SlotBag.empty().with("name", Value.of("Ada")).with("age", marker);

    Value<Person> converted = Converter.standard().convert(Value.of(bag), Person.class);

    assertThat(converted)
      .hasErrorMessage("Cannot convert SlotBag to Person: slot 'age' cannot fill component 'age': " + reason)
      .hasExpectedTypeName(PERSON_NAME);
  }

  @Test
  @DisplayName("a lossy fill keeps the inner refusal as the cause")
  void lossyFillKeepsTheInnerRefusalAsTheCause() {
    SlotBag bag = SlotBag.empty().with("name", Value.of("Ada")).with("age", Value.of(3.7d));

    Value<Person> converted = Converter.standard().convert(Value.of(bag), Person.class);

    assertThat(converted)
      .extractingThrowable()
      .cause()
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Cannot convert Double to Integer: 3.7 is not a whole number");
  }

  @Test
  @DisplayName("components fill in declaration order and the first failing one wins")
  void firstFailingComponentInDeclarationOrderWins() {
    SlotBag bag = SlotBag.empty().with("nick", Value.of("Countess"));

    Value<Person> converted = Converter.standard().convert(Value.of(bag), Person.class);

    assertThat(converted).hasErrorMessage(
      "Cannot convert SlotBag to Person: slot 'name' for component 'name' is missing"
    );
  }

  @Test
  @DisplayName("a List component refuses the conversion in both directions, before any slot is read")
  void listComponentRefusesBothDirections() {
    SlotBag bag = SlotBag.empty().with("name", Value.of("Ada")).with("tags", Value.listOf("x"));

    Value<Tagged> toRecord = Converter.standard().convert(Value.of(bag), Tagged.class);
    Value<SlotBag> toStructure = Converter.standard().convert(Value.of(new Tagged("Ada", List.of("x"))), SlotBag.class);

    assertThat(toRecord)
      .hasUnsupportedReason(
        "Cannot convert SlotBag to Tagged: component 'tags' has type List, which structural conversion does not support"
      )
      .hasExpectedTypeName("dev.zems.lib.value.convert.StructuralConversionTest$Tagged");
    assertThat(toStructure)
      .hasUnsupportedReason(
        "Cannot convert Tagged to SlotBag: component 'tags' has type List, which structural conversion does not support"
      )
      .hasExpectedTypeName("dev.zems.lib.value.fixture.SlotBag");
  }

  @Test
  @DisplayName("a Value-typed component refuses the conversion")
  void valueTypedComponentRefuses() {
    SlotBag bag = SlotBag.empty().with("inner", Value.of("Ada"));

    Value<Wrapped> converted = Converter.standard().convert(Value.of(bag), Wrapped.class);

    assertThat(converted).hasUnsupportedReason(
      "Cannot convert SlotBag to Wrapped: component 'inner' has type Value, which structural conversion does not support"
    );
  }

  @Test
  @DisplayName("a Collection component refuses the conversion before any slot is read")
  void collectionComponentRefusesStatically() {
    SlotBag bag = SlotBag.empty();

    Value<Bundle> converted = Converter.standard().convert(Value.of(bag), Bundle.class);

    assertThat(converted).hasUnsupportedReason(
      "Cannot convert SlotBag to Bundle: component 'items' has type Collection, which structural conversion does not support"
    );
  }

  @Test
  @DisplayName("a type-variable component holding a scalar converts both ways")
  void typeVariableComponentHoldingAScalarConvertsBothWays() {
    SlotBag bag = SlotBag.empty().with("value", Value.of("Ada"));

    var toRecord = Converter.standard().convert(Value.of(bag), Holder.class);
    Value<SlotBag> toStructure = Converter.standard().convert(Value.of(new Holder<>("Ada")), SlotBag.class);

    assertThat(toRecord).isEqualTo(Value.of(new Holder<>("Ada")));
    assertThat(toStructure).isPresent();
    Assertions.assertThat(toStructure.unbox().slots()).containsExactly(entry("value", Value.of("Ada")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("wideComponentsHoldingACollection")
  @DisplayName("a wide component holding a collection refuses record to structure instead of throwing")
  void wideComponentHoldingACollectionRefusesToStructure(
    String label,
    Record holder,
    String recordName,
    String componentType
  ) {
    Value<SlotBag> converted = Converter.standard().convert(Value.of(holder), SlotBag.class);

    assertThat(converted).hasUnsupportedReason(
      "Cannot convert " +
        recordName +
        " to SlotBag: component 'value' has type " +
        componentType +
        ", which structural conversion does not support"
    );
  }

  @Test
  @DisplayName("a wide component filled from a list slot refuses structure to record")
  void wideComponentFilledFromAListSlotRefusesToRecord() {
    SlotBag bag = SlotBag.empty().with("value", Value.listOf("a"));

    Value<Loose> converted = Converter.standard().convert(Value.of(bag), Loose.class);

    assertThat(converted).hasUnsupportedReason(
      "Cannot convert SlotBag to Loose: component 'value' has type Object, which structural conversion does not support"
    );
  }

  @Test
  @DisplayName("a canonical constructor that throws answers an error with the exception as cause")
  void throwingConstructorAnswersAnErrorWithTheCause() {
    SlotBag bag = SlotBag.empty().with("name", Value.of("Tom")).with("age", Value.of(12));

    Value<Adult> converted = Converter.standard().convert(Value.of(bag), Adult.class);

    assertThat(converted)
      .hasErrorMessage("Cannot convert SlotBag to Adult: an adult is at least 18")
      .hasExpectedTypeName("dev.zems.lib.value.convert.StructuralConversionTest$Adult");
    assertThat(converted)
      .extractingThrowable()
      .cause()
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("an adult is at least 18");
  }

  @Test
  @DisplayName("a constructor exception without a message is named by its simple class name")
  void messagelessConstructorExceptionIsNamedByItsClass() {
    SlotBag bag = SlotBag.empty().with("count", Value.of(-1));

    Value<Counter> converted = Converter.standard().convert(Value.of(bag), Counter.class);

    assertThat(converted).hasErrorMessage("Cannot convert SlotBag to Counter: IllegalStateException");
  }

  @Test
  @DisplayName("an error from the structure's build is reframed under the record and the target")
  void buildErrorIsReframed() {
    Value<RefusingBag> converted = Converter.standard().convert(Value.of(new Person("Ada", 36)), RefusingBag.class);

    assertThat(converted)
      .hasErrorMessage("Cannot convert Person to RefusingBag: a refusing bag accepts no slots")
      .hasExpectedTypeName("dev.zems.lib.value.fixture.SlotFixtures$RefusingBag");
  }

  @Test
  @DisplayName("a payload subclass is named by its shape's type in the refusal, not by its runtime class")
  void payloadSubclassIsNamedByItsShapeType() {
    LabelledSlotBag bag = new LabelledSlotBag(SlotBag.empty().with("name", Value.of("Ada")));

    Value<Person> converted = Converter.standard().convert(Value.of(bag), Person.class);

    assertThat(converted).hasErrorMessage(
      "Cannot convert SlotBag to Person: slot 'age' for component 'age' is missing"
    );
  }

  record Person(String name, int age) {}

  record Tagged(String name, List<String> tags) {}

  record Wrapped(Value<String> inner) {}

  record Bundle(Collection<String> items) {}

  record Holder<T>(T value) {}

  record Loose(Object value) {}

  record Portable(Serializable value) {}

  record Adult(String name, int age) {
    Adult {
      if (age < 18) {
        throw new IllegalArgumentException("an adult is at least 18");
      }
    }
  }

  record Counter(int count) {
    Counter {
      if (count < 0) {
        throw new IllegalStateException();
      }
    }
  }

  static class LabelledSlotBag extends SlotBag {

    LabelledSlotBag(SlotBag source) {
      super(source.slots());
    }
  }
}
