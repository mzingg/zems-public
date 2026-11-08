package dev.zems.lib.value.builtin;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.THROWABLE;

import dev.zems.lib.common.junit.JourneyTest;
import dev.zems.lib.value.ErrorValue;
import dev.zems.lib.value.TypeName;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.junit.RoundTripFormatHarness;
import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("TypeValue — a symbolic type name that never reaches the class loader")
class TypeValueJourneyTest {

  private static final String PERSON_NAME = "dev.zems.lib.value.builtin.TypeValueJourneyTest$Person";

  static Stream<RoundTripFormatHarness.Format> formats() {
    return RoundTripFormatHarness.all();
  }

  static Stream<InvalidCase> invalidInputs() {
    return Stream.of(
      new InvalidCase("null string", () -> Value.typeOf((String) null), "Type name must not be null or empty"),
      new InvalidCase("empty string", () -> Value.typeOf(""), "Type name must not be null or empty"),
      new InvalidCase("blank string", () -> Value.typeOf("   "), "Type name must not be null or empty"),
      new InvalidCase("not an identifier", () -> Value.typeOf("not a name!"), "Invalid type name: not a name!"),
      new InvalidCase("empty segment", () -> Value.typeOf("a..b"), "Invalid type name: a..b"),
      new InvalidCase("trailing dot", () -> Value.typeOf("com.example."), "Invalid type name: com.example."),
      new InvalidCase("null Class", () -> Value.typeOf((Class<?>) null), "Class must not be null"),
      new InvalidCase("array Class", () -> Value.typeOf(String[].class), "Invalid type name: [Ljava.lang.String;")
    );
  }

  @Test
  @JourneyTest(speakingId = "type-value-builtin", acceptance = "fromclass")
  @DisplayName("typeOf(Class) extracts the qualified name for top-level, nested and primitive classes")
  void typeOfClassExtractsTheQualifiedName() {
    Value<TypeName> topLevel = Value.typeOf(String.class);
    Value<TypeName> nested = Value.typeOf(Person.class);
    Value<TypeName> primitive = Value.typeOf(int.class);

    assertThat(topLevel).isInstanceOfSatisfying(TypeValue.class, v ->
      assertThat(v.typeName().name()).isEqualTo("java.lang.String")
    );
    assertThat(nested).isInstanceOfSatisfying(TypeValue.class, v ->
      assertThat(v.typeName().name()).isEqualTo(PERSON_NAME)
    );
    assertThat(primitive).isInstanceOfSatisfying(TypeValue.class, v ->
      assertThat(v.typeName().name()).isEqualTo("int")
    );
  }

  @Test
  @JourneyTest(speakingId = "type-value-builtin", acceptance = "named")
  @DisplayName("typeOf(String) yields the same kind of value, and the marker separates it from ordinary text")
  void typeOfStringIsMarkedAsATypeName() {
    Value<TypeName> named = Value.typeOf("com.example.Person");

    assertThat(named).isInstanceOfSatisfying(TypeValue.class, v ->
      assertThat(v.typeName().name()).isEqualTo("com.example.Person")
    );
    assertThat(named.isTypeName()).isTrue();
    assertThat(named.asTypeName()).hasValue("com.example.Person");

    Value<String> text = Value.of("com.example.Person");
    assertThat(text.isTypeName()).isFalse();
    assertThat(text.asTypeName()).isEmpty();
  }

  @Test
  @JourneyTest(speakingId = "type-value-builtin", acceptance = "identity")
  @DisplayName("the construction path leaves no trace: from a Class and from its name are equal and hash alike")
  void bothConstructionPathsProduceEqualValues() {
    Value<TypeName> fromClass = Value.typeOf(Person.class);
    Value<TypeName> parsed = Value.typeOf(PERSON_NAME);

    assertThat(fromClass).isEqualTo(parsed);
    assertThat(fromClass).hasSameHashCodeAs(parsed);
    assertThat(fromClass.asTypeName()).hasValue(PERSON_NAME);
    assertThat(parsed.asTypeName()).hasValue(PERSON_NAME);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "type-value-builtin", acceptance = "wire")
  @DisplayName("the name round-trips to an equal value, and both factories write byte-identical output")
  void nameRoundTripsAndBothFactoriesWriteTheSameBytes(RoundTripFormatHarness.Format format) {
    Value<TypeName> fromClass = Value.typeOf(Person.class);
    Value<TypeName> parsed = Value.typeOf(PERSON_NAME);

    byte[] fromClassWire = format.write(fromClass, BuiltinTypeDescriptors.TYPE_NAME);
    byte[] parsedWire = format.write(parsed, BuiltinTypeDescriptors.TYPE_NAME);

    assertThat(fromClassWire).as("nothing but the name is ever on the wire").isEqualTo(parsedWire);

    Value<TypeName> reread = format.read(fromClassWire, BuiltinTypeDescriptors.TYPE_NAME);
    assertThat(reread).isEqualTo(fromClass);
    assertThat(reread.asTypeName()).hasValue(PERSON_NAME);
  }

  @Test
  @JourneyTest(speakingId = "type-value-builtin", acceptance = "inert")
  @DisplayName("a name is inert data whether or not a class of that name exists — it is never resolved")
  void namesAreNeverResolvedToClasses() {
    assertThat(TypeNameProbe.probedTypeInitialised()).as("baseline before anything is constructed").isFalse();

    Value<TypeName> ghost = Value.typeOf("com.example.absent.NoSuchThing");
    assertThat(ghost.asTypeName()).hasValue("com.example.absent.NoSuchThing");

    // The class literal loads and links ProbedType but does not run its static initialiser (JLS 12.4.1); a
    // Class.forName(name) would, and would trip the probe before the round trip is even tested.
    Value<TypeName> probed = Value.typeOf(TypeNameProbe.ProbedType.class.getName());
    assertThat(probed.asTypeName()).hasValue("dev.zems.lib.value.builtin.TypeNameProbe$ProbedType");

    for (RoundTripFormatHarness.Format format : RoundTripFormatHarness.all().toList()) {
      for (Value<TypeName> value : List.of(ghost, probed)) {
        byte[] wire = format.write(value, BuiltinTypeDescriptors.TYPE_NAME);
        Value<TypeName> reread = format.read(wire, BuiltinTypeDescriptors.TYPE_NAME);

        assertThat(reread).as("%s round trip", format.name()).isEqualTo(value);
        assertThat(reread.convertTo(String.class))
          .as("%s canonical form", format.name())
          .hasStringValue(value.unbox().name());
        assertThat(reread.asTypeName()).as("%s name", format.name()).hasValue(value.unbox().name());
      }
    }

    assertThat(TypeNameProbe.probedTypeInitialised())
      .as("constructing, writing, reading and reading back a name must never ask the class loader about it")
      .isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidInputs")
  @JourneyTest(speakingId = "type-value-builtin", acceptance = "invalid")
  @DisplayName("invalid input yields an ErrorValue with its documented message instead of throwing")
  void invalidInputYieldsErrorValue(InvalidCase invalid) {
    Value<TypeName> result = invalid.factory().get();

    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).get(THROWABLE).hasMessage(invalid.expectedMessage());
  }

  record InvalidCase(String label, Supplier<Value<TypeName>> factory, String expectedMessage) {
    @Override
    public String toString() {
      return label;
    }
  }

  record Person(String name) {}
}
