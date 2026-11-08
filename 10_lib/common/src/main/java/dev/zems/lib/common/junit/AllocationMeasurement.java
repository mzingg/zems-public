package dev.zems.lib.common.junit;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;

/**
 * Per-thread allocation and elapsed-time measurement for micro-workloads. Allocation comes from
 * {@link ThreadMXBean#getThreadAllocatedBytes(long)}; class initialisation fails on a JVM without it.
 */
public final class AllocationMeasurement {

  private static final ThreadMXBean THREAD_MX_BEAN = initThreadMxBean();

  private AllocationMeasurement() {}

  /**
   * Runs the workload once, without warmup. {@code iterations} is the loop count inside the workload; it only
   * normalises the per-iteration figures of the returned sample.
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

  /** Runs the workload {@code warmup + repeats} times; the sample holds per-field medians of the recorded repeats. */
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
    return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2;
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
   * One measurement of a workload. {@code allocatedBytes} is the calling thread's allocation delta; {@code iterations}
   * normalises the per-iteration accessors.
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
