package dev.zems.lib.value.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common.junit.AllocationMeasurement;
import dev.zems.lib.common.junit.AllocationMeasurement.Sample;
import dev.zems.lib.common.junit.JourneyTest;
import dev.zems.lib.value.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ValueCache per-call cost and eager-init budget")
class ValueCacheInitializationJourneyTest {

  private static final int ITERATIONS = 100_000;
  private static final int WARMUP = 2;
  private static final int REPEATS = 5;

  // The measured median is 1-3 ms (JDK 26, 8-core Ryzen); the slack absorbs CI noise.
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

    assertThat(sample.bytesPerIteration())
      .as("Value.of(0) is a default-policy cache hit and must not allocate")
      .isZero();
  }

  @Test
  @JourneyTest(speakingId = "value-cache-initialization", acceptance = "a2")
  void valueCacheConstruction_meetsEagerInitBudget() {
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
