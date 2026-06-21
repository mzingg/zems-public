package dev.zems.lib.common._test;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;

/**
 * Per-thread allocation and elapsed-time measurement for micro-workloads across zems modules.
 *
 * <p>
 * Uses {@link ThreadMXBean#getThreadAllocatedBytes(long)} for allocation accounting. Fails fast at class init if the
 * running JVM does not provide it (HotSpot / OpenJDK do; the project JDK floor is 25, so this is the expected
 * platform).
 *
 * <p>
 * Published in the common test-jar via the {@code dev/zems/lib/common/_test/**} glob so any module can reuse the same
 * measurement shape.
 */
public final class AllocationMeasurement {

  private static final ThreadMXBean THREAD_MX_BEAN = initThreadMxBean();

  private AllocationMeasurement() {}

  /**
   * Run the workload once and return one allocation/elapsed measurement. No warmup.
   *
   * @param iterations the number of iterations performed inside {@code workload} (used only to normalise the reported
   *                   per-iteration figures; the workload owns its loop)
   * @param workload   the workload to time
   */
  public static Sample measure(int iterations, Runnable workload) {
    long threadId = Thread.currentThread().threadId();
    long allocStart = THREAD_MX_BEAN.getThreadAllocatedBytes(threadId);
    long timeStart = System.nanoTime();
    workload.run();
    long timeEnd = System.nanoTime();
    long allocEnd = THREAD_MX_BEAN.getThreadAllocatedBytes(threadId);
    return new Sample(timeEnd - timeStart, allocEnd - allocStart, iterations);
  }

  /**
   * Run the workload {@code warmup + repeats} times and return per-field medians across the measured samples. Warmup
   * runs are not recorded.
   */
  public static Sample measureMedian(int warmup, int iterations, int repeats, Runnable workload) {
    if (warmup < 0 || repeats <= 0) {
      throw new IllegalArgumentException("warmup must be >= 0 and repeats must be > 0");
    }
    for (int i = 0; i < warmup; i++) {
      workload.run();
    }
    long[] elapsed = new long[repeats];
    long[] allocated = new long[repeats];
    long threadId = Thread.currentThread().threadId();
    for (int i = 0; i < repeats; i++) {
      long allocStart = THREAD_MX_BEAN.getThreadAllocatedBytes(threadId);
      long timeStart = System.nanoTime();
      workload.run();
      long timeEnd = System.nanoTime();
      long allocEnd = THREAD_MX_BEAN.getThreadAllocatedBytes(threadId);
      elapsed[i] = timeEnd - timeStart;
      allocated[i] = allocEnd - allocStart;
    }
    return new Sample(median(elapsed), median(allocated), iterations);
  }

  private static long median(long[] xs) {
    long[] sorted = xs.clone();
    Arrays.sort(sorted);
    int n = sorted.length;
    return (n % 2 == 1) ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2;
  }

  private static ThreadMXBean initThreadMxBean() {
    var bean = ManagementFactory.getThreadMXBean();
    if (!(bean instanceof ThreadMXBean sun)) {
      throw new IllegalStateException(
        "JVM does not provide com.sun.management.ThreadMXBean; AllocationMeasurement requires HotSpot or OpenJDK"
      );
    }
    if (!sun.isThreadAllocatedMemorySupported()) {
      throw new IllegalStateException("JVM does not support per-thread allocation accounting");
    }
    if (!sun.isThreadAllocatedMemoryEnabled()) {
      sun.setThreadAllocatedMemoryEnabled(true);
    }
    return sun;
  }

  /**
   * One measurement of a workload.
   *
   * @param elapsedNanos   wall-clock nanoseconds the workload took
   * @param allocatedBytes per-thread allocation delta over the workload
   * @param iterations     iteration count performed inside the workload (for per-op normalisation)
   */
  public record Sample(long elapsedNanos, long allocatedBytes, int iterations) {
    public double elapsedMillis() {
      return elapsedNanos / 1_000_000.0;
    }

    public double nanosPerIteration() {
      return iterations == 0 ? 0.0 : (double) elapsedNanos / iterations;
    }

    public double bytesPerIteration() {
      return iterations == 0 ? 0.0 : (double) allocatedBytes / iterations;
    }
  }
}
