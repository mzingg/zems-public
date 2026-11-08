package dev.zems.lib.value;

import java.util.Objects;

/**
 * A symbolic Java type name, the payload of a {@link dev.zems.lib.value.builtin.TypeValue}.
 *
 * <p>
 * The canonical form is what {@link Class#getName()} yields for top-level classes, nested classes
 * ({@code Outer$Inner}) and primitives: dot-separated Java identifiers. The name is never resolved to a
 * {@code Class}. Direct construction is supported; the constructor throws {@link IllegalArgumentException} on a
 * malformed name, where the {@code Value.typeOf(...)} factories answer an {@link ErrorValue}.
 */
public record TypeName(String name) {
  public TypeName {
    Objects.requireNonNull(name, "Type name must not be null");
    if (!isQualifiedName(name)) {
      throw new IllegalArgumentException("Invalid type name: " + name);
    }
  }

  /**
   * True for one or more non-empty dot-separated segments that each satisfy {@link Character#isJavaIdentifierStart}
   * and {@link Character#isJavaIdentifierPart}. Keywords pass, so primitive names such as {@code int} are accepted;
   * array ({@code [Ljava.lang.String;}) and hidden ({@code Foo$$Lambda/0x1}) class names are rejected, as are
   * {@code null} and blank input.
   */
  public static boolean isQualifiedName(String candidate) {
    if (candidate == null || candidate.isEmpty()) {
      return false;
    }
    int segmentLength = 0;
    for (int i = 0; i < candidate.length(); i++) {
      char c = candidate.charAt(i);
      if (c == '.') {
        if (segmentLength == 0) {
          return false;
        }
        segmentLength = 0;
        continue;
      }
      boolean wellFormed = segmentLength == 0 ? Character.isJavaIdentifierStart(c) : Character.isJavaIdentifierPart(c);
      if (!wellFormed) {
        return false;
      }
      segmentLength++;
    }
    return segmentLength > 0;
  }

  /** The bare name, not the record form. */
  @Override
  public String toString() {
    return name;
  }
}
