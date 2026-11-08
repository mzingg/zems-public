package dev.zems.lib.value;

/** The closed set of version grammars a {@link Version} parses, orders and compares by. */
public enum VersionDialect {
  /** SemVer 2.0.0: exactly {@code MAJOR.MINOR.PATCH}, optional pre-release and build metadata. */
  SEMVER("semver", "SemVer"),
  /** JDK version strings (JEP 223), backed by {@link Runtime.Version}. */
  JDK("jdk", "JDK"),
  /** JPMS module versions, backed by {@link java.lang.module.ModuleDescriptor.Version}. */
  MODULE("jpms", "JPMS module");

  private final String symbol;
  private final String displayName;

  VersionDialect(String symbol, String displayName) {
    this.symbol = symbol;
    this.displayName = displayName;
  }

  /** The short symbol, also the prefix of a version's dialect-qualified form ({@code semver:1.0.0}). */
  public String symbol() {
    return symbol;
  }

  /** The name used in messages, such as {@code SemVer} or {@code JPMS module}. */
  public String displayName() {
    return displayName;
  }
}
