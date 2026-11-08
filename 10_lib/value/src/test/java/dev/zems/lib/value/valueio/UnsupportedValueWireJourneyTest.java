package dev.zems.lib.value.valueio;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.JourneyTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.junit.RoundTripFormatHarness;
import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("UnsupportedValue on the wire — its own representation in both formats")
class UnsupportedValueWireJourneyTest {

  private static final String REASON = "Cannot convert UUID to Integer: no conversion exists";

  /** RFC 8949 §3.4: tag 49158 ({@code CborTag.STATE_UNSUPPORTED}), encoded with a two-byte argument. */
  private static final String STATE_UNSUPPORTED_TAG = "d9c006";

  /** RFC 8949 §3.4: tag 49155 ({@code CborTag.STATE_ERROR}), encoded with a two-byte argument. */
  private static final String STATE_ERROR_TAG = "d9c003";

  /** RFC 8949 §3.4: tag 49159, one past the state-marker range, patched in to stand for an unknown state. */
  private static final String UNKNOWN_STATE_TAG = "d9c007";

  static Stream<RoundTripFormatHarness.Format> formats() {
    return RoundTripFormatHarness.all();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "unsupported-value-state", acceptance = "a4")
  @DisplayName("an unsupported marker round-trips with its reason and its expected-type descriptor")
  void roundTripsWithReasonAndExpectedType(RoundTripFormatHarness.Format format) {
    Value<String> marker = Value.unsupported(REASON, String.class);

    byte[] wire = format.write(marker, BuiltinTypeDescriptors.STRING);
    Value<String> recovered = format.read(wire, BuiltinTypeDescriptors.STRING);

    assertThat(recovered).isUnsupported().hasUnsupportedReason(REASON).hasExpectedTypeName("java.lang.String");
    assertThat(recovered).isEqualTo(marker);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "unsupported-value-state", acceptance = "a4")
  @DisplayName("an unsupported element inside a list round-trips next to a present one")
  void roundTripsAsAListElement(RoundTripFormatHarness.Format format) {
    TypeDescriptor<List<Value<String>>> listDescriptor = TypeDescriptor.ofList(
      "List<String>",
      BuiltinTypeDescriptors.STRING
    );
    Value<List<Value<String>>> list = Value.listOfTyped(
      BuiltinTypeDescriptors.STRING,
      Value.of("a"),
      Value.unsupported(REASON, String.class)
    );

    byte[] wire = format.write(list, listDescriptor);
    List<Value<String>> recovered = format.read(wire, listDescriptor).unbox();

    assertThat(recovered.getFirst()).hasStringValue("a");
    assertThat(recovered.get(1)).isUnsupported().hasUnsupportedReason(REASON);
    assertThat(recovered.get(1)).isEqualTo(Value.unsupported(REASON, String.class));
  }

  @Test
  @JourneyTest(speakingId = "unsupported-value-state", acceptance = "a4")
  @DisplayName("the binary form carries its own tag, distinct from the error tag")
  void binaryFormUsesItsOwnTag() {
    byte[] unsupportedWire = RoundTripFormatHarness.binary().write(
      Value.unsupported(REASON, String.class),
      BuiltinTypeDescriptors.STRING
    );
    byte[] errorWire = RoundTripFormatHarness.binary().write(
      Value.errorMessage(REASON, String.class),
      BuiltinTypeDescriptors.STRING
    );

    String unsupportedHex = HexFormat.of().formatHex(unsupportedWire);
    String errorHex = HexFormat.of().formatHex(errorWire);

    assertThat(unsupportedHex).contains(STATE_UNSUPPORTED_TAG).doesNotContain(STATE_ERROR_TAG);
    assertThat(errorHex).contains(STATE_ERROR_TAG).doesNotContain(STATE_UNSUPPORTED_TAG);
  }

  @Test
  @JourneyTest(speakingId = "unsupported-value-state", acceptance = "a4")
  @DisplayName("the JSON form names the state and carries the reason in its own field")
  void jsonFormNamesTheStateAndTheReason() {
    byte[] wire = RoundTripFormatHarness.json().write(
      Value.unsupported(REASON, String.class),
      BuiltinTypeDescriptors.STRING
    );

    String json = new String(wire, StandardCharsets.UTF_8);

    assertThat(json)
      .contains("\"__state\":\"UNSUPPORTED\"")
      .contains("\"__reason\":\"" + REASON + "\"")
      .doesNotContain("__errorClass")
      .doesNotContain("__errorMessage");
  }

  @Test
  @JourneyTest(speakingId = "unsupported-value-state", acceptance = "a4")
  @DisplayName("a writer with no explicit descriptor takes the expected type from the marker itself")
  void inferredWriteTakesTheDescriptorFromTheMarker() {
    var bos = new ByteArrayOutputStream();
    try (var w = ValueIo.framed().binaryWriter(bos)) {
      w.write(Value.unsupported(REASON, String.class));
    }

    Value<String> recovered;
    try (var r = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
      recovered = r.read(BuiltinTypeDescriptors.STRING);
    }

    assertThat(recovered).isUnsupported().hasUnsupportedReason(REASON);
  }

  @Test
  @JourneyTest(speakingId = "unsupported-value-state", acceptance = "a4")
  @DisplayName("a checksummed framed binary document verifies and reads the marker back")
  void checksummedBinaryDocumentVerifies() {
    var protocol = ValueIo.framed().withChecksum(ChecksumAlgorithm.SHA_256);
    var bos = new ByteArrayOutputStream();
    try (var w = protocol.binaryWriter(bos)) {
      w.write(Value.unsupported(REASON, String.class), BuiltinTypeDescriptors.STRING);
    }

    Value<String> recovered;
    try (var r = protocol.binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
      recovered = r.read(BuiltinTypeDescriptors.STRING);
    }

    assertThat(recovered).isUnsupported().hasUnsupportedReason(REASON);
  }

  @Test
  @JourneyTest(speakingId = "unsupported-value-state", acceptance = "a4")
  @DisplayName("a checksummed framed JSON document verifies and reads the marker back")
  void checksummedJsonDocumentVerifies() {
    var protocol = ValueIo.framed().withChecksum(ChecksumAlgorithm.SHA_256);
    var sw = new StringWriter();
    try (var w = protocol.jsonWriter(sw)) {
      w.write(Value.unsupported(REASON, String.class), BuiltinTypeDescriptors.STRING);
    }

    Value<String> recovered;
    try (var r = protocol.jsonReader(new StringReader(sw.toString()))) {
      recovered = r.read(BuiltinTypeDescriptors.STRING);
    }

    assertThat(recovered).isUnsupported().hasUnsupportedReason(REASON);
  }

  @Test
  @JourneyTest(speakingId = "unsupported-value-state", acceptance = "a6")
  @DisplayName("a JSON reader refuses an unsupported marker with no reason rather than inventing one")
  void jsonReaderRefusesAMarkerWithoutAReason() {
    String json = "{\"$payload\":{\"__state\":\"UNSUPPORTED\"}}";

    try (var r = ValueIo.streaming().jsonReader(new StringReader(json))) {
      r.hasMoreRecords();
      assertThatThrownBy(() -> r.read(BuiltinTypeDescriptors.STRING))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unsupported marker at '$payload' is missing \"__reason\"");
    }
  }

  @Test
  @JourneyTest(speakingId = "unsupported-value-state", acceptance = "a6")
  @DisplayName("a JSON reader refuses a state name it does not know, naming the state it found")
  void jsonReaderRefusesAnUnknownStateName() {
    String json = "{\"$payload\":{\"__state\":\"BOGUS\"}}";

    try (var r = ValueIo.streaming().jsonReader(new StringReader(json))) {
      r.hasMoreRecords();
      assertThatThrownBy(() -> r.read(BuiltinTypeDescriptors.STRING))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unknown state value 'BOGUS' at '$payload'");
    }
  }

  @Test
  @JourneyTest(speakingId = "unsupported-value-state", acceptance = "a6")
  @DisplayName("a binary reader refuses a state tag it does not know, naming the tag and the range it expected")
  void binaryReaderRefusesAnUnknownStateTag() {
    var bos = new ByteArrayOutputStream();
    try (var w = ValueIo.streaming().binaryWriter(bos)) {
      w.write(Value.unsupported(REASON, String.class), BuiltinTypeDescriptors.STRING);
    }

    String hex = HexFormat.of().formatHex(bos.toByteArray());
    int tagAt = hex.indexOf(STATE_UNSUPPORTED_TAG);
    assertThat(tagAt).as("premise: the writer emitted the unsupported tag on a byte boundary").isNotNegative().isEven();
    byte[] patched = HexFormat.of().parseHex(
      hex.substring(0, tagAt) + UNKNOWN_STATE_TAG + hex.substring(tagAt + STATE_UNSUPPORTED_TAG.length())
    );

    try (var r = ValueIo.streaming().binaryReader(new ByteArrayInputStream(patched))) {
      assertThatThrownBy(() -> r.read(BuiltinTypeDescriptors.STRING))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
          "unknown CBOR tag 49159 at '$payload': expected a state-marker tag (49152-49158) " +
            "or the type-verify tag (49157)"
        );
    }
  }
}
