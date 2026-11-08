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
 * Process-wide cache of well-known {@code Value} instances; {@link #INSTANCE} is built from
 * {@link ValueCachePolicy#fromSystemProperties()} at class initialisation.
 *
 * <p>
 * Always cached: both booleans, every byte and short, and one empty instance per collection type. Controlled by the
 * policy: {@code IntegerValue} and {@code LongValue} over {@code -(max+1)..max}, and a bounded LRU of
 * {@code StringValue}.
 */
public final class ValueCache {

  public static final ValueCache INSTANCE = new ValueCache(ValueCachePolicy.fromSystemProperties());

  private static final int MAX_ARRAY_HALF_RANGE = 1 << 20;

  private final ValueCachePolicy policy;

  private final BooleanValue trueValue;
  private final BooleanValue falseValue;

  private final ByteValue[] byteCache;
  private final ShortValue[] shortCache;

  // A Map<Integer, IntegerValue> lookup boxes the key on every call; the array answers the hot range without
  // allocating, which the zero-allocation journey test pins.
  private final IntegerValue[] intArray;
  private final int intArrayMin;
  private final ConcurrentMap<Integer, IntegerValue> intMap;
  private final int intMapMin;
  private final int intMapMax;

  // Same as intArray: the array avoids boxing the key on every lookup in the hot range.
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
    this.intMap = policy.intMax() > intHalf ? new ConcurrentHashMap<>() : null;

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
    this.longMap = policy.longMax() > longHalf ? new ConcurrentHashMap<>() : null;

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

  /** The cached instance, or {@code null} when {@code i} is outside the policy's range. */
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

  /** The cached instance, or {@code null} when {@code l} is outside the policy's range. */
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

  /** The cached instance, or {@code null} when string caching is disabled or {@code s} is {@code null}. */
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
