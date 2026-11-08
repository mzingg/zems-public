package dev.zems.lib.value.valueio.descriptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Structural signature of a {@link TypeDescriptor}: the first 16 hex chars of SHA-256 over its shape inputs. */
final class Signatures {

  private static final HexFormat HEX = HexFormat.of();
  private static final int SIGNATURE_BYTES = 8;

  private Signatures() {}

  static String forScalar(String descriptorName) {
    return hash("scalar|" + descriptorName);
  }

  static String forList(String descriptorName, String elementSignature) {
    return hash("list|" + descriptorName + "|" + elementSignature);
  }

  static String forSet(String descriptorName, String elementSignature) {
    return hash("set|" + descriptorName + "|" + elementSignature);
  }

  static String forMap(String descriptorName, String keySignature, String valueSignature) {
    return hash("map|" + descriptorName + "|" + keySignature + "|" + valueSignature);
  }

  static String forSortedMap(String descriptorName, String keySignature, String valueSignature) {
    return hash("sortedmap|" + descriptorName + "|" + keySignature + "|" + valueSignature);
  }

  static String forUnion(String descriptorName, List<String> branchSignatures) {
    StringBuilder sb = new StringBuilder("union|").append(descriptorName);
    for (String branch : branchSignatures) {
      sb.append('|').append(branch);
    }
    return hash(sb.toString());
  }

  static String forStructured(String descriptorName, List<SlotEntry> slots) {
    StringBuilder sb = new StringBuilder("struct|").append(descriptorName);
    for (SlotEntry slot : slots) {
      sb.append('|').append(slot.id()).append('=').append(slot.signature());
    }
    return hash(sb.toString());
  }

  private static String hash(String input) {
    try {
      MessageDigest sha = MessageDigest.getInstance("SHA-256");
      byte[] full = sha.digest(input.getBytes(StandardCharsets.UTF_8));
      return HEX.formatHex(full, 0, SIGNATURE_BYTES);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  record SlotEntry(int id, String signature) {}
}
