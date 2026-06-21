package dev.zems.lib.value.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.builtin.BooleanValue;
import dev.zems.lib.value.builtin.ByteValue;
import dev.zems.lib.value.builtin.IntegerValue;
import dev.zems.lib.value.builtin.ShortValue;
import dev.zems.lib.value.builtin.StringValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("ValueCache")
class ValueCacheContractTest {

  @Nested
  @DisplayName("Always-cached types (no policy knob)")
  class AlwaysCached {

    @Test
    void bothBooleansHitTheSingleton() {
      var cache = new ValueCache(ValueCachePolicy.DISABLED);

      BooleanValue trueA = cache.cached(true);
      BooleanValue trueB = cache.cached(true);
      assertThat(trueA).isSameAs(trueB);

      BooleanValue falseA = cache.cached(false);
      assertThat(falseA).isNotSameAs(trueA);
      assertThat(falseA.bool()).isFalse();
    }

    @Test
    void everyByteHitsThePreAllocatedTable() {
      var cache = new ValueCache(ValueCachePolicy.DISABLED);
      for (int b = Byte.MIN_VALUE; b <= Byte.MAX_VALUE; b++) {
        ByteValue first = cache.cached((byte) b);
        ByteValue second = cache.cached((byte) b);
        assertThat(first).isSameAs(second);
        assertThat(first.byteValue()).isEqualTo((byte) b);
      }
    }

    @Test
    void everyShortHitsThePreAllocatedTable() {
      var cache = new ValueCache(ValueCachePolicy.DISABLED);
      for (int s = Short.MIN_VALUE; s <= Short.MAX_VALUE; s++) {
        ShortValue cached = cache.cached((short) s);
        assertThat(cached.shortValue()).isEqualTo((short) s);
        assertThat(cache.cached((short) s)).isSameAs(cached);
      }
    }
  }

  @Nested
  @DisplayName("IntegerValue cache")
  class IntCache {

    @Test
    void inRangeReturnsSameInstance() {
      var cache = new ValueCache(new ValueCachePolicy(100, 0, 0, false));
      assertThat(cache.cached(0)).isSameAs(cache.cached(0));
      assertThat(cache.cached(50)).isSameAs(cache.cached(50));
      assertThat(cache.cached(100)).isSameAs(cache.cached(100));
      assertThat(cache.cached(-101)).isSameAs(cache.cached(-101));
    }

    @Test
    void outOfRangeMisses() {
      var cache = new ValueCache(new ValueCachePolicy(100, 0, 0, false));
      assertThat(cache.cached(101)).isNull();
      assertThat(cache.cached(-102)).isNull();
      assertThat(cache.cached(1_000_000)).isNull();
      assertThat(cache.cached(Integer.MIN_VALUE)).isNull();
      assertThat(cache.cached(Integer.MAX_VALUE)).isNull();
    }

    @Test
    void disabledPolicyMissesEverywhere() {
      var cache = new ValueCache(ValueCachePolicy.DISABLED);
      assertThat(cache.cached(0)).isNull();
      assertThat(cache.cached(1)).isNull();
      assertThat(cache.cached(-1)).isNull();
    }

    @Test
    void unboundedHitsAnyInt() {
      var cache = new ValueCache(ValueCachePolicy.UNBOUNDED);
      // Values inside the pre-allocated range and a few well past it both hit.
      assertThat(cache.cached(0)).isSameAs(cache.cached(0));
      assertThat(cache.cached(1_500_000)).isSameAs(cache.cached(1_500_000));
      assertThat(cache.cached(Integer.MAX_VALUE)).isSameAs(cache.cached(Integer.MAX_VALUE));
      assertThat(cache.cached(Integer.MIN_VALUE)).isSameAs(cache.cached(Integer.MIN_VALUE));
    }

    @Test
    void valueOfDelegatesThroughCache() {
      // Default policy covers small ints; both Value.of and the direct cache lookup return the
      // same singleton.
      assertThat(Value.of(42)).isSameAs(Value.of(42));
    }
  }

  @Nested
  @DisplayName("LongValue cache")
  class LongCache {

    @Test
    void inRangeReturnsSameInstance() {
      var cache = new ValueCache(new ValueCachePolicy(0, 100, 0, false));
      assertThat(cache.cached(0L)).isSameAs(cache.cached(0L));
      assertThat(cache.cached(100L)).isSameAs(cache.cached(100L));
      assertThat(cache.cached(-101L)).isSameAs(cache.cached(-101L));
    }

    @Test
    void outOfRangeMisses() {
      var cache = new ValueCache(new ValueCachePolicy(0, 100, 0, false));
      assertThat(cache.cached(101L)).isNull();
      assertThat(cache.cached(Long.MAX_VALUE)).isNull();
      assertThat(cache.cached(Long.MIN_VALUE)).isNull();
    }

    @Test
    void unboundedHitsAnyLongInPolicyBound() {
      // longMax is an int field, so the bound is [-(intMax+1), intMax]. Any long outside that
      // misses, even under "unbounded" — by design, since the policy uses int half-range.
      var cache = new ValueCache(ValueCachePolicy.UNBOUNDED);
      assertThat(cache.cached((long) Integer.MAX_VALUE)).isSameAs(cache.cached((long) Integer.MAX_VALUE));
      assertThat(cache.cached((long) Integer.MIN_VALUE)).isSameAs(cache.cached((long) Integer.MIN_VALUE));
    }
  }

  @Nested
  @DisplayName("StringValue cache")
  class StringCache {

    @Test
    void defaultPolicyReturnsSameInstanceOnRepeatHit() {
      var cache = new ValueCache(ValueCachePolicy.DEFAULT);
      StringValue first = cache.cached("hello");
      StringValue second = cache.cached("hello");
      assertThat(first).isSameAs(second);
      assertThat(first.string()).isEqualTo("hello");
    }

    @Test
    void disabledPolicyAlwaysMisses() {
      var cache = new ValueCache(ValueCachePolicy.DISABLED);
      assertThat(cache.cached("hello")).isNull();
    }

    @Test
    void boundedLruEvictsEldestEntry() {
      var cache = new ValueCache(new ValueCachePolicy(0, 0, 3, false));
      StringValue first = cache.cached("a");
      cache.cached("b");
      cache.cached("c");
      cache.cached("d"); // size > 3 → evicts "a"
      assertThat(cache.cached("a")).isNotSameAs(first); // re-inserted, fresh instance
    }

    @Test
    void recentAccessKeepsEntryAlive() {
      var cache = new ValueCache(new ValueCachePolicy(0, 0, 3, false));
      StringValue first = cache.cached("a");
      cache.cached("b");
      cache.cached("c");
      cache.cached("a"); // touch — "a" moves to most-recent, "b" becomes eldest
      cache.cached("d"); // evicts "b", not "a"
      assertThat(cache.cached("a")).isSameAs(first);
    }

    @Test
    void unboundedNeverEvicts() {
      var cache = new ValueCache(new ValueCachePolicy(0, 0, 0, true));
      StringValue marker = cache.cached("marker");
      for (int i = 0; i < 5000; i++) {
        cache.cached("filler-" + i);
      }
      assertThat(cache.cached("marker")).isSameAs(marker);
    }

    @Test
    void nullStringMisses() {
      var cache = new ValueCache(ValueCachePolicy.DEFAULT);
      assertThat(cache.cached((String) null)).isNull();
    }
  }

  @Nested
  @DisplayName("Empty-collection singletons")
  class EmptyCollections {

    @Test
    void everyEmptyGetterReturnsTheSameInstance() {
      var cache = new ValueCache(ValueCachePolicy.DEFAULT);
      assertThat(cache.emptyList()).isSameAs(cache.emptyList());
      assertThat(cache.emptySet()).isSameAs(cache.emptySet());
      assertThat(cache.emptyMap()).isSameAs(cache.emptyMap());
      assertThat(cache.emptySortedMap()).isSameAs(cache.emptySortedMap());
    }
  }

  @Nested
  @DisplayName("Cross-cache equality (interning invisible to callers)")
  class CrossCacheEquality {

    @Test
    void distinctValueCacheInstancesProduceEqualButNotSameRecords() {
      var a = new ValueCache(ValueCachePolicy.DEFAULT);
      var b = new ValueCache(ValueCachePolicy.DEFAULT);
      IntegerValue fromA = a.cached(7);
      IntegerValue fromB = b.cached(7);
      assertThat(fromA).isNotSameAs(fromB).isEqualTo(fromB);
    }
  }

  @Nested
  @DisplayName("Process-wide INSTANCE")
  class ProcessWideInstance {

    @Test
    void valueOfBooleanReturnsTheSameSingleton() {
      assertThat(Value.of(true)).isSameAs(Value.of(true));
      assertThat(Value.of(false)).isSameAs(Value.of(false));
    }

    @Test
    void valueOfByteAlwaysHitsTheCache() {
      assertThat(Value.of((byte) 7)).isSameAs(Value.of((byte) 7));
      assertThat(Value.of((byte) -100)).isSameAs(Value.of((byte) -100));
    }

    @Test
    void valueOfShortAlwaysHitsTheCache() {
      assertThat(Value.of((short) 30_000)).isSameAs(Value.of((short) 30_000));
      assertThat(Value.of((short) -30_000)).isSameAs(Value.of((short) -30_000));
    }

    @Test
    void valueOfIntHitsTheDefaultRange() {
      assertThat(Value.of(0)).isSameAs(Value.of(0));
      assertThat(Value.of(32_767)).isSameAs(Value.of(32_767));
      assertThat(Value.of(-32_768)).isSameAs(Value.of(-32_768));
    }

    @Test
    void valueOfLongHitsTheDefaultRange() {
      assertThat(Value.of(0L)).isSameAs(Value.of(0L));
      assertThat(Value.of(32_767L)).isSameAs(Value.of(32_767L));
      assertThat(Value.of(-32_768L)).isSameAs(Value.of(-32_768L));
    }

    @Test
    void emptyCollectionFactoriesReuseTheSingleton() {
      assertThat(Value.emptyList()).isSameAs(Value.emptyList());
      assertThat(Value.emptySet()).isSameAs(Value.emptySet());
      assertThat(Value.emptyMap()).isSameAs(Value.emptyMap());
      assertThat(Value.emptySortedMap()).isSameAs(Value.emptySortedMap());
    }
  }
}
