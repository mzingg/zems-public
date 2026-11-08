package dev.zems.lib.value;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** A parsed SemVer 2.0.0 version; the payload behind a {@link VersionDialect#SEMVER} {@link Version}. */
record SemanticVersion(
  BigInteger major,
  BigInteger minor,
  BigInteger patch,
  List<String> preRelease,
  List<String> build
) {
  private static final int CORE_SIZE = 3;

  SemanticVersion {
    preRelease = List.copyOf(preRelease);
    build = List.copyOf(build);
  }

  static SemanticVersion parse(String text) {
    int plus = text.indexOf('+');
    String withoutBuild = plus < 0 ? text : text.substring(0, plus);
    int dash = withoutBuild.indexOf('-');
    List<BigInteger> numbers = coreNumbers(dash < 0 ? withoutBuild : withoutBuild.substring(0, dash));
    List<String> preRelease = dash < 0 ? List.of() : identifiers(withoutBuild.substring(dash + 1), true);
    List<String> build = plus < 0 ? List.of() : identifiers(text.substring(plus + 1), false);
    return new SemanticVersion(numbers.get(0), numbers.get(1), numbers.get(2), preRelease, build);
  }

  int comparePrecedence(SemanticVersion other) {
    int byCore = compareCore(other);
    if (byCore != 0) {
      return byCore;
    }
    if (preRelease.isEmpty() || other.preRelease.isEmpty()) {
      return Boolean.compare(preRelease.isEmpty(), other.preRelease.isEmpty());
    }
    int shared = Math.min(preRelease.size(), other.preRelease.size());
    for (int i = 0; i < shared; i++) {
      int byIdentifier = compareIdentifiers(preRelease.get(i), other.preRelease.get(i));
      if (byIdentifier != 0) {
        return byIdentifier;
      }
    }
    return Integer.compare(preRelease.size(), other.preRelease.size());
  }

  private int compareCore(SemanticVersion other) {
    int byMajor = major.compareTo(other.major);
    if (byMajor != 0) {
      return byMajor;
    }
    int byMinor = minor.compareTo(other.minor);
    return byMinor != 0 ? byMinor : patch.compareTo(other.patch);
  }

  private static int compareIdentifiers(String left, String right) {
    boolean leftNumeric = isNumeric(left);
    boolean rightNumeric = isNumeric(right);
    if (leftNumeric && rightNumeric) {
      return new BigInteger(left).compareTo(new BigInteger(right));
    }
    if (leftNumeric || rightNumeric) {
      return leftNumeric ? -1 : 1;
    }
    return left.compareTo(right);
  }

  private static List<BigInteger> coreNumbers(String core) {
    String[] parts = core.split("\\.", -1);
    if (parts.length != CORE_SIZE) {
      throw new IllegalArgumentException("expected MAJOR.MINOR.PATCH");
    }
    List<BigInteger> numbers = new ArrayList<>(CORE_SIZE);
    for (String part : parts) {
      if (!isNumeric(part)) {
        throw new IllegalArgumentException("expected MAJOR.MINOR.PATCH");
      }
      numbers.add(new BigInteger(requireNoLeadingZero(part)));
    }
    return numbers;
  }

  private static List<String> identifiers(String dotted, boolean numericMustNotLeadWithZero) {
    List<String> identifiers = List.of(dotted.split("\\.", -1));
    for (String identifier : identifiers) {
      if (identifier.isEmpty()) {
        throw new IllegalArgumentException("empty identifier");
      }
      if (!identifier.chars().allMatch(SemanticVersion::isIdentifierCharacter)) {
        throw new IllegalArgumentException("identifier '" + identifier + "' has a character outside [0-9A-Za-z-]");
      }
      if (numericMustNotLeadWithZero && isNumeric(identifier)) {
        requireNoLeadingZero(identifier);
      }
    }
    return identifiers;
  }

  private static String requireNoLeadingZero(String numeric) {
    if (numeric.length() > 1 && numeric.charAt(0) == '0') {
      throw new IllegalArgumentException("numeric identifier '" + numeric + "' has a leading zero");
    }
    return numeric;
  }

  private static boolean isNumeric(String identifier) {
    return !identifier.isEmpty() && identifier.chars().allMatch(SemanticVersion::isAsciiDigit);
  }

  private static boolean isAsciiDigit(int c) {
    return c >= '0' && c <= '9';
  }

  private static boolean isIdentifierCharacter(int c) {
    return isAsciiDigit(c) || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '-';
  }
}
