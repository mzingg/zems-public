package dev.zems.lib.value;

import java.lang.module.ModuleDescriptor;
import java.util.Objects;

/**
 * A version string parsed under a {@link VersionDialect}, the payload of a
 * {@link dev.zems.lib.value.builtin.VersionValue}.
 *
 * <p>
 * Equality is the dialect's and never spans dialects: under JPMS {@code 1.0} equals {@code 1.0.0}, under SemVer two
 * versions that differ only in build metadata are unequal. Precedence is a separate question, answered by
 * {@link Versions#comparePrecedence}: SemVer build metadata is equal in precedence though unequal in value.
 * {@link #text()} keeps the text as written; {@link #toString()} is the dialect-qualified form ({@code semver:1.0.0})
 * that {@link #parseQualified} reads back.
 */
public final class Version {

  private static final char QUALIFIER_SEPARATOR = ':';

  private final VersionDialect dialect;
  private final String text;
  private final Object parsed;

  private Version(VersionDialect dialect, String text, Object parsed) {
    this.dialect = dialect;
    this.text = text;
    this.parsed = parsed;
  }

  /**
   * Parses {@code text} by the rules of {@code dialect}.
   *
   * @throws IllegalArgumentException on a null dialect, null or blank text, or text the dialect does not accept; the
   *     message names the dialect and the fault
   */
  public static Version parse(String text, VersionDialect dialect) {
    if (dialect == null) {
      throw new IllegalArgumentException("Version dialect must not be null");
    }
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("Version text must not be null or empty");
    }
    try {
      return new Version(dialect, text, parseUnder(dialect, text));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
        "Invalid " + dialect.displayName() + " version '" + text + "': " + e.getMessage(),
        e
      );
    }
  }

  /**
   * Parses the dialect-qualified form {@code <symbol>:<version>}, such as {@code semver:1.0.0}.
   *
   * @throws IllegalArgumentException on null or blank text, a missing or unknown symbol, or a version the named dialect
   *     does not accept
   */
  public static Version parseQualified(String qualified) {
    if (qualified == null || qualified.isBlank()) {
      throw new IllegalArgumentException("Version text must not be null or empty");
    }
    int separator = qualified.indexOf(QUALIFIER_SEPARATOR);
    String symbol = separator < 0 ? "" : qualified.substring(0, separator);
    for (VersionDialect dialect : VersionDialect.values()) {
      if (dialect.symbol().equals(symbol)) {
        return parse(qualified.substring(separator + 1), dialect);
      }
    }
    throw new IllegalArgumentException(
      "Invalid qualified version '" + qualified + "': expected <symbol>:<version> with symbol semver, jdk or jpms"
    );
  }

  public VersionDialect dialect() {
    return dialect;
  }

  /** The text as written: {@code 1.0} stays {@code 1.0} under JPMS even though it equals {@code 1.0.0}. */
  public String text() {
    return text;
  }

  int comparePrecedence(Version other) {
    return Integer.signum(
      switch (parsed) {
        case SemanticVersion semantic -> semantic.comparePrecedence((SemanticVersion) other.parsed);
        case Runtime.Version runtime -> runtime.compareTo((Runtime.Version) other.parsed);
        case ModuleDescriptor.Version module -> module.compareTo((ModuleDescriptor.Version) other.parsed);
        default -> throw new IllegalStateException("Unknown version payload: " + parsed.getClass().getName());
      }
    );
  }

  /** The dialect-qualified form, such as {@code semver:1.0.0}. */
  @Override
  public String toString() {
    return dialect.symbol() + QUALIFIER_SEPARATOR + text;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Version other && dialect == other.dialect && parsed.equals(other.parsed);
  }

  @Override
  public int hashCode() {
    // ModuleDescriptor.Version hashes its raw string, so 1.0 and 1.0.0 would be equal with different hashes.
    return dialect == VersionDialect.MODULE ? Objects.hash(dialect, leadingModuleNumber(text)) : parsed.hashCode();
  }

  private static Object parseUnder(VersionDialect dialect, String text) {
    return switch (dialect) {
      case SEMVER -> SemanticVersion.parse(text);
      case JDK -> Runtime.Version.parse(text);
      case MODULE -> ModuleDescriptor.Version.parse(text);
    };
  }

  // The same int arithmetic ModuleDescriptor.Version uses for its first token, overflow included: equal module versions
  // share this number, where a BigInteger would split 4294967297 from its equal 1.
  private static int leadingModuleNumber(String text) {
    int number = text.charAt(0) - '0';
    for (int i = 1; i < text.length() && text.charAt(i) >= '0' && text.charAt(i) <= '9'; i++) {
      number = number * 10 + (text.charAt(i) - '0');
    }
    return number;
  }
}
