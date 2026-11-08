package dev.zems.lib.value.cache;

import java.util.Locale;
import java.util.Map;

/**
 * Immutable configuration for {@link ValueCache}: the bounds of the optional int, long and string caches. A preset is
 * selected through the {@code zems.value.cache.mode} system property and single bounds are overridden through the
 * per-type properties; see {@link #fromSystemProperties()}.
 *
 * @param intMax the {@code IntegerValue} cache covers {@code -(intMax+1)..intMax}
 * @param longMax the {@code LongValue} cache covers {@code -(longMax+1)..longMax}
 * @param stringMax capacity of the bounded LRU {@code StringValue} cache; {@code 0} disables it, and it must be
 *     {@code 0} when {@code stringIsUnbounded} is {@code true}
 * @param stringIsUnbounded {@code true} caches every string with no eviction
 */
public record ValueCachePolicy(int intMax, int longMax, int stringMax, boolean stringIsUnbounded) {
  public static final ValueCachePolicy DEFAULT = new ValueCachePolicy(32_767, 32_767, 1024, false);
  public static final ValueCachePolicy AGGRESSIVE = new ValueCachePolicy(32_767, 32_767, 8192, false);
  public static final ValueCachePolicy MINIMAL = new ValueCachePolicy(0, 0, 0, false);
  public static final ValueCachePolicy DISABLED = MINIMAL;
  public static final ValueCachePolicy UNBOUNDED = new ValueCachePolicy(Integer.MAX_VALUE, Integer.MAX_VALUE, 0, true);

  public static final String PROP_MODE = "zems.value.cache.mode";
  public static final String PROP_INT_MAX = "zems.value.cache.int.max";
  public static final String PROP_LONG_MAX = "zems.value.cache.long.max";
  public static final String PROP_STRING_MAX = "zems.value.cache.string.max";

  private static final String UNBOUNDED_LITERAL = "unbounded";

  public ValueCachePolicy {
    if (intMax < 0) {
      throw new IllegalArgumentException(PROP_INT_MAX + " must be >= 0, was " + intMax);
    }
    if (longMax < 0) {
      throw new IllegalArgumentException(PROP_LONG_MAX + " must be >= 0, was " + longMax);
    }
    if (stringMax < 0) {
      throw new IllegalArgumentException(PROP_STRING_MAX + " must be >= 0, was " + stringMax);
    }
    if (stringIsUnbounded && stringMax != 0) {
      throw new IllegalArgumentException(
        PROP_STRING_MAX + " must be 0 when stringIsUnbounded is true, was " + stringMax
      );
    }
  }

  /**
   * Builds a policy from the system properties. Every property is optional; an absent one falls back to the preset
   * named by {@code zems.value.cache.mode}, which itself defaults to {@link #DEFAULT}.
   */
  public static ValueCachePolicy fromSystemProperties() {
    return parse(System::getProperty);
  }

  /** Builds a policy from {@code properties} by the same rules as {@link #fromSystemProperties()}. */
  public static ValueCachePolicy parse(Map<String, String> properties) {
    return parse(properties::get);
  }

  private static ValueCachePolicy parse(java.util.function.Function<String, String> lookup) {
    String modeStr = lookup.apply(PROP_MODE);
    ValueCachePolicy base = modeStr == null ? DEFAULT : parseMode(modeStr);
    int intMax = parseBound(lookup, PROP_INT_MAX, base.intMax);
    int longMax = parseBound(lookup, PROP_LONG_MAX, base.longMax);
    int baseStringEncoded = base.stringIsUnbounded ? Integer.MAX_VALUE : base.stringMax;
    int rawStringMax = parseBound(lookup, PROP_STRING_MAX, baseStringEncoded);
    boolean stringIsUnbounded = rawStringMax == Integer.MAX_VALUE;
    int stringMax = stringIsUnbounded ? 0 : rawStringMax;
    return new ValueCachePolicy(intMax, longMax, stringMax, stringIsUnbounded);
  }

  private static ValueCachePolicy parseMode(String raw) {
    return switch (raw.toLowerCase(Locale.ROOT)) {
      case "default" -> DEFAULT;
      case "aggressive" -> AGGRESSIVE;
      case "minimal" -> MINIMAL;
      case "disabled" -> DISABLED;
      case UNBOUNDED_LITERAL -> UNBOUNDED;
      default -> throw new IllegalArgumentException(
        PROP_MODE + " must be one of default/aggressive/minimal/disabled/unbounded, was: " + raw
      );
    };
  }

  private static int parseBound(java.util.function.Function<String, String> lookup, String name, int fallback) {
    String raw = lookup.apply(name);
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    String trimmed = raw.trim();
    if (UNBOUNDED_LITERAL.equalsIgnoreCase(trimmed)) {
      return Integer.MAX_VALUE;
    }
    try {
      long parsed = Long.parseLong(trimmed);
      if (parsed < 0 || parsed > Integer.MAX_VALUE) {
        throw new IllegalArgumentException(name + " out of range [0, Integer.MAX_VALUE]: " + raw);
      }
      return (int) parsed;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(name + " must be a non-negative integer or 'unbounded', was: " + raw, e);
    }
  }
}
