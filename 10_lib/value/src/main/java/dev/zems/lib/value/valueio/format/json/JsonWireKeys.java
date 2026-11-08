package dev.zems.lib.value.valueio.format.json;

/**
 * Maps a slot {@code (id, name)} to its JSON key: reserved {@code $...} and {@code __...} names pass through literally,
 * every other slot becomes {@code __slot<id>}.
 */
final class JsonWireKeys {

  static final String SLOT_PREFIX = "__slot";

  private JsonWireKeys() {}

  static String effectiveKey(int id, String name) {
    if (isReserved(name)) {
      return name;
    }
    return SLOT_PREFIX + id;
  }

  static boolean isReserved(String name) {
    if (name == null || name.isEmpty()) {
      return false;
    }
    return name.charAt(0) == '$' || (name.length() >= 2 && name.charAt(0) == '_' && name.charAt(1) == '_');
  }

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
