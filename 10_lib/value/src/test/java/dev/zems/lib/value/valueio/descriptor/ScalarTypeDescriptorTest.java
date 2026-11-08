package dev.zems.lib.value.valueio.descriptor;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.Quantity;
import dev.zems.lib.value.Units;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.junit.RoundTripFormatHarness;
import dev.zems.lib.value.valueio.ChecksumAlgorithm;
import dev.zems.lib.value.valueio.ValueIo;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ContractTest
@DisplayName("ScalarTypeDescriptor")
class ScalarTypeDescriptorTest {

  private static final String QUANTITY_NAME = "dev.zems.lib.value.Quantity";
  private static final String FURLONG = "5 furlong";
  private static final String UNKNOWN_FURLONG = "Invalid quantity '5 furlong': unknown unit symbol 'furlong'";

  static Stream<RoundTripFormatHarness.Format> formats() {
    return RoundTripFormatHarness.all();
  }

  @Nested
  @DisplayName("a tolerant text descriptor")
  class TolerantText {

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.zems.lib.value.valueio.descriptor.ScalarTypeDescriptorTest#formats")
    @DisplayName("answers the parsed value for good text at the top level")
    void goodTextReadsAsTheValue(RoundTripFormatHarness.Format format) {
      byte[] wire = format.write(Value.of("4.50 EUR/kg"), textNamed(QUANTITY_NAME));

      assertThat(format.read(wire, BuiltinTypeDescriptors.QUANTITY)).isEqualTo(Value.measurableOf("4.50 EUR/kg"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.zems.lib.value.valueio.descriptor.ScalarTypeDescriptorTest#formats")
    @DisplayName("answers an ErrorValue for bad text at the top level")
    void badTextAtTheTopLevelIsAnErrorValue(RoundTripFormatHarness.Format format) {
      byte[] wire = format.write(Value.of(FURLONG), textNamed(QUANTITY_NAME));

      assertThat(format.read(wire, BuiltinTypeDescriptors.QUANTITY))
        .isError()
        .hasErrorMessage(UNKNOWN_FURLONG)
        .hasExpectedTypeName(QUANTITY_NAME);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.zems.lib.value.valueio.descriptor.ScalarTypeDescriptorTest#formats")
    @DisplayName("answers an ErrorValue for a bad set element")
    void aBadSetElementIsAnErrorValue(RoundTripFormatHarness.Format format) {
      byte[] wire = format.write(Value.setOf(FURLONG), TypeDescriptor.ofSet("quantities", textNamed(QUANTITY_NAME)));

      Value<Set<Value<Quantity>>> read = format.read(
        wire,
        TypeDescriptor.ofSet("quantities", BuiltinTypeDescriptors.QUANTITY)
      );

      assertThat(read.unbox()).containsExactly(Value.errorMessage(UNKNOWN_FURLONG, Quantity.class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.zems.lib.value.valueio.descriptor.ScalarTypeDescriptorTest#formats")
    @DisplayName("answers an ErrorValue for a bad map value, next to a good one")
    void aBadMapValueIsAnErrorValue(RoundTripFormatHarness.Format format) {
      byte[] wire = format.write(
        Value.mapOf(Map.entry("good", "5 m"), Map.entry("bad", FURLONG)),
        TypeDescriptor.ofMap("weights", BuiltinTypeDescriptors.STRING, textNamed(QUANTITY_NAME))
      );

      Value<Map<String, Value<Quantity>>> read = format.read(
        wire,
        TypeDescriptor.ofMap("weights", BuiltinTypeDescriptors.STRING, BuiltinTypeDescriptors.QUANTITY)
      );

      assertThat(read.unbox()).containsExactlyInAnyOrderEntriesOf(
        Map.of("good", Value.of(5, Units.METRE), "bad", Value.errorMessage(UNKNOWN_FURLONG, Quantity.class))
      );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.zems.lib.value.valueio.descriptor.ScalarTypeDescriptorTest#formats")
    @DisplayName("throws for bad text in a record component, a raw position")
    void badTextInARecordComponentThrows(RoundTripFormatHarness.Format format) {
      byte[] wire = format.write(Value.of(new Parcel<>(FURLONG)), parcelOf(textNamed(QUANTITY_NAME)));

      assertThatThrownBy(() -> format.read(wire, parcelOf(BuiltinTypeDescriptors.QUANTITY)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(UNKNOWN_FURLONG);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.zems.lib.value.valueio.descriptor.ScalarTypeDescriptorTest#formats")
    @DisplayName("throws for bad text in a map key, a raw position")
    void badTextInAMapKeyThrows(RoundTripFormatHarness.Format format) {
      byte[] wire = format.write(
        Value.mapOf(Map.entry(FURLONG, "heavy")),
        TypeDescriptor.ofMap("labels", textNamed(QUANTITY_NAME), BuiltinTypeDescriptors.STRING)
      );

      assertThatThrownBy(() ->
        format.read(
          wire,
          TypeDescriptor.ofMap("labels", BuiltinTypeDescriptors.QUANTITY, BuiltinTypeDescriptors.STRING)
        )
      )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(UNKNOWN_FURLONG);
    }

    @Test
    @DisplayName("keeps its tolerance through withAliases and withEvolutionPolicy")
    void theToleranceSurvivesTheCopies() {
      RoundTripFormatHarness.Format binary = RoundTripFormatHarness.binary();
      byte[] wire = binary.write(Value.of(FURLONG), textNamed(QUANTITY_NAME));

      assertThat(binary.read(wire, BuiltinTypeDescriptors.QUANTITY.withAliases("legacy.Quantity")))
        .isError()
        .hasErrorMessage(UNKNOWN_FURLONG);
      assertThat(binary.read(wire, BuiltinTypeDescriptors.QUANTITY.withEvolutionPolicy(EvolutionPolicy.LENIENT)))
        .isError()
        .hasErrorMessage(UNKNOWN_FURLONG);
    }

    @Test
    @DisplayName("keeps its tolerance when adopted under another name by TypeDescriptor.of(name, Quantity.class)")
    void theToleranceSurvivesAdoptionUnderAnotherName() {
      RoundTripFormatHarness.Format json = RoundTripFormatHarness.json();
      byte[] wire = json.write(Value.of(FURLONG), textNamed("shop.Price"));

      assertThat(json.read(wire, TypeDescriptor.of("shop.Price", Quantity.class)))
        .isError()
        .hasErrorMessage(UNKNOWN_FURLONG)
        .hasExpectedTypeName(QUANTITY_NAME);
    }

    @Test
    @DisplayName("a tolerated error keeps the checksum and type verification in step")
    void aToleratedErrorKeepsTheEnvelopeInStep() {
      var protocol = ValueIo.framed().withChecksum(ChecksumAlgorithm.SHA_256).usingTypeVerification();
      var bos = new ByteArrayOutputStream();
      try (var w = protocol.binaryWriter(bos)) {
        w.write(Value.of(FURLONG), textNamed(QUANTITY_NAME));
      }

      Value<Quantity> read;
      try (var r = protocol.binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        read = r.read(BuiltinTypeDescriptors.QUANTITY);
      }

      assertThat(read).isError().hasErrorMessage(UNKNOWN_FURLONG);
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @DisplayName("a descriptor without a tolerant parse still throws for bad text at the top level")
  void aPlainDescriptorStillThrows(RoundTripFormatHarness.Format format) {
    byte[] wire = format.write(Value.of("not-a-number"), textNamed(BigDecimal.class.getName()));

    assertThatThrownBy(() -> format.read(wire, BuiltinTypeDescriptors.BIG_DECIMAL))
      .isInstanceOf(NumberFormatException.class)
      .hasMessage("Character n is neither a decimal digit number, decimal point, nor \"e\" notation exponential mark.");
  }

  private static TypeDescriptor<String> textNamed(String descriptorName) {
    return TypeDescriptor.of(
      descriptorName,
      String.class,
      reader -> reader.readString(0, "value"),
      (writer, text) -> writer.writeString(0, "value", text)
    );
  }

  private static <W> TypeDescriptor<Parcel<W>> parcelOf(TypeDescriptor<W> weight) {
    @SuppressWarnings("unchecked")
    Class<Parcel<W>> parcelClass = (Class<Parcel<W>>) (Class<?>) Parcel.class;
    return TypeDescriptor.of(
      "parcel",
      parcelClass,
      reader -> new Parcel<>(reader.readRecord(0, "weight", weight)),
      (writer, parcel) -> writer.writeRecord(0, "weight", weight, parcel.weight())
    );
  }

  record Parcel<W>(W weight) {}
}
