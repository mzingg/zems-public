package dev.zems.lib.value.marshal.format.json;

/**
 * Translation between caller-supplied slot {@code (id, name)} pairs and the JSON key actually emitted on the wire. Two
 * key namespaces coexist:
 *
 * <ul>
 * <li><b>Reserved envelope slots</b> — names that start with {@code $} ({@code $header},
 * {@code $payload}, {@code $terminator}) are emitted literally. They identify wire-level
 * infrastructure at the top of the document.</li>
 * <li><b>Reserved per-record metadata slots</b> — names that start with {@code __}
 * ({@code __type}, {@code __state}, {@code __errorClass}, {@code __errorMessage}) are also
 * emitted literally. They identify metadata inside a record alongside user slots.</li>
 * <li><b>User slots</b> — anything else is rewritten to {@code __slot<id>} (e.g. {@code __slot0},
 * {@code __slot1}). The numeric id is the wire identity; user-supplied slot names from
 * {@code SlotSpec.name()} never reach the wire. Renames are therefore free everywhere — change
 * the descriptor's slot name and the wire bytes are unchanged.</li>
 * </ul>
 *
 * <p>
 * The {@code __slot<id>} prefix is a minor readability win over bare numeric keys
 * ({@code "0"}, {@code "1"}) at zero wire cost: JSON keys are strings and the reader has to
 * parse them anyway, so a structured prefix stays cheap.
 *
 * <p>
 * Binary CBOR uses pure integer slot ids natively — no equivalent translation needed there.
 */
final class JsonWireKeys {

  /** Prefix used for user slots in the JSON wire — followed by the slot's numeric id. */
  static final String SLOT_PREFIX = "__slot";

  private JsonWireKeys() {}

  /**
   * Returns the JSON key that represents slot {@code (id, name)} on the wire: {@code name} when {@code name} starts
   * with {@code $} or {@code __} (reserved namespaces), {@code "__slot" + id} otherwise.
   */
  static String effectiveKey(int id, String name) {
    if (isReserved(name)) {
      return name;
    }
    return SLOT_PREFIX + id;
  }

  /** True when {@code name} is a reserved envelope ({@code $}) or metadata ({@code __}) name. */
  static boolean isReserved(String name) {
    if (name == null || name.isEmpty()) {
      return false;
    }
    return (name.charAt(0) == '$' || (name.length() >= 2 && name.charAt(0) == '_' && name.charAt(1) == '_'));
  }

  /**
   * Inverse of {@link #effectiveKey} for the user-slot range: parses {@code "__slotN"} back to {@code N}. Returns
   * {@code -1} when {@code key} is not in the user-slot range (e.g. a reserved name or an unrecognised string).
   */
  static int parseSlotId(String key) {
    if (key == null || !key.startsWith(SLOT_PREFIX)) {
      return -1;
    }
    try {
      return Integer.parseInt(key, SLOT_PREFIX.length(), key.length(), 10);
    } catch (NumberFormatException ignored) {
      return -1;
    }
  }
}
