package dev.zems.lib.value.convert;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;

import dev.zems.lib.common.junit.JourneyTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.fixture.SlotBag;
import dev.zems.lib.value.fixture.Tripwires;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Bound dispatch — the type name a structure carries picks its class from the caller's bound set only")
class BoundDispatchJourneyTest {

  private static final String CIRCLE_NAME = "dev.zems.lib.value.convert.BoundDispatchJourneyTest$Circle";
  private static final String RECT_NAME = "dev.zems.lib.value.convert.BoundDispatchJourneyTest$Rect";
  private static final String SHAPE_NAME = "dev.zems.lib.value.convert.BoundDispatchJourneyTest$Shape";
  private static final String BOUND_SET = "[" + CIRCLE_NAME + ", " + RECT_NAME + "]";
  private static final String INIT_TRIPWIRE_NAME = "dev.zems.lib.value.fixture.InitTripwire";
  private static final String INIT_TRIPWIRE_RESOURCE = "dev/zems/lib/value/fixture/InitTripwire.class";
  private static final String LOAD_TRIPWIRE_NAME = "dev.zems.lib.value.fixture.LoadTripwire";
  private static final String LOAD_TRIPWIRE_RESOURCE = "dev/zems/lib/value/fixture/LoadTripwire.class";

  static Stream<TypeSlotCase> slotsNamingCircle() {
    return Stream.of(
      new TypeSlotCase("type value built from the class", Value.typeOf(Circle.class)),
      new TypeSlotCase("type value built from the name alone", Value.typeOf(CIRCLE_NAME)),
      new TypeSlotCase("string in qualified-name form", Value.of(CIRCLE_NAME))
    );
  }

  static Stream<TypeSlotCase> slotsHoldingNoTypeName() {
    return Stream.of(
      new TypeSlotCase("an int", Value.of(42)),
      new TypeSlotCase("a malformed name string", Value.of("not a name!")),
      new TypeSlotCase("explicit null", Value.nullValue()),
      new TypeSlotCase(
        "a nested structure whose slot spells Circle's name",
        Value.of(SlotBag.empty().with("name", Value.of(CIRCLE_NAME)))
      )
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("slotsNamingCircle")
  @JourneyTest(speakingId = "type-value-bound-dispatch", acceptance = "a1")
  @DisplayName("a structure converts to the bound class its type slot names; the name alone carries no class")
  void structureConvertsToTheBoundClassItsTypeSlotNames(TypeSlotCase typeSlot) {
    BoundTypes<Shape> shapes = BoundTypes.of(Shape.class, Circle.class, Rect.class);
    SlotBag bag = SlotBag.empty().with("kind", typeSlot.slot()).with("radius", Value.of(2.0));

    Value<Shape> shape = Converter.standard().convert(Value.of(bag), shapes, "kind");

    assertThat(shape)
      .isPresent()
      .isEqualTo(Value.of(new Circle(2.0)));
  }

  @Test
  @JourneyTest(speakingId = "type-value-bound-dispatch", acceptance = "a1")
  @DisplayName("structures of mixed shapes each convert to the class their own type slot names")
  void mixedStructuresEachConvertToTheirOwnClass() {
    BoundTypes<Shape> shapes = BoundTypes.of(Shape.class, Circle.class, Rect.class);
    List<SlotBag> bags = List.of(
      SlotBag.empty().with("kind", Value.typeOf(Circle.class)).with("radius", Value.of(2.0)),
      SlotBag.empty().with("kind", Value.typeOf(Rect.class)).with("width", Value.of(3.0)).with("height", Value.of(4.0))
    );

    List<Value<Shape>> converted = bags
      .stream()
      .map(bag -> Converter.standard().convert(Value.of(bag), shapes, "kind"))
      .toList();

    assertThat(converted).containsExactly(Value.of(new Circle(2.0)), Value.of(new Rect(3.0, 4.0)));
  }

  @Test
  @JourneyTest(speakingId = "type-value-bound-dispatch", acceptance = "a2")
  @DisplayName("two callers dispatch the same structure on different slots, each getting the class its slot names")
  void twoCallersDispatchTheSameDataOnDifferentSlots() {
    BoundTypes<Shape> shapes = BoundTypes.of(Shape.class, Circle.class, Rect.class);
    SlotBag bag = SlotBag.empty()
      .with("kind", Value.typeOf(Circle.class))
      .with("type", Value.typeOf(Rect.class))
      .with("radius", Value.of(2.0))
      .with("width", Value.of(3.0))
      .with("height", Value.of(4.0));

    Value<Shape> byKind = Converter.standard().convert(Value.of(bag), shapes, "kind");
    Value<Shape> byType = Converter.standard().convert(Value.of(bag), shapes, "type");

    assertThat(byKind).isEqualTo(Value.of(new Circle(2.0)));
    assertThat(byType).isEqualTo(Value.of(new Rect(3.0, 4.0)));
  }

  @Test
  @JourneyTest(speakingId = "type-value-bound-dispatch", acceptance = "a2")
  @DisplayName("conventional names like type, @type and class are neither a fallback nor reserved")
  void noSlotNameHasBuiltInMeaning() {
    BoundTypes<Shape> shapes = BoundTypes.of(Shape.class, Circle.class, Rect.class);
    SlotBag bag = SlotBag.empty()
      .with("type", Value.typeOf(Circle.class))
      .with("@type", Value.typeOf(Circle.class))
      .with("class", Value.typeOf(Circle.class))
      .with("radius", Value.of(2.0));

    Value<Shape> byKind = Converter.standard().convert(Value.of(bag), shapes, "kind");
    Value<Shape> byAtType = Converter.standard().convert(Value.of(bag), shapes, "@type");

    assertThat(byKind)
      .isError()
      .isEqualTo(Value.errorMessage("Cannot convert SlotBag to Shape: type slot 'kind' is missing", Shape.class));
    assertThat(byAtType).isEqualTo(Value.of(new Circle(2.0)));
  }

  @Test
  @JourneyTest(speakingId = "type-value-bound-dispatch", acceptance = "a3")
  @DisplayName("a name outside the bound set is refused with an error naming the name and the set, never absent")
  void unboundNameIsRefusedNamingTheNameAndTheSet() {
    BoundTypes<Shape> shapes = BoundTypes.of(Shape.class, Circle.class, Rect.class);
    SlotBag bag = SlotBag.empty().with("kind", Value.typeOf("com.example.Pwn")).with("radius", Value.of(2.0));

    Value<Shape> shape = Converter.standard().convert(Value.of(bag), shapes, "kind");

    assertThat(shape)
      .isError()
      .isEqualTo(
        Value.errorMessage(
          "Cannot convert SlotBag to Shape: type name 'com.example.Pwn' is not in the bound set " + BOUND_SET,
          Shape.class
        )
      );
  }

  @Test
  @JourneyTest(speakingId = "type-value-bound-dispatch", acceptance = "a3")
  @DisplayName("an unbound class on the classpath named by the data is refused and its static initialiser never runs")
  void unboundClassOnTheClasspathIsNeverInitialised() {
    assertThat(BoundDispatchJourneyTest.class.getClassLoader().getResource(INIT_TRIPWIRE_RESOURCE))
      .as("precondition: %s is on the test classpath", INIT_TRIPWIRE_NAME)
      .isNotNull();
    assertThat(Tripwires.INIT_TRIPWIRE_INITIALISED).as("precondition: nothing initialised the tripwire").isFalse();
    BoundTypes<Shape> shapes = BoundTypes.of(Shape.class, Circle.class, Rect.class);
    SlotBag bag = SlotBag.empty().with("kind", Value.typeOf(INIT_TRIPWIRE_NAME));

    Value<Shape> shape = Converter.standard().convert(Value.of(bag), shapes, "kind");

    assertThat(shape).isEqualTo(
      Value.errorMessage(
        "Cannot convert SlotBag to Shape: type name '" + INIT_TRIPWIRE_NAME + "' is not in the bound set " + BOUND_SET,
        Shape.class
      )
    );
    assertThat(Tripwires.INIT_TRIPWIRE_INITIALISED).isFalse();
  }

  @Test
  @JourneyTest(speakingId = "type-value-bound-dispatch", acceptance = "a3")
  @DisplayName("an unbound class file on the classpath named by the data is refused without ever being loaded")
  void unboundClassFileOnTheClasspathIsNeverLoaded() throws IOException {
    try (
      InputStream junk = BoundDispatchJourneyTest.class.getClassLoader().getResourceAsStream(LOAD_TRIPWIRE_RESOURCE)
    ) {
      assertThat(junk).as("precondition: %s is on the test classpath", LOAD_TRIPWIRE_NAME).isNotNull();
      assertThat(new String(junk.readAllBytes(), StandardCharsets.US_ASCII).strip())
        .as("precondition: loading %s would throw ClassFormatError", LOAD_TRIPWIRE_NAME)
        .isEqualTo("not a class file");
    }
    BoundTypes<Shape> shapes = BoundTypes.of(Shape.class, Circle.class, Rect.class);
    SlotBag bag = SlotBag.empty().with("kind", Value.typeOf(LOAD_TRIPWIRE_NAME));

    Value<Shape> shape = Converter.standard().convert(Value.of(bag), shapes, "kind");

    assertThat(shape).isEqualTo(
      Value.errorMessage(
        "Cannot convert SlotBag to Shape: type name '" + LOAD_TRIPWIRE_NAME + "' is not in the bound set " + BOUND_SET,
        Shape.class
      )
    );
  }

  @Test
  @JourneyTest(speakingId = "type-value-bound-dispatch", acceptance = "a3")
  @DisplayName("neither a bound class's simple name nor the base type's name matches the bound set")
  void simpleNameAndBaseNameDoNotMatch() {
    BoundTypes<Shape> shapes = BoundTypes.of(Shape.class, Circle.class, Rect.class);
    SlotBag simpleName = SlotBag.empty().with("kind", Value.of("Circle")).with("radius", Value.of(2.0));
    SlotBag baseName = SlotBag.empty().with("kind", Value.typeOf(Shape.class)).with("radius", Value.of(2.0));

    Value<Shape> bySimpleName = Converter.standard().convert(Value.of(simpleName), shapes, "kind");
    Value<Shape> byBaseName = Converter.standard().convert(Value.of(baseName), shapes, "kind");

    assertThat(bySimpleName).isEqualTo(
      Value.errorMessage(
        "Cannot convert SlotBag to Shape: type name 'Circle' is not in the bound set " + BOUND_SET,
        Shape.class
      )
    );
    assertThat(byBaseName).isEqualTo(
      Value.errorMessage(
        "Cannot convert SlotBag to Shape: type name '" + SHAPE_NAME + "' is not in the bound set " + BOUND_SET,
        Shape.class
      )
    );
  }

  @Test
  @JourneyTest(speakingId = "type-value-bound-dispatch", acceptance = "a4")
  @DisplayName("a matched class the data does not fit fails exactly as the single-target conversion to that class")
  void matchedClassFailsExactlyAsTheSingleTargetConversion() {
    BoundTypes<Shape> shapes = BoundTypes.of(Shape.class, Circle.class, Rect.class);
    SlotBag mismatched = SlotBag.empty().with("kind", Value.typeOf(Circle.class)).with("radius", Value.of("abc"));
    SlotBag missing = SlotBag.empty().with("kind", Value.typeOf(Circle.class));

    Value<Shape> dispatchedMismatch = Converter.standard().convert(Value.of(mismatched), shapes, "kind");
    Value<Shape> dispatchedMissing = Converter.standard().convert(Value.of(missing), shapes, "kind");

    assertThat(dispatchedMismatch)
      .isEqualTo(Converter.standard().convert(Value.of(mismatched), Circle.class))
      .isEqualTo(
        Value.errorMessage(
          "Cannot convert SlotBag to Circle: slot 'radius' cannot fill component 'radius': " +
            "Cannot convert String to Double: Not a number: abc",
          Circle.class
        )
      );
    assertThat(dispatchedMissing)
      .isEqualTo(Converter.standard().convert(Value.of(missing), Circle.class))
      .isEqualTo(
        Value.errorMessage(
          "Cannot convert SlotBag to Circle: slot 'radius' for component 'radius' is missing",
          Circle.class
        )
      );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("slotsHoldingNoTypeName")
  @JourneyTest(speakingId = "type-value-bound-dispatch", acceptance = "a5")
  @DisplayName("a type slot holding no type name answers a structured error naming the slot, never an exception")
  void typeSlotWithoutATypeNameReportsAStructuredError(TypeSlotCase typeSlot) {
    BoundTypes<Shape> shapes = BoundTypes.of(Shape.class, Circle.class, Rect.class);
    SlotBag bag = SlotBag.empty().with("kind", typeSlot.slot()).with("radius", Value.of(2.0));

    Value<Shape> shape = Converter.standard().convert(Value.of(bag), shapes, "kind");

    assertThat(shape)
      .isError()
      .isEqualTo(
        Value.errorMessage("Cannot convert SlotBag to Shape: type slot 'kind' does not hold a type name", Shape.class)
      );
  }

  interface Shape {}

  record Circle(double radius) implements Shape {}

  record Rect(double width, double height) implements Shape {}

  record TypeSlotCase(String label, Value<?> slot) {
    @Override
    public String toString() {
      return label;
    }
  }
}
