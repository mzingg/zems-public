package dev.zems.lib.value.valueio.descriptor;

import java.util.Objects;

/** Validates slot and alias names: no NUL, control characters (TAB, LF and CR allowed), DEL or lone surrogates. */
final class Names {

  private Names() {}

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
        i++;
      } else if (Character.isLowSurrogate(ch)) {
        throw new IllegalArgumentException(kind + " name '" + name + "' contains a lone low surrogate at index " + i);
      }
    }
    return name;
  }

  private static char extractLegalCharacterAt(String name, String kind, int i) {
    char ch = name.charAt(i);
    if (isDisallowedControl(ch)) {
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

  private static boolean isDisallowedControl(char ch) {
    boolean whitespaceControl = ch == '\t' || ch == '\n' || ch == '\r';
    return (ch < 0x20 && !whitespaceControl) || ch == 0x7F;
  }
}
