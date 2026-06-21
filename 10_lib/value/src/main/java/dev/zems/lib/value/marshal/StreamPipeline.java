package dev.zems.lib.value.marshal;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Producer/consumer bridge between a {@link BlockingQueue} and a thread-confined {@link AbstractStateWriter} /
 * {@link AbstractStateReader}. Lets one thread produce {@link Value} objects while another drains them to/from the
 * wire.
 *
 * <p>
 * <b>Thread-confinement contract.</b> {@link AbstractStateWriter} and {@link AbstractStateReader}
 * hold mutable, unguarded internal state ({@code byteCount}, {@code recordDepth}, {@code closed}, the checksum digest).
 * Concurrent invocations on the same instance have undefined behaviour. The helpers below are the recommended way to
 * bridge across threads — exactly one thread (the I/O thread, scheduled via the supplied {@link Executor}) ever touches
 * the writer/reader.
 */
public final class StreamPipeline {

  private StreamPipeline() {}

  /**
   * Drain a queue of {@link Value} objects into a writer on a single I/O thread. Returns when the {@code poisonPill}
   * sentinel is observed in the queue or when the I/O thread is interrupted; the writer is closed on return. The
   * returned future completes normally on success or exceptionally on writer/queue failure.
   *
   * @param writer     thread-confined writer; will be operated on (and closed) only by the I/O thread
   * @param descriptor descriptor used to marshal each value via {@link AbstractStateWriter#write}
   * @param source     bounded or unbounded queue from which Values are taken
   * @param poisonPill sentinel that signals end-of-stream; matched by reference identity ({@code ==}), so the same
   *                   instance passed here must be the one enqueued (value-equal stand-ins do not count)
   * @param executor   executor that runs the drain loop
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
   * Pump a reader's records onto a queue on a single I/O thread. The future completes with the total record count when
   * EOF is reached. The reader is closed on return. On failure (queue rejection, reader exception), the future
   * completes exceptionally.
   *
   * <p>
   * The {@code endSentinel} is offered to the queue exactly once when the reader hits EOF, letting the consumer detect
   * end-of-stream without polling for a separate signal.
   *
   * @param reader      thread-confined reader; operated on (and closed) only by the I/O thread
   * @param descriptor  type descriptor used to read each record
   * @param sink        queue that receives Values; backpressure honoured via {@link BlockingQueue#put}
   * @param endSentinel sentinel offered when EOF is reached (can be {@code null} to skip)
   * @param executor    executor that runs the pump loop
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
