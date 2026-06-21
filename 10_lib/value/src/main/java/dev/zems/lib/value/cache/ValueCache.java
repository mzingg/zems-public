package dev.zems.lib.value.cache;

import dev.zems.lib.value.builtin.BooleanValue;
import dev.zems.lib.value.builtin.ByteValue;
import dev.zems.lib.value.builtin.IntegerValue;
import dev.zems.lib.value.builtin.ListValue;
import dev.zems.lib.value.builtin.LongValue;
import dev.zems.lib.value.builtin.MapValue;
import dev.zems.lib.value.builtin.SetValue;
import dev.zems.lib.value.builtin.ShortValue;
import dev.zems.lib.value.builtin.SortedMapValue;
import dev.zems.lib.value.builtin.StringValue;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Process-wide cache for well-known {@code Value} instances. The static {@link #INSTANCE} is built from
 * {@link ValueCachePolicy#fromSystemProperties()} at class-init; tests that need a specific policy instantiate their
 * own {@code ValueCache} directly via the package-private constructor.
 *
 * <p>
 * <strong>Always-cached</strong> (no policy knob): both booleans, all 256 byte values, all
 * 65 536 short values, and one empty instance per collection type. The total fixed footprint is ~1.6 MiB, dominated by
 * the short table.
 *
 * <p>
 * <strong>Policy-controlled</strong>: the {@code IntegerValue} cache covers
 * {@code -(intMax+1)..intMax}, the {@code LongValue} cache covers the same range as a long, and the {@code StringValue}
 * cache is a bounded LRU. See {@link ValueCachePolicy}.
 *
 * <p>
 * The {@code IntegerValue} / {@code LongValue} arrays cover a half-range of
 * {@code min(policy.intMax(), MAX_ARRAY_HALF_RANGE)} (resp. {@code longMax}). Values inside the policy bound but
 * outside that pre-allocated range fall through to a lazy {@link ConcurrentHashMap}. The {@code unbounded} preset goes
 * through this path: every int the caller actually requests becomes a cached instance, but no 4-billion-entry array is
 * allocated up-front.
 */
public final class ValueCache {

  public static final ValueCache INSTANCE = new ValueCache(ValueCachePolicy.fromSystemProperties());

  /**
   * Practical upper bound for the eager array allocation per type. At ~24 bytes per {@code IntegerValue} record this
   * caps the per-type up-front allocation at ~24 MiB (1 048 576 entries × 2 sides). Values beyond this in a policy
   * still hit the cache via the lazy map fallback; only the pre-allocation is capped.
   */
  private static final int MAX_ARRAY_HALF_RANGE = 1 << 20;

  private final ValueCachePolicy policy;

  private final BooleanValue trueValue;
  private final BooleanValue falseValue;

  private final ByteValue[] byteCache;
  private final ShortValue[] shortCache;

  // To avoid Integer autoboxing, we store MAX_ARRAY_HALF_RANGE values in an array for direct primitive lookup
  private final IntegerValue[] intArray;
  private final int intArrayMin;
  private final ConcurrentMap<Integer, IntegerValue> intMap;
  private final int intMapMin;
  private final int intMapMax;

  // To avoid Long autoboxing, we store MAX_ARRAY_HALF_RANGE values in an array for direct primitive lookup
  private final LongValue[] longArray;
  private final long longArrayMin;
  private final ConcurrentMap<Long, LongValue> longMap;
  private final long longMapMin;
  private final long longMapMax;

  private final Map<String, StringValue> stringCache;

  private final ListValue<?> emptyList;
  private final SetValue<?> emptySet;
  private final MapValue<?, ?> emptyMap;
  private final SortedMapValue<?, ?> emptySortedMap;

  ValueCache(ValueCachePolicy policy) {
    this.policy = policy;
    this.trueValue = new BooleanValue(true);
    this.falseValue = new BooleanValue(false);

    this.byteCache = new ByteValue[256];
    for (int i = -128; i <= 127; i++) {
      byteCache[i + 128] = new ByteValue((byte) i);
    }

    this.shortCache = new ShortValue[65_536];
    for (int i = -32_768; i <= 32_767; i++) {
      shortCache[i + 32_768] = new ShortValue((short) i);
    }

    int intHalf = Math.min(policy.intMax(), MAX_ARRAY_HALF_RANGE);
    if (intHalf > 0) {
      this.intArrayMin = -(intHalf + 1);
      this.intArray = new IntegerValue[intHalf * 2 + 2];
      for (int idx = 0; idx < intArray.length; idx++) {
        intArray[idx] = new IntegerValue(idx + intArrayMin);
      }
    } else {
      this.intArrayMin = 0;
      this.intArray = null;
    }
    this.intMapMax = policy.intMax();
    this.intMapMin = -policy.intMax() - 1;
    this.intMap = (policy.intMax() > intHalf) ? new ConcurrentHashMap<>() : null;

    int longHalf = Math.min(policy.longMax(), MAX_ARRAY_HALF_RANGE);
    if (longHalf > 0) {
      this.longArrayMin = -((long) longHalf + 1L);
      this.longArray = new LongValue[longHalf * 2 + 2];
      for (int idx = 0; idx < longArray.length; idx++) {
        longArray[idx] = new LongValue(idx + longArrayMin);
      }
    } else {
      this.longArrayMin = 0L;
      this.longArray = null;
    }
    this.longMapMax = policy.longMax();
    this.longMapMin = -((long) policy.longMax() + 1L);
    this.longMap = (policy.longMax() > longHalf) ? new ConcurrentHashMap<>() : null;

    this.stringCache = createStringCache(policy.stringMax(), policy.stringIsUnbounded());

    this.emptyList = new ListValue<>(List.of());
    this.emptySet = new SetValue<>(Set.of());
    this.emptyMap = new MapValue<>(Map.of());
    this.emptySortedMap = new SortedMapValue<>(Collections.unmodifiableSortedMap(new TreeMap<>()));
  }

  private static Map<String, StringValue> createStringCache(int max, boolean unbounded) {
    if (unbounded) {
      return new ConcurrentHashMap<>();
    }
    if (max == 0) {
      return null;
    }
    return Collections.synchronizedMap(
      new LinkedHashMap<>(max + 1, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, StringValue> eldest) {
          return size() > max;
        }
      }
    );
  }

  /** Returns the immutable policy this cache was constructed with. */
  public ValueCachePolicy policy() {
    return policy;
  }

  public BooleanValue cached(boolean b) {
    return b ? trueValue : falseValue;
  }

  public ByteValue cached(byte b) {
    return byteCache[b - Byte.MIN_VALUE];
  }

  public ShortValue cached(short s) {
    return shortCache[s - Short.MIN_VALUE];
  }

  public IntegerValue cached(int i) {
    if (intArray != null) {
      int idx = i - intArrayMin;
      if (idx >= 0 && idx < intArray.length) {
        return intArray[idx];
      }
    }
    if (intMap != null && i >= intMapMin && i <= intMapMax) {
      return intMap.computeIfAbsent(i, IntegerValue::new);
    }
    return null;
  }

  public LongValue cached(long l) {
    if (longArray != null) {
      long idx = l - longArrayMin;
      if (idx >= 0 && idx < longArray.length) {
        return longArray[(int) idx];
      }
    }
    if (longMap != null && l >= longMapMin && l <= longMapMax) {
      return longMap.computeIfAbsent(l, LongValue::new);
    }
    return null;
  }

  public StringValue cached(String s) {
    if (stringCache == null || s == null) {
      return null;
    }
    if (stringCache instanceof ConcurrentMap<String, StringValue> chm) {
      return chm.computeIfAbsent(s, StringValue::new);
    }
    synchronized (stringCache) {
      StringValue existing = stringCache.get(s);
      if (existing != null) {
        return existing;
      }
      StringValue created = new StringValue(s);
      stringCache.put(s, created);
      return created;
    }
  }

  @SuppressWarnings("unchecked")
  public <E> ListValue<E> emptyList() {
    return (ListValue<E>) emptyList;
  }

  @SuppressWarnings("unchecked")
  public <E> SetValue<E> emptySet() {
    return (SetValue<E>) emptySet;
  }

  @SuppressWarnings("unchecked")
  public <K, V> MapValue<K, V> emptyMap() {
    return (MapValue<K, V>) emptyMap;
  }

  @SuppressWarnings("unchecked")
  public <K, V> SortedMapValue<K, V> emptySortedMap() {
    return (SortedMapValue<K, V>) emptySortedMap;
  }
}
