package dev.zems.lib.value.valueio;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.util.Iterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** Helpers shared by the {@code ValueIo} Stream-API entry points. */
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
      // The reader is thread-confined, and the inherited trySplit would let fork/join advance it from several
      // threads under .parallel(). Refusing to split keeps every tryAdvance on one thread; only the downstream
      // operations run in parallel.
      return null;
    }
  }
}
