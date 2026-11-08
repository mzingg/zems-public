package dev.zems.lib.value.builtin;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.JourneyTest;
import dev.zems.lib.value.BoxedValue;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.junit.RoundTripFormatHarness;
import dev.zems.lib.value.valueio.ChecksumAlgorithm;
import dev.zems.lib.value.valueio.ValueIo;
import dev.zems.lib.value.valueio.descriptor.EnumAlias;
import dev.zems.lib.value.valueio.descriptor.EvolutionPolicy;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import dev.zems.lib.value.valueio.descriptor.UnknownConstantPolicy;
import dev.zems.lib.value.valueio.descriptor.UnknownSlotPolicy;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("EnumValue — enum constants as first-class values, written by name")
class EnumValueJourneyTest {

  private static final String STATUS_NAME = "dev.zems.lib.value.builtin.EnumValueJourneyTest$Status";
  private static final String UNKNOWN_PENDING = "Unknown constant 'PENDING' for enum " + STATUS_NAME;
  private static final EvolutionPolicy TOLERANT = new EvolutionPolicy(
    UnknownSlotPolicy.FAIL,
    UnknownConstantPolicy.ERROR_VALUE
  );

  static Stream<RoundTripFormatHarness.Format> formats() {
    return RoundTripFormatHarness.all();
  }

  @Test
  @JourneyTest(speakingId = "enum-value-builtin", acceptance = "of")
  @DisplayName("Value.of(constant) pattern-matches as EnumValue, not BoxedValue, and unboxes to the same constant")
  void ofEnumConstantIsAFirstClassEnumValue() {
    Value<Status> value = Value.of(Status.ACTIVE);

    Status matched = switch (value) {
      case EnumValue<Status>(Status constant) -> constant;
      default -> null;
    };

    assertThat(matched).isSameAs(Status.ACTIVE);
    assertThat(value).isNotInstanceOf(BoxedValue.class);
    assertThat(value.unbox()).isSameAs(Status.ACTIVE);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "enum-value-builtin", acceptance = "of")
  @DisplayName("a constant with a body is an EnumValue of its declaring enum and round-trips to the same constant")
  void constantWithABodyRoundTripsAsItsDeclaringEnum(RoundTripFormatHarness.Format format) {
    Value<Operation> value = Value.of(Operation.PLUS);

    assertThat(value).isInstanceOfSatisfying(EnumValue.class, enumValue ->
      assertThat(enumValue.constant()).isSameAs(Operation.PLUS)
    );
    assertThat(value.valueType()).isEqualTo(TypeDescriptor.of(Operation.class));

    byte[] wire = format.write(value, value.valueType());
    Value<Operation> reread = format.read(wire, TypeDescriptor.of(Operation.class));

    assertThat(reread).isEqualTo(value);
    assertThat(reread.unbox()).isSameAs(Operation.PLUS);
    assertThat(reread.unbox().apply(2, 3)).isEqualTo(5);
  }

  @Test
  @JourneyTest(speakingId = "enum-value-builtin", acceptance = "accessors")
  @DisplayName("isEnum, asEnum(Class) and asEnum() answer the constant; other enums and plain text do not match")
  void enumAccessorsAnswerTheConstant() {
    Value<Status> value = Value.of(Status.ACTIVE);

    assertThat(value.isEnum()).isTrue();
    assertThat(value.asEnum(Status.class)).hasValue(Status.ACTIVE);
    assertThat(value.asEnum()).hasValue(Status.ACTIVE);
    assertThat(value).hasEnumValue(Status.ACTIVE);
    assertThat(value.asEnum(Colour.class)).isEmpty();

    Value<String> text = Value.of("ACTIVE");
    assertThat(text.isEnum()).isFalse();
    assertThat(text.asEnum()).isEmpty();
    assertThat(text.asEnum(Status.class)).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "enum-value-builtin", acceptance = "name")
  @DisplayName("a constant round-trips, and the payload on the wire is its name")
  void constantRoundTripsUnderItsName(RoundTripFormatHarness.Format format) {
    TypeDescriptor<Status> descriptor = TypeDescriptor.of(Status.class);
    Value<Status> value = Value.of(Status.ARCHIVED);

    byte[] wire = format.write(value, descriptor);

    assertThat(format.read(wire, descriptor)).isEqualTo(value).hasEnumValue(Status.ARCHIVED);
    assertThat(format.read(wire, wireNameOf(Status.class))).hasStringValue("ARCHIVED");
  }

  @Test
  @JourneyTest(speakingId = "enum-value-builtin", acceptance = "name")
  @DisplayName("framed JSON carries the constant name in the scalar one-slot shape")
  void jsonPayloadIsTheConstantName() {
    byte[] wire = RoundTripFormatHarness.json().write(Value.of(Status.ACTIVE), TypeDescriptor.of(Status.class));

    assertThat(new String(wire, StandardCharsets.UTF_8)).contains("\"$payload\":{\"__slot0\":\"ACTIVE\"}");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "enum-value-builtin", acceptance = "name")
  @DisplayName("reordering the constants does not break an old payload: it reads back by name")
  void reorderedConstantsReadBackByName(RoundTripFormatHarness.Format format) {
    byte[] wire = format.write(Value.of(OrderV1.A), TypeDescriptor.of(OrderV1.class));

    Value<OrderV2> reread = format.read(wire, TypeDescriptor.of(OrderV2.class).withAliases(OrderV1.class.getName()));

    assertThat(reread).hasEnumValue(OrderV2.A);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "enum-value-builtin", acceptance = "name")
  @DisplayName("an overridden toString() never reaches the wire: the name is written")
  void overriddenToStringNeverReachesTheWire(RoundTripFormatHarness.Format format) {
    TypeDescriptor<Colour> descriptor = TypeDescriptor.of(Colour.class);

    byte[] wire = format.write(Value.of(Colour.RED), descriptor);

    assertThat(format.read(wire, wireNameOf(Colour.class))).hasStringValue("RED");
    assertThat(format.read(wire, descriptor)).hasEnumValue(Colour.RED);
  }

  @Test
  @JourneyTest(speakingId = "enum-value-builtin", acceptance = "find")
  @DisplayName("find(enum class) answers a descriptor named after the enum, with nothing hand-written")
  void findAnswersAWiredDescriptorForAnyEnum() {
    assertThat(TypeDescriptor.find(Status.class)).hasValueSatisfying(descriptor -> {
      assertThat(descriptor.descriptorName()).isEqualTo(STATUS_NAME);
      assertThat(descriptor.qualifiedName()).isEqualTo(STATUS_NAME);
    });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "enum-value-builtin", acceptance = "find")
  @DisplayName("a record with an enum component synthesises and round-trips")
  void recordWithAnEnumComponentRoundTrips(RoundTripFormatHarness.Format format) {
    TypeDescriptor<Account> descriptor = TypeDescriptor.of(Account.class);
    Value<Account> value = Value.of(new Account("ada", Status.DISABLED));

    byte[] wire = format.write(value, descriptor);

    assertThat(format.read(wire, descriptor)).isEqualTo(value);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "enum-value-builtin", acceptance = "find")
  @DisplayName("an unknown wire constant throws under STRICT and is an ErrorValue when the policy tolerates it")
  void unknownWireConstantThrowsUnderStrictAndIsAnErrorWhenTolerated(RoundTripFormatHarness.Format format) {
    byte[] wire = format.write(Value.of("PENDING"), wireNameOf(Status.class));

    assertThatThrownBy(() -> format.read(wire, TypeDescriptor.of(Status.class)))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage(UNKNOWN_PENDING);
    assertThat(format.read(wire, TypeDescriptor.of(Status.class).withEvolutionPolicy(TOLERANT)))
      .isError()
      .hasErrorMessage(UNKNOWN_PENDING)
      .hasExpectedTypeName(STATUS_NAME);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "enum-value-builtin", acceptance = "alias")
  @DisplayName("an old payload under the @EnumAlias name reads as the renamed constant, which writes its new name")
  void aliasReadsAsTheRenamedConstantAndRewritesTheCurrentName(RoundTripFormatHarness.Format format) {
    TypeDescriptor<Status> descriptor = TypeDescriptor.of(Status.class);
    byte[] oldWire = format.write(Value.of("INACTIVE"), wireNameOf(Status.class));

    Value<Status> reread = format.read(oldWire, descriptor);
    byte[] rewritten = format.write(reread, descriptor);

    assertThat(reread).isEqualTo(Value.of(Status.DISABLED));
    assertThat(format.read(rewritten, wireNameOf(Status.class))).hasStringValue("DISABLED");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "enum-value-builtin", acceptance = "policy")
  @DisplayName("STRICT and LENIENT both throw on an unknown constant; only ERROR_VALUE tolerates it")
  void onlyTheConstantPolicyToleratesAnUnknownConstant(RoundTripFormatHarness.Format format) {
    byte[] wire = format.write(Value.of("PENDING"), wireNameOf(Status.class));

    assertThatThrownBy(() ->
      format.read(wire, TypeDescriptor.of(Status.class).withEvolutionPolicy(EvolutionPolicy.STRICT))
    )
      .isInstanceOf(IllegalStateException.class)
      .hasMessage(UNKNOWN_PENDING);
    assertThatThrownBy(() ->
      format.read(wire, TypeDescriptor.of(Status.class).withEvolutionPolicy(EvolutionPolicy.LENIENT))
    )
      .isInstanceOf(IllegalStateException.class)
      .hasMessage(UNKNOWN_PENDING);
    assertThat(format.read(wire, TypeDescriptor.of(Status.class).withEvolutionPolicy(TOLERANT))).isEqualTo(
      Value.errorMessage(UNKNOWN_PENDING, TypeDescriptor.of(Status.class))
    );
  }

  @Test
  @JourneyTest(speakingId = "enum-value-builtin", acceptance = "policy")
  @DisplayName("a tolerated unknown constant in a checksummed binary document still verifies on close")
  void toleratedUnknownConstantKeepsTheBinaryChecksumInStep() {
    var protocol = ValueIo.framed().withChecksum(ChecksumAlgorithm.SHA_256);
    var bos = new ByteArrayOutputStream();
    try (var w = protocol.binaryWriter(bos)) {
      w.write(Value.of("PENDING"), wireNameOf(Status.class));
    }

    Value<Status> recovered;
    try (var r = protocol.binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
      recovered = r.read(TypeDescriptor.of(Status.class).withEvolutionPolicy(TOLERANT));
    }

    assertThat(recovered).isEqualTo(Value.errorMessage(UNKNOWN_PENDING, TypeDescriptor.of(Status.class)));
  }

  @Test
  @JourneyTest(speakingId = "enum-value-builtin", acceptance = "policy")
  @DisplayName("a tolerated unknown constant in a checksummed JSON document still verifies on close")
  void toleratedUnknownConstantKeepsTheJsonChecksumInStep() {
    var protocol = ValueIo.framed().withChecksum(ChecksumAlgorithm.SHA_256);
    var sw = new StringWriter();
    try (var w = protocol.jsonWriter(sw)) {
      w.write(Value.of("PENDING"), wireNameOf(Status.class));
    }

    Value<Status> recovered;
    try (var r = protocol.jsonReader(new StringReader(sw.toString()))) {
      recovered = r.read(TypeDescriptor.of(Status.class).withEvolutionPolicy(TOLERANT));
    }

    assertThat(recovered).isEqualTo(Value.errorMessage(UNKNOWN_PENDING, TypeDescriptor.of(Status.class)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "enum-value-builtin", acceptance = "policy")
  @DisplayName("in a list, only the unknown element becomes an ErrorValue when the element descriptor tolerates it")
  void unknownListElementBecomesAPerElementErrorValue(RoundTripFormatHarness.Format format) {
    byte[] wire = format.write(
      Value.listOf("ACTIVE", "PENDING", "ARCHIVED"),
      TypeDescriptor.ofList("statuses", wireNameOf(Status.class))
    );

    Value<List<Value<Status>>> tolerated = format.read(
      wire,
      TypeDescriptor.ofList("statuses", TypeDescriptor.of(Status.class).withEvolutionPolicy(TOLERANT))
    );

    assertThat(tolerated.unbox()).containsExactly(
      Value.of(Status.ACTIVE),
      Value.errorMessage(UNKNOWN_PENDING, TypeDescriptor.of(Status.class)),
      Value.of(Status.ARCHIVED)
    );
    assertThatThrownBy(() -> format.read(wire, TypeDescriptor.ofList("statuses", TypeDescriptor.of(Status.class))))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage(UNKNOWN_PENDING);
  }

  @Test
  @JourneyTest(speakingId = "enum-value-builtin", acceptance = "string")
  @DisplayName("convertTo(String) answers the constant name, never an overridden toString()")
  void enumConvertsToItsConstantName() {
    assertThat(Value.of(Status.ACTIVE).convertTo(String.class)).hasStringValue("ACTIVE");
    assertThat(Value.of(Colour.RED).convertTo(String.class)).hasStringValue("RED");
    assertThat(Value.of(Operation.MINUS).convertTo(String.class)).hasStringValue("MINUS");
  }

  private static TypeDescriptor<String> wireNameOf(Class<? extends Enum<?>> enumClass) {
    return TypeDescriptor.of(
      enumClass.getName(),
      String.class,
      reader -> reader.readString(0, "value"),
      (writer, name) -> writer.writeString(0, "value", name)
    );
  }

  public enum Status {
    ACTIVE,
    ARCHIVED,

    @EnumAlias("INACTIVE")
    DISABLED,
  }

  public enum Operation {
    PLUS {
      @Override
      int apply(int left, int right) {
        return left + right;
      }
    },
    MINUS {
      @Override
      int apply(int left, int right) {
        return left - right;
      }
    };

    abstract int apply(int left, int right);
  }

  public enum Colour {
    RED,
    GREEN;

    @Override
    public String toString() {
      return "colour " + name().toLowerCase(Locale.ROOT);
    }
  }

  public enum OrderV1 {
    A,
    B,
  }

  public enum OrderV2 {
    B,
    A,
  }

  public record Account(String owner, Status status) {}
}
