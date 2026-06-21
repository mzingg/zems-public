package dev.zems.lib.value.marshal.descriptor;

import java.util.Objects;

/**
 * Validates slot/alias names against unsafe control characters and lone surrogate halves.
 *
 * <p>
 * Applied uniformly at {@link SlotSpec} construction so all wire formats see the same rule. The id-anchored binary
 * format does not carry names on the wire, but JSON does — names become JSON object keys and must be representable as
 * well-formed strings.
 *
 * <p>
 * Allowed: any Unicode codepoint except NUL, the control range U+0001..U+001F (with TAB / LF / CR allowed), U+007F, and
 * lone surrogate halves.
 */
final class Names {

  private Names() {}

  /**
   * Returns {@code name} on success; throws {@link NullPointerException} for null, {@link IllegalArgumentException} for
   * empty or for any disallowed character.
   *
   * @param kind diagnostic label for the error message ({@code "slot"}, {@code "alias"}, …)
   */
  static String validate(String name, String kind) {
    Objects.requireNonNull(name, () -> kind + " name must not be null");
    if (name.isEmpty()) {
      throw new IllegalArgumentException(kind + " name must not be empty");
    }
    for (int i = 0; i < name.length(); i++) {
      var ch = extractLegalCharacterAt(name, kind, i);
      if (Character.isHighSurrogate(ch)) {
        if (i + 1 >= name.length() || !Character.isLowSurrogate(name.charAt(i + 1))) {
          throw new IllegalArgumentException(
            kind + " name '" + name + "' contains a lone high surrogate at index " + i
          );
        }
        i++; // skip the paired low surrogate
      } else if (Character.isLowSurrogate(ch)) {
        throw new IllegalArgumentException(kind + " name '" + name + "' contains a lone low surrogate at index " + i);
      }
    }
    return name;
  }

  private static char extractLegalCharacterAt(String name, String kind, int i) {
    char ch = name.charAt(i);
    // Disallow unsafe control chars: NUL, U+0001..U+0008, U+000B, U+000C, U+000E..U+001F, U+007F.
    // Allow tab (0x09), LF (0x0A), CR (0x0D) — these are the standard whitespace controls.
    if ((ch < 0x20 && ch != 0x09 && ch != 0x0A && ch != 0x0D) || ch == 0x7F) {
      throw new IllegalArgumentException(
        kind +
          " name '" +
          name +
          "' contains a disallowed control character (U+" +
          String.format("%04X", (int) ch) +
          ")"
      );
    }
    return ch;
  }
}
