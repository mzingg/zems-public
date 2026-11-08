package dev.zems.lib.value.valueio.descriptor;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.junit.RoundTripFormatHarness;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ContractTest
@DisplayName("EnumTypeDescriptor")
class EnumTypeDescriptorTest {

  private static final String OUTER = "dev.zems.lib.value.valueio.descriptor.EnumTypeDescriptorTest$";
  private static final String UNKNOWN_PENDING = "Unknown constant 'PENDING' for enum " + OUTER + "Status";
  private static final EvolutionPolicy TOLERANT = new EvolutionPolicy(
    UnknownSlotPolicy.FAIL,
    UnknownConstantPolicy.ERROR_VALUE
  );

  static Stream<RoundTripFormatHarness.Format> formats() {
    return RoundTripFormatHarness.all();
  }

  private static TypeDescriptor<String> wireNameOf(Class<? extends Enum<?>> enumClass) {
    return TypeDescriptor.of(
      enumClass.getName(),
      String.class,
      reader -> reader.readString(0, "value"),
      (writer, name) -> writer.writeString(0, "value", name)
    );
  }

  @Nested
  @DisplayName("synthesis refusals")
  class SynthesisRefusals {

    @Test
    @DisplayName("a blank alias is refused, naming the constant")
    void blankAliasIsRefused() {
      assertThatThrownBy(() -> TypeDescriptor.find(BlankAlias.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Blank @EnumAlias on " + OUTER + "BlankAlias.SECOND");
    }

    @Test
    @DisplayName("an alias equal to another constant's name is ambiguous")
    void aliasClashingWithAnotherConstantNameIsRefused() {
      assertThatThrownBy(() -> TypeDescriptor.find(AliasIsAnotherName.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Ambiguous wire name 'ACTIVE' in enum " + OUTER + "AliasIsAnotherName: ACTIVE and ARCHIVED");
    }

    @Test
    @DisplayName("one alias on two constants is ambiguous; the constants are named in declaration order")
    void aliasSharedByTwoConstantsIsRefused() {
      assertThatThrownBy(() -> TypeDescriptor.find(SharedAlias.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Ambiguous wire name 'OLD' in enum " + OUTER + "SharedAlias: FIRST and SECOND");
    }

    @Test
    @DisplayName("an alias on an earlier constant naming a later one still lists them in declaration order")
    void forwardClashListsConstantsInDeclarationOrder() {
      assertThatThrownBy(() -> TypeDescriptor.find(ForwardClash.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Ambiguous wire name 'LATER' in enum " + OUTER + "ForwardClash: EARLIER and LATER");
    }

    @Test
    @DisplayName("a refusal is not cached as an empty answer: every lookup refuses again")
    void refusalRepeatsOnEveryLookup() {
      assertThatThrownBy(() -> TypeDescriptor.find(SharedAlias.class)).isInstanceOf(IllegalStateException.class);
      assertThatThrownBy(() -> TypeDescriptor.find(SharedAlias.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Ambiguous wire name 'OLD' in enum " + OUTER + "SharedAlias: FIRST and SECOND");
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @DisplayName("an alias equal to its own constant's name is accepted and reads as that constant")
  void selfAliasIsAccepted(RoundTripFormatHarness.Format format) {
    byte[] wire = format.write(Value.of("ONLY"), wireNameOf(SelfAlias.class));

    assertThat(format.read(wire, TypeDescriptor.of(SelfAlias.class))).hasEnumValue(SelfAlias.ONLY);
  }

  @Test
  @DisplayName("an enum's own DESCRIPTOR field wins over synthesis, for find and for the value's type")
  void descriptorFieldWinsOverSynthesis() {
    assertThat(TypeDescriptor.find(WithDescriptor.class)).hasValueSatisfying(found ->
      assertThat(found).isSameAs(WithDescriptor.DESCRIPTOR)
    );
    assertThat(Value.of(WithDescriptor.B).valueType()).isSameAs(WithDescriptor.DESCRIPTOR);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @DisplayName("a map key is a raw position: an unknown constant throws even under ERROR_VALUE")
  void unknownConstantAsMapKeyThrowsUnderErrorValue(RoundTripFormatHarness.Format format) {
    byte[] wire = format.write(
      Value.mapOf(Map.entry("PENDING", "x")),
      TypeDescriptor.ofMap("byStatus", wireNameOf(Status.class), BuiltinTypeDescriptors.STRING)
    );
    TypeDescriptor<Map<Status, Value<String>>> tolerantKeys = TypeDescriptor.ofMap(
      "byStatus",
      TypeDescriptor.of(Status.class).withEvolutionPolicy(TOLERANT),
      BuiltinTypeDescriptors.STRING
    );

    assertThatThrownBy(() -> format.read(wire, tolerantKeys))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage(UNKNOWN_PENDING);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @DisplayName("a union payload is a raw position: an unknown constant throws even under ERROR_VALUE")
  void unknownConstantAsUnionPayloadThrowsUnderErrorValue(RoundTripFormatHarness.Format format) {
    byte[] wire = format.write(Value.of((Object) "PENDING"), TypeDescriptor.oneOf("either", wireNameOf(Status.class)));
    TypeDescriptor<Object> tolerantUnion = TypeDescriptor.oneOf(
      "either",
      TypeDescriptor.of(Status.class).withEvolutionPolicy(TOLERANT)
    );

    assertThatThrownBy(() -> format.read(wire, tolerantUnion))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage(UNKNOWN_PENDING);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @SuppressWarnings("unchecked")
  @DisplayName("a record component is a raw position: an unknown constant throws even under ERROR_VALUE")
  void unknownConstantAsRecordComponentThrowsUnderErrorValue(RoundTripFormatHarness.Format format) {
    byte[] wire = format.write(
      Value.of(new AccountWire("ada", new StatusWire("PENDING"))),
      TypeDescriptor.of(AccountWire.class)
    );
    StructuredTypeDescriptor<Account> synthesised = (StructuredTypeDescriptor<Account>) TypeDescriptor.of(
      Account.class
    );
    StructuredTypeDescriptor<Account> tolerantStatus = synthesised.withSlot("status", slot ->
      ((SlotConfigurer<Status>) slot).descriptor(TypeDescriptor.of(Status.class).withEvolutionPolicy(TOLERANT))
    );

    assertThatThrownBy(() -> format.read(wire, tolerantStatus))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage(UNKNOWN_PENDING);
  }

  @Test
  @DisplayName("withAliases and withEvolutionPolicy each keep the other setting and leave the original untouched")
  void withersKeepTheOtherSetting() {
    TypeDescriptor<Status> original = TypeDescriptor.of(Status.class);

    TypeDescriptor<Status> policyThenAliases = original.withEvolutionPolicy(TOLERANT).withAliases("com.example.Old");
    TypeDescriptor<Status> aliasesThenPolicy = original.withAliases("com.example.Old").withEvolutionPolicy(TOLERANT);

    assertThat(policyThenAliases.evolutionPolicy()).isEqualTo(TOLERANT);
    assertThat(policyThenAliases.nameAliases()).containsExactly("com.example.Old");
    assertThat(aliasesThenPolicy.evolutionPolicy()).isEqualTo(TOLERANT);
    assertThat(aliasesThenPolicy.nameAliases()).containsExactly("com.example.Old");
    assertThat(original.evolutionPolicy()).isEqualTo(EvolutionPolicy.STRICT);
    assertThat(original.nameAliases()).isEmpty();
  }

  @Test
  @DisplayName("equality is on descriptor name and enum class; aliases and the policy do not count")
  void equalityIgnoresAliasesAndPolicy() {
    TypeDescriptor<Status> plain = TypeDescriptor.of(Status.class);
    TypeDescriptor<Status> customised = plain.withAliases("com.example.Old").withEvolutionPolicy(TOLERANT);

    assertThat(customised).isEqualTo(plain).hasSameHashCodeAs(plain);
    assertThat(TypeDescriptor.of(SelfAlias.class)).isNotEqualTo(plain);
    assertThat(TypeDescriptor.of(Status.class.getName(), Status.class)).isNotEqualTo(plain);
  }

  public enum Status {
    ACTIVE,
    ARCHIVED,
  }

  public record Account(String owner, Status status) {}

  public record StatusWire(String value) {}

  public record AccountWire(String owner, StatusWire status) {}

  public enum BlankAlias {
    FIRST,

    @EnumAlias(" ")
    SECOND,
  }

  public enum AliasIsAnotherName {
    ACTIVE,

    @EnumAlias("ACTIVE")
    ARCHIVED,
  }

  public enum SharedAlias {
    @EnumAlias("OLD")
    FIRST,

    @EnumAlias("OLD")
    SECOND,
  }

  public enum ForwardClash {
    @EnumAlias("LATER")
    EARLIER,

    LATER,
  }

  public enum SelfAlias {
    @EnumAlias("ONLY")
    ONLY,

    OTHER,
  }

  public enum WithDescriptor {
    A,
    B;

    public static final TypeDescriptor<WithDescriptor> DESCRIPTOR = TypeDescriptor.of(
      "custom.WithDescriptor",
      WithDescriptor.class,
      reader -> values()[reader.readInt(0, "ordinal")],
      (writer, value) -> writer.writeInt(0, "ordinal", value.ordinal())
    );
  }
}
