package dev.zems.lib.value.marshal;

import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.util.List;
import java.util.Objects;

/**
 * A {@link StateWriter} that delegates all operations to multiple sub-writers. Useful for tee-style output,
 * simultaneous writes to multiple formats, or pairing production writes with logging.
 */
public class CombinedStateWriter implements StateWriter {

  private final List<StateWriter> writers;

  public CombinedStateWriter(StateWriter... writers) {
    this(List.of(writers));
  }

  public CombinedStateWriter(List<StateWriter> writers) {
    Objects.requireNonNull(writers, "writers must not be null");
    if (writers.isEmpty()) {
      throw new IllegalArgumentException("writers must not be empty");
    }
    this.writers = List.copyOf(writers);
  }

  @Override
  public void writeBoolean(int id, String name, boolean value) {
    writers.forEach(w -> w.writeBoolean(id, name, value));
  }

  @Override
  public void writeChar(int id, String name, char value) {
    writers.forEach(w -> w.writeChar(id, name, value));
  }

  @Override
  public void writeShort(int id, String name, short value) {
    writers.forEach(w -> w.writeShort(id, name, value));
  }

  @Override
  public void writeInt(int id, String name, int value) {
    writers.forEach(w -> w.writeInt(id, name, value));
  }

  @Override
  public void writeLong(int id, String name, long value) {
    writers.forEach(w -> w.writeLong(id, name, value));
  }

  @Override
  public void writeFloat(int id, String name, float value) {
    writers.forEach(w -> w.writeFloat(id, name, value));
  }

  @Override
  public void writeDouble(int id, String name, double value) {
    writers.forEach(w -> w.writeDouble(id, name, value));
  }

  @Override
  public void writeString(int id, String name, String value) {
    writers.forEach(w -> w.writeString(id, name, value));
  }

  @Override
  public void writeBytes(int id, String name, byte[] value) {
    writers.forEach(w -> w.writeBytes(id, name, value));
  }

  @Override
  public <H> void writeHeader(TypeDescriptor<H> descriptor, H header) {
    writers.forEach(w -> w.writeHeader(descriptor, header));
  }

  @Override
  public <F> void writeTerminator(TypeDescriptor<F> descriptor, F terminator) {
    writers.forEach(w -> w.writeTerminator(descriptor, terminator));
  }

  @Override
  public void beginNested(int id, String name) {
    writers.forEach(w -> w.beginNested(id, name));
  }

  @Override
  public void endNested(int id, String name) {
    writers.forEach(w -> w.endNested(id, name));
  }

  @Override
  public void writeNull(int id, String name) {
    writers.forEach(w -> w.writeNull(id, name));
  }

  @Override
  public void writeUndefined(int id, String name) {
    writers.forEach(w -> w.writeUndefined(id, name));
  }

  @Override
  public void writeUnresolved(int id, String name) {
    writers.forEach(w -> w.writeUnresolved(id, name));
  }

  @Override
  public void writeError(int id, String name, Throwable throwable) {
    writers.forEach(w -> w.writeError(id, name, throwable));
  }

  @Override
  public void writeTombstone(int id, String name) {
    writers.forEach(w -> w.writeTombstone(id, name));
  }

  @Override
  public <T> void writeRecord(int id, String name, TypeDescriptor<T> descriptor, T value) {
    writers.forEach(w -> w.writeRecord(id, name, descriptor, value));
  }

  @Override
  public void close() {
    writers.forEach(StateWriter::close);
  }
}
