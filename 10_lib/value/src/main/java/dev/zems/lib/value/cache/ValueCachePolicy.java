package dev.zems.lib.value.cache;

import java.util.Locale;
import java.util.Map;

/**
 * Immutable configuration for {@link ValueCache}. Controls the per-type bounds of the optional caches (int, long,
 * string). The mandatory caches — {@code BooleanValue}, {@code ByteValue}, {@code ShortValue}, and the empty-collection
 * singletons — are not configurable: their full domains are tiny, and the hit rate is 100 % by definition.
 *
 * <p>
 * Five presets are exposed as constants and selectable via the {@code zems.value.cache.mode} VM property. Individual
 * bounds can be overridden via per-type VM properties; see {@link #fromSystemProperties()} for the property names.
 *
 * <pre>
 * | Preset       | int.max           | long.max          | string.max | stringIsUnbounded |
 * |--------------|-------------------|-------------------|------------|-------------------|
 * | DEFAULT      | 32_767            | 32_767            | 1024       | false             |
 * | AGGRESSIVE   | 32_767            | 32_767            | 8192       | false             |
 * | MINIMAL      | 0                 | 0                 | 0          | false             |
 * | DISABLED     | 0                 | 0                 | 0          | false             |
 * | UNBOUNDED    | Integer.MAX_VALUE | Integer.MAX_VALUE | 0          | true              |
 * </pre>
 *
 * <p>
 * {@code MINIMAL} and {@code DISABLED} are functionally identical; the two names express different intents (minimum
 * optional caching vs. opting out of optional caching).
 *
 * @param intMax            half-range bound for the {@code IntegerValue} cache (range is {@code -(intMax+1)..intMax})
 * @param longMax           half-range bound for the {@code LongValue} cache (range is {@code -(longMax+1)..longMax});
 *                          stored as {@code int} since arrays cannot exceed {@link Integer#MAX_VALUE} entries anyway
 * @param stringMax         bounded LRU capacity for the {@code StringValue} cache; {@code 0} disables. Ignored when
 *                          {@code stringIsUnbounded} is {@code true} (and must then be {@code 0}).
 * @param stringIsUnbounded {@code true} switches the {@code StringValue} cache to a lock-free
 *                          {@link java.util.concurrent.ConcurrentHashMap} with no eviction. {@code false} uses the
 *                          bounded LRU (or disables it when {@code stringMax == 0}).
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
   * Build a policy from {@link System#getProperty(String)}. All properties are optional; absent properties fall back to
   * the preset selected by {@code zems.value.cache.mode} (default: {@link #DEFAULT}).
   */
  public static ValueCachePolicy fromSystemProperties() {
    return parse(System::getProperty);
  }

  /**
   * Build a policy from a property map. Used directly by tests; the {@link #fromSystemProperties()} factory is a thin
   * wrapper around this with {@code System::getProperty}.
   */
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
