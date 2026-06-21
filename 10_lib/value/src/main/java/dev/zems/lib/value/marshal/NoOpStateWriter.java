package dev.zems.lib.value.marshal;

import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;

/**
 * No-op {@link StateWriter} that discards all writes. Useful for benchmarking, validation, and as a placeholder in
 * tests. Implements {@link StateWriter} directly — no Protocol envelope, no checksum, no byte counting.
 */
public class NoOpStateWriter implements StateWriter {

  @Override
  public void writeBoolean(int id, String name, boolean value) {}

  @Override
  public void writeChar(int id, String name, char value) {}

  @Override
  public void writeShort(int id, String name, short value) {}

  @Override
  public void writeInt(int id, String name, int value) {}

  @Override
  public void writeLong(int id, String name, long value) {}

  @Override
  public void writeFloat(int id, String name, float value) {}

  @Override
  public void writeDouble(int id, String name, double value) {}

  @Override
  public void writeString(int id, String name, String value) {}

  @Override
  public void writeBytes(int id, String name, byte[] value) {}

  @Override
  public <H> void writeHeader(TypeDescriptor<H> descriptor, H header) {}

  @Override
  public <F> void writeTerminator(TypeDescriptor<F> descriptor, F terminator) {}

  @Override
  public void beginNested(int id, String name) {}

  @Override
  public void endNested(int id, String name) {}

  @Override
  public void writeNull(int id, String name) {}

  @Override
  public void writeUndefined(int id, String name) {}

  @Override
  public void writeUnresolved(int id, String name) {}

  @Override
  public void writeError(int id, String name, Throwable throwable) {}

  @Override
  public void writeTombstone(int id, String name) {}

  @Override
  public <T> void writeRecord(int id, String name, TypeDescriptor<T> descriptor, T value) {}

  @Override
  public void close() {}
}
