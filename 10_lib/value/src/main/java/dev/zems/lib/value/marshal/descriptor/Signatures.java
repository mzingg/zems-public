package dev.zems.lib.value.marshal.descriptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Computes a stable structural signature for a {@link TypeDescriptor}. The signature is a 16-character hex prefix of
 * SHA-256 over a canonical UTF-8 encoding of the descriptor's shape inputs.
 *
 * <p>
 * Inputs are descriptor-shape only — primary slot names, child descriptor signatures, and descriptor identity.
 * Read-time concerns (aliases, defaults, evolution policy) are
 * <b>excluded</b>: they bridge a wire-shape mismatch on read but do not change the wire
 * shape themselves.
 *
 * <p>
 * The signature is deterministic across JVMs / build environments — pure function of descriptor reflection and slot
 * configuration, no timestamps or class-file hashes.
 */
final class Signatures {

  private static final HexFormat HEX = HexFormat.of();

  private Signatures() {}

  /** Computes a signature for a scalar descriptor, identified by its descriptor name. */
  static String forScalar(String descriptorName) {
    return hash("scalar|" + descriptorName);
  }

  /** Computes a signature for a list descriptor as {@code list[<elementSignature>]}. */
  static String forList(String descriptorName, String elementSignature) {
    return hash("list|" + descriptorName + "|" + elementSignature);
  }

  /** Computes a signature for a set descriptor as {@code set[<elementSignature>]}. */
  static String forSet(String descriptorName, String elementSignature) {
    return hash("set|" + descriptorName + "|" + elementSignature);
  }

  /** Computes a signature for a map descriptor as {@code map[<keySig>,<valueSig>]}. */
  static String forMap(String descriptorName, String keySignature, String valueSignature) {
    return hash("map|" + descriptorName + "|" + keySignature + "|" + valueSignature);
  }

  /** Computes a signature for a sorted-map descriptor as {@code sortedmap[<keySig>,<valueSig>]}. */
  static String forSortedMap(String descriptorName, String keySignature, String valueSignature) {
    return hash("sortedmap|" + descriptorName + "|" + keySignature + "|" + valueSignature);
  }

  /**
   * Computes a signature for a union (oneOf) descriptor from its ordered branch signatures. Branch order is part of the
   * wire contract (it is the discriminator), so the signatures are fed in declared order.
   */
  static String forUnion(String descriptorName, List<String> branchSignatures) {
    StringBuilder sb = new StringBuilder("union|").append(descriptorName);
    for (String branch : branchSignatures) {
      sb.append('|').append(branch);
    }
    return hash(sb.toString());
  }

  /**
   * Computes a signature for a structured (record-shaped) descriptor from its slot list. Each entry is the slot's id
   * followed by the slot descriptor's signature. Slot id is the cross-format wire identity (binary wire-anchored), so
   * feeding ids keeps the signature stable across renames that preserve ids.
   */
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
      // 8-byte truncation → 16 hex chars
      return HEX.formatHex(full, 0, 8);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  /** Lightweight tuple for {@link #forStructured} to avoid pulling in the full SlotSpec type here. */
  record SlotEntry(int id, String signature) {}
}
