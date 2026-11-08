package dev.zems.lib.value.valueio;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Producer/consumer bridge between a {@link BlockingQueue} and a thread-confined {@link AbstractStateWriter} or
 * {@link AbstractStateReader}. Readers and writers hold unguarded mutable state, so exactly one thread (the I/O thread
 * scheduled on the supplied {@link Executor}) may touch them; these helpers keep that contract while another thread
 * produces or consumes the values.
 */
public final class StreamPipeline {

  private StreamPipeline() {}

  /**
   * Drains the queue into the writer on one I/O thread until {@code poisonPill} is taken from it; the writer is closed
   * on return and the future completes exceptionally on a writer or queue failure.
   *
   * @param poisonPill matched by reference identity, so the instance passed here must be the one enqueued
   */
  public static <T> CompletableFuture<Void> drainToWriter(
    AbstractStateWriter writer,
    TypeDescriptor<T> descriptor,
    BlockingQueue<Value<T>> source,
    Value<T> poisonPill,
    Executor executor
  ) {
    Objects.requireNonNull(writer, "writer must not be null");
    Objects.requireNonNull(descriptor, "descriptor must not be null");
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(poisonPill, "poisonPill must not be null");
    Objects.requireNonNull(executor, "executor must not be null");
    return CompletableFuture.runAsync(() -> {
      try (writer) {
        while (true) {
          Value<T> v;
          try {
            v = source.take();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("drainToWriter interrupted while taking from queue", e);
          }
          if (v == poisonPill) {
            return;
          }
          writer.write(v, descriptor);
        }
      }
    }, executor);
  }

  /**
   * Pumps the reader's records onto the queue on one I/O thread, honouring back-pressure through
   * {@link BlockingQueue#put}; the future completes with the record count at EOF, the reader is closed on return.
   *
   * @param endSentinel put on the queue once at EOF, or {@code null} for no end signal
   */
  public static <T> CompletableFuture<Long> pumpFromReader(
    AbstractStateReader reader,
    TypeDescriptor<T> descriptor,
    BlockingQueue<Value<T>> sink,
    Value<T> endSentinel,
    Executor executor
  ) {
    Objects.requireNonNull(reader, "reader must not be null");
    Objects.requireNonNull(descriptor, "descriptor must not be null");
    Objects.requireNonNull(sink, "sink must not be null");
    Objects.requireNonNull(executor, "executor must not be null");
    return CompletableFuture.supplyAsync(() -> {
      long count = 0;
      try (reader) {
        while (reader.hasMoreRecords()) {
          Value<T> v = reader.read(descriptor);
          reader.consumeRecordSeparator();
          try {
            sink.put(v);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("pumpFromReader interrupted while putting on queue", e);
          }
          count++;
        }
        if (endSentinel != null) {
          try {
            sink.put(endSentinel);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("pumpFromReader interrupted while signaling end", e);
          }
        }
        return count;
      }
    }, executor);
  }
}
