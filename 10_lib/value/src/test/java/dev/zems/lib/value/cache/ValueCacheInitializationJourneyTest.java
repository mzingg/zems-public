package dev.zems.lib.value.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.AllocationMeasurement;
import dev.zems.lib.common._test.AllocationMeasurement.Sample;
import dev.zems.lib.common._test.JourneyTest;
import dev.zems.lib.value.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Two related measurements:
 *
 * <ol>
 * <li>{@link #valueOfZero_perCallIsZeroAllocation()} — {@code Value.of(0)} resolves through the
 * cache to a pre-built {@code IntegerValue}; per-call allocation must stay at zero bytes.
 * <li>{@link #valueCacheConstruction_meetsEagerInitBudget()} — building a fresh {@code
 * ValueCache} pre-populates ~196 600 records (256 bytes + 65 536 shorts + 65 536 ints +
 * 65 536 longs + a handful of singletons). Bounded to keep the eager-init cost visible: a
 * regression that grows the eager set (e.g. someone pre-populates 10 000 strings) fails fast.
 * </ol>
 *
 * <p>
 * Not gated by any system property — both workloads are sub-second and run every build.
 */
@DisplayName("ValueCache per-call cost and eager-init budget")
class ValueCacheInitializationJourneyTest {

  private static final int ITERATIONS = 100_000;
  private static final int WARMUP = 2;
  private static final int REPEATS = 5;

  /**
   * Generous upper bound for a single fresh-{@link ValueCache} construction. Observed median on JDK 26 / Ryzen 8C is
   * around 1-3 ms; the 250 ms bound leaves room for CI noise without letting a real regression (e.g. eagerly
   * pre-populating thousands of strings) hide.
   */
  private static final long EAGER_INIT_MAX_MILLIS = 250;

  @Test
  @JourneyTest(speakingId = "value-cache-initialization", acceptance = "a1")
  void valueOfZero_perCallIsZeroAllocation() {
    Object[] sink = new Object[ITERATIONS];

    Sample sample = AllocationMeasurement.measureMedian(WARMUP, ITERATIONS, REPEATS, () -> {
      for (int i = 0; i < ITERATIONS; i++) {
        sink[i] = Value.of(0);
      }
    });

    System.out.printf(
      "workload=value_of_zero_100k elapsed_ms=%.2f alloc_bytes=%,d alloc_per_op=%.2f%n",
      sample.elapsedMillis(),
      sample.allocatedBytes(),
      sample.bytesPerIteration()
    );

    // Per-op cost must be zero allocation for a cache hit. Any non-zero value is either a
    // cache miss for 0 (regression) or accidental allocation inside the cache lookup.
    assertThat(sample.bytesPerIteration())
      .as("Value.of(0) is a default-policy cache hit and must not allocate")
      .isZero();
  }

  @Test
  @JourneyTest(speakingId = "value-cache-initialization", acceptance = "a2")
  void valueCacheConstruction_meetsEagerInitBudget() {
    // Single shot per repeat: each iteration builds one fresh ValueCache. The constructor
    // walks all 65 536 short / int / long entries plus 256 bytes; total work is bounded.
    Sample sample = AllocationMeasurement.measureMedian(WARMUP, 1, REPEATS, () ->
      new ValueCache(ValueCachePolicy.DEFAULT)
    );

    System.out.printf(
      "workload=value_cache_construction elapsed_ms=%.2f alloc_bytes=%,d%n",
      sample.elapsedMillis(),
      sample.allocatedBytes()
    );

    assertThat(sample.elapsedMillis())
      .as("ValueCache eager pre-population must stay within budget")
      .isLessThan(EAGER_INIT_MAX_MILLIS);
  }
}
