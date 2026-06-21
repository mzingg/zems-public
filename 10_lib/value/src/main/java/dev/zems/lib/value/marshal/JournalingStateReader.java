package dev.zems.lib.value.marshal;

import dev.zems.lib.value.ValueState;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe {@link StateReader} that journals all read operations. Returns default values for primitive reads — the
 * focus is on operation order, not values. Implements {@link StateReader} directly — no Protocol envelope.
 */
public class JournalingStateReader implements StateReader {

  private final List<String> journal = new CopyOnWriteArrayList<>();

  public List<String> journal() {
    return List.copyOf(journal);
  }

  @Override
  public boolean hasField(int id, String name) {
    journal.add("hasField(" + id + ", " + name + ")");
    return false;
  }

  @Override
  public boolean readBoolean(int id, String name) {
    journal.add("readBoolean(" + id + ", " + name + ")");
    return false;
  }

  @Override
  public char readChar(int id, String name) {
    journal.add("readChar(" + id + ", " + name + ")");
    return '\0';
  }

  @Override
  public short readShort(int id, String name) {
    journal.add("readShort(" + id + ", " + name + ")");
    return 0;
  }

  @Override
  public int readInt(int id, String name) {
    journal.add("readInt(" + id + ", " + name + ")");
    return 0;
  }

  @Override
  public long readLong(int id, String name) {
    journal.add("readLong(" + id + ", " + name + ")");
    return 0L;
  }

  @Override
  public float readFloat(int id, String name) {
    journal.add("readFloat(" + id + ", " + name + ")");
    return 0.0f;
  }

  @Override
  public double readDouble(int id, String name) {
    journal.add("readDouble(" + id + ", " + name + ")");
    return 0.0;
  }

  @Override
  public String readString(int id, String name) {
    journal.add("readString(" + id + ", " + name + ")");
    return null;
  }

  @Override
  public byte[] readBytes(int id, String name) {
    journal.add("readBytes(" + id + ", " + name + ")");
    return new byte[0];
  }

  @Override
  public <T> T readRecord(int id, String name, TypeDescriptor<T> descriptor) {
    journal.add("readRecord(" + id + ", " + name + ", " + descriptor.descriptorName() + ")");
    return null;
  }

  @Override
  public <H> H readHeader(TypeDescriptor<H> descriptor) {
    journal.add("readHeader(" + descriptor.descriptorName() + ")");
    return null;
  }

  @Override
  public <F> F readTerminator(TypeDescriptor<F> descriptor) {
    journal.add("readTerminator(" + descriptor.descriptorName() + ")");
    return null;
  }

  @Override
  public void beginNested(int id, String name) {
    journal.add("beginNested(" + id + ", " + name + ")");
  }

  @Override
  public void endNested(int id, String name) {
    journal.add("endNested(" + id + ", " + name + ")");
  }

  @Override
  public ValueState peekValueStateOrNull(int id, String name) {
    journal.add("peekValueStateOrNull(" + id + ", " + name + ")");
    return null;
  }

  @Override
  public Throwable readError(int id, String name) {
    journal.add("readError(" + id + ", " + name + ")");
    return new IllegalStateException("journaled");
  }

  @Override
  public void close() {
    journal.add("close()");
  }
}
