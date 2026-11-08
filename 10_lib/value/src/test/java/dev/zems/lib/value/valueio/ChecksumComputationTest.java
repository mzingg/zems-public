package dev.zems.lib.value.valueio;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.ValueState;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("ChecksumComputation")
@ContractTest
class ChecksumComputationTest {

  static Stream<String> feedString_corpus() {
    return Stream.of("", "a", "java.lang.String", "café — éléphant 漢字 🦄", "x".repeat(2049), "Ω".repeat(513));
  }

  @Test
  void feedDescriptorName_matchesRawSha256() throws Exception {
    var c = new ChecksumComputation(ChecksumAlgorithm.SHA_256);
    c.feedDescriptorName("java.lang.String");
    String actual = c.hex();

    var direct = MessageDigest.getInstance("SHA-256");
    direct.update("java.lang.String".getBytes(StandardCharsets.UTF_8));
    String expected = HexFormat.of().formatHex(direct.digest());

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void feedBoolean_matchesIdVarintPlusByte() throws Exception {
    var c = new ChecksumComputation(ChecksumAlgorithm.SHA_256);
    c.feedSlotId(0);
    c.feedBoolean(true);
    String actual = c.hex();

    var direct = MessageDigest.getInstance("SHA-256");
    direct.update(new byte[] { 0 });
    direct.update(new byte[] { 1 });
    String expected = HexFormat.of().formatHex(direct.digest());

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void feedInt_matchesIdVarintPlusFourBytes() throws Exception {
    var c = new ChecksumComputation(ChecksumAlgorithm.SHA_256);
    c.feedSlotId(2);
    c.feedInt(0x01020304);
    String actual = c.hex();

    var direct = MessageDigest.getInstance("SHA-256");
    direct.update(new byte[] { 2 });
    direct.update(new byte[] { 0x01, 0x02, 0x03, 0x04 });
    String expected = HexFormat.of().formatHex(direct.digest());

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void feedState_null_isJustOneByte() throws Exception {
    var c = new ChecksumComputation(ChecksumAlgorithm.SHA_256);
    c.feedState(ValueState.NULL);
    String actual = c.hex();

    var direct = MessageDigest.getInstance("SHA-256");
    direct.update(new byte[] { 0x01 });
    String expected = HexFormat.of().formatHex(direct.digest());

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void suspend_skipsBytes() {
    var control = new ChecksumComputation(ChecksumAlgorithm.SHA_256);
    control.feedString("a");
    String controlHex = control.hex();

    var c = new ChecksumComputation(ChecksumAlgorithm.SHA_256);
    c.feedString("a");
    c.suspend();
    c.feedString("ignored-while-suspended");
    c.feedInt(999);
    c.resume();
    String afterSuspend = c.hex();

    assertThat(afterSuspend).isEqualTo(controlHex);
  }

  @ParameterizedTest(name = "[{index}] feedString digest equals raw SHA-256 of UTF-8 bytes")
  @MethodSource("feedString_corpus")
  void feedString_matchesRawSha256(String value) throws Exception {
    var c = new ChecksumComputation(ChecksumAlgorithm.SHA_256);
    c.feedString(value);
    String actual = c.hex();

    var direct = MessageDigest.getInstance("SHA-256");
    direct.update(value.getBytes(StandardCharsets.UTF_8));
    String expected = HexFormat.of().formatHex(direct.digest());

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void feedString_reusable_produces_independent_digests() throws Exception {
    var c = new ChecksumComputation(ChecksumAlgorithm.SHA_256);
    c.feedString("ignored");
    c.hex();
    c.feedString("漢字");
    String actual = c.hex();

    var direct = MessageDigest.getInstance("SHA-256");
    direct.update("漢字".getBytes(StandardCharsets.UTF_8));
    String expected = HexFormat.of().formatHex(direct.digest());

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void differentAlgorithm_producesDifferentDigest() {
    var c256 = new ChecksumComputation(ChecksumAlgorithm.SHA_256);
    var c512 = new ChecksumComputation(ChecksumAlgorithm.SHA_512);
    c256.feedString("payload");
    c512.feedString("payload");
    assertThat(c256.hex()).isNotEqualTo(c512.hex());
    assertThat(c256.hex()).hasSize(64);
    assertThat(c512.hex()).hasSize(128);
  }
}
