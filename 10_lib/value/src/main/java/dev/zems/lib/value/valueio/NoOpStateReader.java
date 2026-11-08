package dev.zems.lib.value.valueio;

import dev.zems.lib.value.ValueState;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;

/**
 * A {@link StateReader} that reads nothing and answers zero values; it has no protocol envelope. {@link #readError}
 * and {@link #readUnsupported} throw {@link UnsupportedOperationException}.
 */
public class NoOpStateReader implements StateReader {

  @Override
  public boolean hasField(int id, String name) {
    return false;
  }

  @Override
  public boolean readBoolean(int id, String name) {
    return false;
  }

  @Override
  public char readChar(int id, String name) {
    return '\0';
  }

  @Override
  public short readShort(int id, String name) {
    return 0;
  }

  @Override
  public int readInt(int id, String name) {
    return 0;
  }

  @Override
  public long readLong(int id, String name) {
    return 0L;
  }

  @Override
  public float readFloat(int id, String name) {
    return 0.0f;
  }

  @Override
  public double readDouble(int id, String name) {
    return 0.0;
  }

  @Override
  public String readString(int id, String name) {
    return null;
  }

  @Override
  public byte[] readBytes(int id, String name) {
    return new byte[0];
  }

  @Override
  public <T> T readRecord(int id, String name, TypeDescriptor<T> descriptor) {
    return null;
  }

  @Override
  public <H> H readHeader(TypeDescriptor<H> descriptor) {
    return null;
  }

  @Override
  public <F> F readTerminator(TypeDescriptor<F> descriptor) {
    return null;
  }

  @Override
  public void beginNested(int id, String name) {}

  @Override
  public void endNested(int id, String name) {}

  @Override
  public ValueState peekValueStateOrNull(int id, String name) {
    return null;
  }

  @Override
  public Throwable readError(int id, String name) {
    throw new UnsupportedOperationException("NoOpStateReader.readError");
  }

  @Override
  public String readUnsupported(int id, String name) {
    throw new UnsupportedOperationException("NoOpStateReader.readUnsupported");
  }

  @Override
  public void close() {}
}
