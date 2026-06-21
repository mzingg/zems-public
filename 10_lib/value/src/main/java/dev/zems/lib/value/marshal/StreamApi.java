package dev.zems.lib.value.marshal;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.util.Iterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Helpers shared by every {@code ValueIo} Stream-API entry point. The format-specific methods on
 * {@code ValueIo} ({@code binaryRecords}, {@code jsonWriteAll}, …) all reduce to {@link #recordsStream} or
 * {@link #writeAll} over a freshly constructed reader/writer.
 *
 * <p>
 * Centralising the spliterator, framed-vs-streaming write semantics, and class-to-descriptor coercion here keeps
 * {@code ValueIo} focused on its I/O responsibilities and removes ~50 lines of repeated plumbing.
 */
final class StreamApi {

  private StreamApi() {}

  @SuppressWarnings("unchecked")
  static <T> TypeDescriptor<T> requireDescriptor(Class<T> type) {
    Objects.requireNonNull(type, "type must not be null");
    return (TypeDescriptor<T>) TypeDescriptor.find(type).orElseThrow(() ->
      new IllegalArgumentException("No TypeDescriptor registered for " + type.getName())
    );
  }

  static <T> Stream<Value<T>> recordsStream(AbstractStateReader reader, TypeDescriptor<T> descriptor) {
    Objects.requireNonNull(descriptor, "descriptor must not be null");
    var spliterator = new RecordSpliterator<>(reader, descriptor);
    return StreamSupport.stream(spliterator, false).onClose(reader::close);
  }

  static <T> void writeAll(
    AbstractStateWriter w,
    TypeDescriptor<T> descriptor,
    Protocol.Mode mode,
    Stream<? extends Value<T>> values
  ) {
    Objects.requireNonNull(descriptor, "descriptor must not be null");
    if (mode == Protocol.Mode.FRAMED) {
      Iterator<? extends Value<T>> it = values.iterator();
      if (!it.hasNext()) {
        throw new IllegalStateException("framed mode requires exactly one record, got 0");
      }
      w.write(it.next(), descriptor);
      if (it.hasNext()) {
        throw new IllegalStateException("framed mode requires exactly one record, got 2 or more");
      }
    } else {
      values.forEach(v -> w.write(v, descriptor));
    }
  }

  /**
   * Pulls one {@link Value} per {@link #tryAdvance(Consumer)} via {@link AbstractStateReader#read(TypeDescriptor)},
   * then consumes the format's record separator (no-op in FRAMED mode). Sequential only — {@link #trySplit()} returns
   * {@code null}.
   */
  private static final class RecordSpliterator<T> extends Spliterators.AbstractSpliterator<Value<T>> {

    private final AbstractStateReader reader;
    private final TypeDescriptor<T> descriptor;

    RecordSpliterator(AbstractStateReader reader, TypeDescriptor<T> descriptor) {
      super(Long.MAX_VALUE, ORDERED | NONNULL | IMMUTABLE);
      this.reader = reader;
      this.descriptor = descriptor;
    }

    @Override
    public boolean tryAdvance(Consumer<? super Value<T>> action) {
      if (!reader.hasMoreRecords()) {
        return false;
      }
      Value<T> v = reader.read(descriptor);
      reader.consumeRecordSeparator();
      action.accept(v);
      return true;
    }

    @Override
    public Spliterator<Value<T>> trySplit() {
      // The reader is thread-confined (mutable cursor + frame state) and the decode contract is sequential even
      // under .parallel(). AbstractSpliterator's inherited trySplit would let the fork/join machinery advance the
      // reader from more than one thread; refusing to split keeps every tryAdvance on one thread, so only the
      // downstream stream operations run in parallel.
      return null;
    }
  }
}
