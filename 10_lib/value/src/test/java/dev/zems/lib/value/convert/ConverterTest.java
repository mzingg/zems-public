package dev.zems.lib.value.convert;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.CoreValue;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.fixture.SlotFixtures.Point;
import dev.zems.lib.value.json.engine.JsonObject;
import dev.zems.lib.value.json.engine.JsonString;
import dev.zems.lib.value.json.engine.JsonValue;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.net.InetAddress;
import java.time.ZoneId;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("Converter")
class ConverterTest {

  @Test
  @DisplayName("a null value or a null target class is a programming bug, not a data problem")
  void nullArgumentsThrowRatherThanReturningAnError() {
    Converter converter = Converter.standard();

    assertThatThrownBy(() -> converter.convert(null, Integer.class))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Source value must not be null");
    assertThatThrownBy(() -> converter.convert(Value.of(1), null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Target class must not be null");
  }

  @Test
  @DisplayName("registration validates eagerly — a broken conversion fails where it is registered")
  void withRejectsNullsAtRegistrationTime() {
    Converter converter = Converter.standard();

    assertThatThrownBy(() -> converter.with(null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Conversion must not be null");
    assertThatThrownBy(() -> converter.with(new HalfDeclaredConversion(null, Integer.class)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Conversion source type must not be null");
    assertThatThrownBy(() -> converter.with(new HalfDeclaredConversion(String.class, null)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Conversion target type must not be null");
  }

  @Test
  @DisplayName("a primitive target class is read as its wrapper")
  void primitiveTargetsAreNormalisedToWrappers() {
    Converter converter = Converter.standard();

    assertThat(converter.convert(Value.of(42L), int.class)).isEqualTo(Value.of(42));
    assertThat(converter.convert(Value.of(1), double.class)).isEqualTo(Value.of(1.0d));
    assertThat(converter.convert(Value.of("true"), boolean.class)).isEqualTo(Value.of(true));
    assertThat(converter.convert(Value.of("x"), char.class)).isEqualTo(Value.of('x'));
  }

  @Test
  @DisplayName("state markers have no content to re-type, so they pass through as themselves")
  void stateMarkersPassThroughRetyped() {
    Converter converter = Converter.standard();

    assertThat(converter.convert(Value.nullValue(), Integer.class)).isNullValue();
    assertThat(converter.convert(Value.undefined(), Integer.class)).isUndefined();
    assertThat(converter.convert(Value.unresolved(), Integer.class)).isUnresolved();
    assertThat(converter.convert(Value.tombstone(), Integer.class)).isTombstone();
  }

  @Test
  @DisplayName("an error keeps its reason and takes on the requested expected type")
  void errorsAreRetargetedNotRewritten() {
    Converter converter = Converter.standard();
    Value<String> failed = Value.errorMessage("boom", String.class);

    Value<Integer> retargeted = converter.convert(failed, Integer.class);

    assertThat(retargeted).hasErrorMessage("boom").hasExpectedTypeName("java.lang.Integer");
    assertThat(retargeted).isEqualTo(Value.errorMessage("boom", Integer.class));
  }

  @Test
  @DisplayName("a payload the target class already accepts is handed back untouched")
  void identityAnswersWhateverTheTargetAlreadyAccepts() {
    Converter converter = Converter.standard();
    Value<Person> boxed = Value.of(new Person("Ada"));
    Value<String> text = Value.of("text");
    Temperature reading = new Temperature(21.5d);

    assertThat(converter.convert(boxed, Person.class)).isSameAs(boxed);
    assertThat(converter.convert(text, CharSequence.class)).isSameAs(text);
    assertThat(converter.convert(reading, Temperature.class)).isSameAs(reading);
  }

  @Test
  @DisplayName("a record that declares SLOTS converted to its own class stays the same instance — identity runs first")
  void identityRunsBeforeTheStructuralStage() {
    Value<Point> boxed = Value.of(new Point(1, 2));

    Value<Point> converted = Converter.standard().convert(boxed, Point.class);

    assertThat(converted).isSameAs(boxed);
  }

  @Test
  @DisplayName("a registered conversion is consulted before the identity rule, so with(...) stays authoritative")
  void registeredConversionsWinOverIdentity() {
    Converter shouting = Converter.standard().with(
      Conversion.of(String.class, String.class, text -> Value.of(text.toUpperCase(Locale.ROOT)))
    );

    assertThat(shouting.convert(Value.of("abc"), String.class)).isEqualTo(Value.of("ABC"));
    assertThat(Converter.standard().convert(Value.of("abc"), String.class)).isEqualTo(Value.of("abc"));
  }

  @Test
  @DisplayName("with(...) derives a new converter, replaces the entry for a pair it already knows, and never mutates")
  void withDerivesAndReplacesWithoutTouchingTheOriginal() {
    Converter standard = Converter.standard();
    assertThat(Converter.standard()).as("the standard converter is shared, built once").isSameAs(standard);

    Converter down = standard.with(Conversions.doubleToInt(Rounding.DOWN));
    Converter up = down.with(Conversions.doubleToInt(Rounding.UP));

    assertThat(down.convert(Value.of(3.7d), Integer.class)).isEqualTo(Value.of(3));
    assertThat(up.convert(Value.of(3.7d), Integer.class)).isEqualTo(Value.of(4));
    assertThat(standard.convert(Value.of(3.7d), Integer.class)).hasErrorMessage(
      "Cannot convert Double to Integer: 3.7 is not a whole number"
    );
    assertThat(down.convert(Value.of(3.7d), Integer.class)).isEqualTo(Value.of(3));
  }

  @Test
  @DisplayName("lookup walks the payload's superclass chain so a factory-produced subtype still finds its conversion")
  void theSuperclassChainIsWalkedOnAMiss() {
    Converter converter = Converter.standard();
    InetAddress address = InetAddress.ofLiteral("192.168.1.1");
    ZoneId zone = ZoneId.of("Europe/Zurich");

    assertThat(address.getClass()).as("premise: the factory hands back a subtype").isNotEqualTo(InetAddress.class);
    assertThat(zone.getClass()).as("premise: the factory hands back a subtype").isNotEqualTo(ZoneId.class);

    assertThat(converter.convert(Value.of(address), String.class)).isEqualTo(Value.of("192.168.1.1"));
    assertThat(converter.convert(Value.of(zone), String.class)).isEqualTo(Value.of("Europe/Zurich"));
  }

  @Test
  @DisplayName("interfaces are not walked — a conversion keyed on one never catches a payload that implements it")
  void interfacesAreNotWalked() {
    Converter viaInterface = Converter.standard().with(
      Conversion.of(CharSequence.class, Person.class, text -> Value.of(new Person(text.toString())))
    );
    Converter viaClass = Converter.standard().with(
      Conversion.of(String.class, Person.class, text -> Value.of(new Person(text)))
    );

    assertThat(viaInterface.convert(Value.of("Ada"), Person.class))
      .isUnsupported()
      .hasUnsupportedReason("Cannot convert String to Person: no conversion exists");
    assertThat(viaClass.convert(Value.of("Ada"), Person.class)).isEqualTo(Value.of(new Person("Ada")));
  }

  @Test
  @DisplayName("a JSON payload looks up under its mirrored kind interface, so with(...) on JsonObject is found")
  void aJsonPayloadLooksUpUnderItsKindInterface() {
    Converter memberNames = Converter.standard().with(
      Conversion.of(JsonObject.class, String.class, object -> Value.of("members " + object.asMap().keySet()))
    );
    Value<JsonValue> document = Value.parseJson("{\"b\":1,\"a\":[]}");

    assertThat(memberNames.convert(document, String.class)).isEqualTo(Value.of("members [b, a]"));
  }

  @Test
  @DisplayName("a JSON object, array or number refusing a target names its kind interface in the frame")
  void aJsonRefusalNamesTheKindInterface() {
    Converter converter = Converter.standard();

    assertThat(converter.convert(Value.parseJson("{\"a\":1}"), Integer.class)).isEqualTo(
      Value.unsupported("Cannot convert JsonObject to Integer: no conversion exists", Integer.class)
    );
    assertThat(converter.convert(Value.parseJson("[1]"), Integer.class)).isEqualTo(
      Value.unsupported("Cannot convert JsonArray to Integer: no conversion exists", Integer.class)
    );
    assertThat(converter.convert(Value.parseJson("12"), String.class)).isEqualTo(
      Value.unsupported("Cannot convert JsonNumber to String: no conversion exists", String.class)
    );
  }

  @Test
  @DisplayName("a JSON document converted to JsonValue, or to its own kind interface, is the document itself")
  void aJsonDocumentConvertedToItsEngineTypeIsItself() {
    Converter converter = Converter.standard();
    Value<JsonValue> object = Value.parseJson("{\"a\":1}");
    Value<JsonValue> string = Value.parseJson("\"x\"");
    Value<JsonValue> number = Value.parseJson("1.50");

    assertThat(converter.convert(object, JsonValue.class)).isSameAs(object);
    assertThat(converter.convert(string, JsonValue.class)).isSameAs(string);
    assertThat(converter.convert(number, JsonValue.class)).isSameAs(number);
    assertThat(converter.convert(string, JsonString.class)).isSameAs(string);
    assertThat(converter.convert(object, JsonObject.class)).isSameAs(object);
  }

  @Test
  @DisplayName("a JSON value of another kind refuses a kind interface as an unsupported type pair")
  void anotherKindInterfaceIsRefused() {
    Converter converter = Converter.standard();

    assertThat(converter.convert(Value.parseJson("1"), JsonString.class)).isEqualTo(
      Value.unsupported("Cannot convert JsonNumber to JsonString: no conversion exists", JsonString.class)
    );
  }

  @Test
  @DisplayName("a conversion body that answers an unsupported value keeps its own reason — the frame is not re-applied")
  void conversionBodyAnswersItsOwnUnsupportedReason() {
    Converter converter = Converter.standard().with(
      Conversion.of(String.class, Person.class, text -> Value.unsupported("names are not parsed here", Person.class))
    );

    assertThat(converter.convert(Value.of("Ada"), Person.class))
      .isUnsupported()
      .hasUnsupportedReason("names are not parsed here");
  }

  @Test
  @DisplayName("an unsupported source re-targets to the requested type, keeping its reason")
  void unsupportedSourceRetargetsKeepingItsReason() {
    Converter converter = Converter.standard();
    Value<Integer> source = Value.unsupported("no conversion exists", Integer.class);

    Value<Long> retargeted = converter.convert(source, Long.class);

    assertThat(retargeted)
      .isUnsupported()
      .hasUnsupportedReason("no conversion exists")
      .hasExpectedTypeName("java.lang.Long");
  }

  @Test
  @DisplayName("dispatch: a null source, null bound types or a null or blank type slot name is a programming bug")
  void dispatchNullArgumentsThrowRatherThanReturningAnError() {
    Converter converter = Converter.standard();
    BoundTypes<Shape> shapes = BoundTypes.of(Shape.class, Circle.class);

    assertThatThrownBy(() -> converter.convert(null, shapes, "kind"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Source value must not be null");
    assertThatThrownBy(() -> converter.convert(Value.of(1), null, "kind"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Bound types must not be null");
    assertThatThrownBy(() -> converter.convert(Value.of(1), shapes, null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Type slot name must not be null or blank");
    assertThatThrownBy(() -> converter.convert(Value.of(1), shapes, " "))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Type slot name must not be null or blank");
  }

  @Test
  @DisplayName("dispatch: state markers pass through as themselves, before any slot is read")
  void dispatchPassesStateMarkersThrough() {
    Converter converter = Converter.standard();
    BoundTypes<Shape> shapes = BoundTypes.of(Shape.class, Circle.class);

    assertThat(converter.convert(Value.nullValue(), shapes, "kind")).isNullValue();
    assertThat(converter.convert(Value.undefined(), shapes, "kind")).isUndefined();
    assertThat(converter.convert(Value.unresolved(), shapes, "kind")).isUnresolved();
    assertThat(converter.convert(Value.tombstone(), shapes, "kind")).isTombstone();
  }

  @Test
  @DisplayName("dispatch: an error source keeps its reason and takes on the base type as its expected type")
  void dispatchRetargetsAnErrorToTheBaseType() {
    Converter converter = Converter.standard();
    BoundTypes<Shape> shapes = BoundTypes.of(Shape.class, Circle.class);
    Value<String> failed = Value.errorMessage("boom", String.class);

    Value<Shape> retargeted = converter.convert(failed, shapes, "kind");

    assertThat(retargeted)
      .isEqualTo(Value.errorMessage("boom", Shape.class))
      .hasExpectedTypeName("dev.zems.lib.value.convert.ConverterTest$Shape");
  }

  record HalfDeclaredConversion(
    Class<String> sourceType,
    Class<Integer> targetType
  ) implements Conversion<String, Integer> {
    @Override
    public Value<Integer> convert(String source) {
      return Value.of(source.length());
    }
  }

  record Person(String name) {}

  interface Shape {}

  record Circle(double radius) implements Shape {}

  record Temperature(double degrees) implements CoreValue<Temperature> {
    @Override
    public TypeDescriptor<Temperature> valueType() {
      return TypeDescriptor.of(Temperature.class.getName(), Temperature.class);
    }
  }
}
