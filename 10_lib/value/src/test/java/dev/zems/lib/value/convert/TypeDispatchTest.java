package dev.zems.lib.value.convert;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.TypeName;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.fixture.BrokenSlots;
import dev.zems.lib.value.fixture.SlotBag;
import dev.zems.lib.value.fixture.SlotFixtures.MisdeclaredSlots;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ContractTest
@DisplayName("TypeDispatch")
class TypeDispatchTest {

  private static final String CIRCLE_NAME = "dev.zems.lib.value.convert.TypeDispatchTest$Circle";
  private static final String SHAPE_NAME = "dev.zems.lib.value.convert.TypeDispatchTest$Shape";
  private static final String SQUARE_NAME = "dev.zems.lib.value.convert.TypeDispatchTest$Square";
  private static final String NO_TYPE_NAME =
    "Cannot convert SlotBag to Shape: type slot 'kind' does not hold a type name";

  static Stream<Arguments> absentTypeSlots() {
    return Stream.of(Arguments.of("undefined", Value.undefined()), Arguments.of("tombstone", Value.tombstone()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("absentTypeSlots")
  @DisplayName("an undefined or tombstoned type slot counts as missing")
  void absentTypeSlotStatesCountAsMissing(String label, Value<TypeName> absent) {
    BoundTypes<Shape> shapes = BoundTypes.of(Shape.class, Circle.class, Square.class);
    SlotBag bag = SlotBag.empty().with("kind", absent).with("radius", Value.of(2.0));

    Value<Shape> shape = Converter.standard().convert(Value.of(bag), shapes, "kind");

    assertThat(shape).isEqualTo(
      Value.errorMessage("Cannot convert SlotBag to Shape: type slot 'kind' is missing", Shape.class)
    );
  }

  @Test
  @DisplayName("an unresolved type slot holds no type name, and there is no cause to carry")
  void unresolvedTypeSlotHoldsNoTypeName() {
    BoundTypes<Shape> shapes = BoundTypes.of(Shape.class, Circle.class, Square.class);
    SlotBag bag = SlotBag.empty().with("kind", Value.unresolved()).with("radius", Value.of(2.0));

    Value<Shape> shape = Converter.standard().convert(Value.of(bag), shapes, "kind");

    assertThat(shape).isEqualTo(Value.errorMessage(NO_TYPE_NAME, Shape.class));
    assertThat(shape).extractingThrowable().hasNoCause();
  }

  @Test
  @DisplayName("an error in the type slot holds no type name, and its throwable rides as the cause")
  void errorInTheTypeSlotRidesAsTheCause() {
    BoundTypes<Shape> shapes = BoundTypes.of(Shape.class, Circle.class, Square.class);
    SlotBag bag = SlotBag.empty()
      .with("kind", Value.errorMessage("kind was never recorded", TypeName.class))
      .with("radius", Value.of(2.0));

    Value<Shape> shape = Converter.standard().convert(Value.of(bag), shapes, "kind");

    assertThat(shape).isEqualTo(Value.errorMessage(NO_TYPE_NAME, Shape.class));
    assertThat(shape).extractingThrowable().cause().hasMessage("kind was never recorded");
  }

  @Test
  @DisplayName("a malformed name string holds no type name, and the parser's refusal rides as the cause")
  void malformedNameCarriesTheParsersRefusalAsTheCause() {
    BoundTypes<Shape> shapes = BoundTypes.of(Shape.class, Circle.class, Square.class);
    SlotBag bag = SlotBag.empty().with("kind", Value.of("not a name!")).with("radius", Value.of(2.0));

    Value<Shape> shape = Converter.standard().convert(Value.of(bag), shapes, "kind");

    assertThat(shape).isEqualTo(Value.errorMessage(NO_TYPE_NAME, Shape.class));
    assertThat(shape)
      .extractingThrowable()
      .cause()
      .hasMessage("Cannot convert String to TypeName: Invalid type name: not a name!");
  }

  @Test
  @DisplayName("a payload with no SLOTS shape is a type-pair refusal: unsupported, not an error")
  void payloadWithoutSlotsIsUnsupported() {
    BoundTypes<Shape> shapes = BoundTypes.of(Shape.class, Circle.class, Square.class);

    Value<Shape> shape = Converter.standard().convert(Value.of(42), shapes, "kind");

    assertThat(shape)
      .isUnsupported()
      .hasUnsupportedReason("Cannot convert Integer to Shape: no conversion exists")
      .hasExpectedTypeName(SHAPE_NAME);
  }

  @Test
  @DisplayName("an empty bound set refuses every name")
  void emptyBoundSetRefusesEveryName() {
    BoundTypes<Shape> nothing = BoundTypes.of(Shape.class);
    SlotBag bag = SlotBag.empty().with("kind", Value.typeOf(Circle.class)).with("radius", Value.of(2.0));

    Value<Shape> shape = Converter.standard().convert(Value.of(bag), nothing, "kind");

    assertThat(shape).isEqualTo(
      Value.errorMessage(
        "Cannot convert SlotBag to Shape: type name '" + CIRCLE_NAME + "' is not in the bound set []",
        Shape.class
      )
    );
  }

  @Test
  @DisplayName("the matched conversion runs on the running converter, so a policy given with with(...) fills it")
  void runningConverterPoliciesApplyToTheMatchedConversion() {
    BoundTypes<Shape> shapes = BoundTypes.of(Shape.class, Circle.class, Square.class);
    SlotBag bag = SlotBag.empty().with("kind", Value.typeOf(Square.class)).with("side", Value.of(2.7));
    Converter rounding = Converter.standard().with(Conversions.doubleToInt(Rounding.DOWN));

    Value<Shape> rounded = rounding.convert(Value.of(bag), shapes, "kind");
    Value<Shape> refused = Converter.standard().convert(Value.of(bag), shapes, "kind");

    assertThat(rounded).isEqualTo(Value.of(new Square(2)));
    assertThat(refused)
      .isEqualTo(
        Value.errorMessage(
          "Cannot convert SlotBag to Square: slot 'side' cannot fill component 'side': " +
            "Cannot convert Double to Integer: 2.7 is not a whole number",
          Square.class
        )
      )
      .hasExpectedTypeName(SQUARE_NAME);
  }

  @Test
  @DisplayName(
    "the type slot is read through the running converter, so a String to TypeName alias given with with(...) applies"
  )
  void runningConverterReadsTheTypeSlot() {
    BoundTypes<Shape> shapes = BoundTypes.of(Shape.class, Circle.class, Square.class);
    SlotBag bag = SlotBag.empty().with("kind", Value.of("circle")).with("radius", Value.of(2.0));
    Converter aliasing = Converter.standard().with(
      Conversion.of(String.class, TypeName.class, text ->
        "circle".equals(text) ? Value.typeOf(CIRCLE_NAME) : Value.typeOf(text)
      )
    );

    Value<Shape> aliased = aliasing.convert(Value.of(bag), shapes, "kind");
    Value<Shape> unaliased = Converter.standard().convert(Value.of(bag), shapes, "kind");

    assertThat(aliased).isEqualTo(Value.of(new Circle(2.0)));
    assertThat(unaliased).isEqualTo(
      Value.errorMessage(
        "Cannot convert SlotBag to Shape: type name 'circle' is not in the bound set [" +
          CIRCLE_NAME +
          ", " +
          SQUARE_NAME +
          "]",
        Shape.class
      )
    );
  }

  @Test
  @DisplayName("the refusal frame names the shape's type, not the payload's runtime class")
  void refusalFrameNamesTheShapeTypeNotThePayloadClass() {
    BoundTypes<Shape> shapes = BoundTypes.of(Shape.class, Circle.class, Square.class);
    LabelledSlotBag bag = new LabelledSlotBag(SlotBag.empty().with("radius", Value.of(2.0)));

    Value<Shape> shape = Converter.standard().convert(Value.of(bag), shapes, "kind");

    assertThat(shape).isEqualTo(
      Value.errorMessage("Cannot convert SlotBag to Shape: type slot 'kind' is missing", Shape.class)
    );
  }

  @Test
  @DisplayName("a SlotShape whose slots(...) throws is a programming bug: the exception propagates unchanged")
  void throwingSlotShapePropagates() {
    BoundTypes<Shape> shapes = BoundTypes.of(Shape.class, Circle.class, Square.class);
    Value<BrokenSlots> broken = Value.of(new BrokenSlots());

    assertThatThrownBy(() -> Converter.standard().convert(broken, shapes, "kind"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("broken")
      .hasNoCause();
  }

  @Test
  @DisplayName("a misdeclared SLOTS on the source throws, as for the single-target conversion")
  void misdeclaredSlotsOnTheSourceThrows() {
    BoundTypes<Shape> shapes = BoundTypes.of(Shape.class, Circle.class, Square.class);
    Value<MisdeclaredSlots> misdeclared = Value.of(new MisdeclaredSlots());

    assertThatThrownBy(() -> Converter.standard().convert(misdeclared, shapes, "kind"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage(
        "dev.zems.lib.value.fixture.SlotFixtures$MisdeclaredSlots.SLOTS is declared as java.lang.String " +
          "which is not a SlotShape — declare it as SlotShape or rename the field"
      );
  }

  interface Shape {}

  record Circle(double radius) implements Shape {}

  record Square(int side) implements Shape {}

  static class LabelledSlotBag extends SlotBag {

    LabelledSlotBag(SlotBag source) {
      super(source.slots());
    }
  }
}
