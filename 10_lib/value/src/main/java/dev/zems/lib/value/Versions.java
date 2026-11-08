package dev.zems.lib.value;

/** Precedence questions over two {@link Version} values of the same dialect. */
public final class Versions {

  private Versions() {}

  /**
   * The dialect's precedence of {@code left} against {@code right}: {@code -1}, {@code 0} or {@code 1}. Equal
   * precedence is not equality: SemVer {@code 1.0.0+a} and {@code 1.0.0+b} answer {@code 0} yet are unequal.
   *
   * <p>
   * A state marker in either argument comes back as it is, left first. Versions of different dialects answer an
   * {@link UnsupportedValue}.
   *
   * @throws IllegalArgumentException if either argument is a {@code null} reference
   */
  public static Value<Integer> comparePrecedence(Value<Version> left, Value<Version> right) {
    requireArgument(left, "left");
    requireArgument(right, "right");
    return left.flatMap(have ->
      right.flatMap(need -> {
        if (have.dialect() != need.dialect()) {
          return Value.unsupported(dialectsDiffer(have, need), Integer.class);
        }
        return Value.of(have.comparePrecedence(need));
      })
    );
  }

  /**
   * Whether {@code have} satisfies the at-least requirement {@code need}: its precedence is equal or higher.
   *
   * <p>
   * A state marker in either argument comes back as it is, left first. Versions of different dialects answer an
   * {@link UnsupportedValue}.
   *
   * @throws IllegalArgumentException if either argument is a {@code null} reference
   */
  public static Value<Boolean> atLeast(Value<Version> have, Value<Version> need) {
    requireArgument(have, "have");
    requireArgument(need, "need");
    return have.flatMap(candidate ->
      need.flatMap(requirement -> {
        if (candidate.dialect() != requirement.dialect()) {
          return Value.unsupported(dialectsDiffer(candidate, requirement), Boolean.class);
        }
        return Value.of(candidate.comparePrecedence(requirement) >= 0);
      })
    );
  }

  private static String dialectsDiffer(Version left, Version right) {
    return (
      "Cannot compare a " +
      left.dialect().displayName() +
      " version with a " +
      right.dialect().displayName() +
      " version: dialects differ"
    );
  }

  private static void requireArgument(Value<Version> argument, String name) {
    if (argument == null) {
      throw new IllegalArgumentException(name + " must not be null");
    }
  }
}
