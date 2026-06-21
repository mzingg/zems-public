package dev.zems.lib.value.marshal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.ContractTest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Verifies the new {@link ChecksumComputation} helper feeds bytes the same way as today's
 * {@code ChecksumStateWriter}/{@code ChecksumStateReader} decorators (which it will replace), so the migration to
 * {@link AbstractStateWriter}/{@link AbstractStateReader} preserves the end-to-end checksum digest.
 */
@DisplayName("ChecksumComputation")
@ContractTest
class ChecksumComputationTest {

  /**
   * After replacing {@code value.getBytes(UTF_8)} with a reusable {@link java.nio.charset.CharsetEncoder}, the digest
   * of {@code feedString(s)} must remain byte-identical to {@code MessageDigest.update(s.getBytes(UTF_8))} across
   * ASCII, multi-byte UTF-8, surrogate pairs, empty strings, and strings larger than the internal 1 KiB chunk buffer.
   */
  static Stream<String> feedString_corpus() {
    return Stream.of("", "a", "java.lang.String", "café — éléphant 漢字 🦄", "x".repeat(2049), "Ω".repeat(513));
  }

  /**
   * Single feedDescriptorName must produce the same digest as raw SHA-256 of the descriptor name's UTF-8 bytes.
   * Reproduces the call ChecksumStateWriter makes inside writeRecord(descriptor, value).
   */
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

  /** Mirror the writeBoolean(id, name, value) feed sequence: varint slot id, then 1-byte value. */
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

  /** Mirror the writeInt(id, name, value) feed sequence: varint slot id, then 4-byte big-endian int. */
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

  /** State markers feed a single tag byte (1=NULL, 2=UNDEFINED, 3=UNRESOLVED, 4=ERROR). */
  @Test
  void feedStateTag_one_isJustOneByte() throws Exception {
    var c = new ChecksumComputation(ChecksumAlgorithm.SHA_256);
    c.feedStateTag(1);
    String actual = c.hex();

    var direct = MessageDigest.getInstance("SHA-256");
    direct.update(new byte[] { 0x01 });
    String expected = HexFormat.of().formatHex(direct.digest());

    assertThat(actual).isEqualTo(expected);
  }

  /** Suspend/resume must skip feeding bytes between the two calls. */
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

  /** Reused instance must produce the same digest sequence as a fresh instance — the encoder reset() runs every call. */
  @Test
  void feedString_reusable_produces_independent_digests() throws Exception {
    var c = new ChecksumComputation(ChecksumAlgorithm.SHA_256);
    c.feedString("ignored");
    // Reset the digest by reading hex (consumes & resets MessageDigest); start a fresh logical stream.
    c.hex();
    c.feedString("漢字");
    String actual = c.hex();

    var direct = MessageDigest.getInstance("SHA-256");
    direct.update("漢字".getBytes(StandardCharsets.UTF_8));
    String expected = HexFormat.of().formatHex(direct.digest());

    assertThat(actual).isEqualTo(expected);
  }

  /** SHA-512 is a different algorithm — digest must differ from SHA-256 for the same input. */
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
