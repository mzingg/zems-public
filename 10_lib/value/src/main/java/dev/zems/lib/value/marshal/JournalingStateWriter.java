package dev.zems.lib.value.marshal;

import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe {@link StateWriter} that journals all operations. Useful for testing marshalling logic and production
 * debugging by capturing the operation sequence. Implements {@link StateWriter} directly — no Protocol envelope.
 */
public class JournalingStateWriter implements StateWriter {

  private final List<String> journal = new CopyOnWriteArrayList<>();

  public List<String> journal() {
    return List.copyOf(journal);
  }

  @Override
  public void writeBoolean(int id, String name, boolean value) {
    journal.add("writeBoolean(" + id + ", " + name + ", " + value + ")");
  }

  @Override
  public void writeChar(int id, String name, char value) {
    journal.add("writeChar(" + id + ", " + name + ", " + value + ")");
  }

  @Override
  public void writeShort(int id, String name, short value) {
    journal.add("writeShort(" + id + ", " + name + ", " + value + ")");
  }

  @Override
  public void writeInt(int id, String name, int value) {
    journal.add("writeInt(" + id + ", " + name + ", " + value + ")");
  }

  @Override
  public void writeLong(int id, String name, long value) {
    journal.add("writeLong(" + id + ", " + name + ", " + value + ")");
  }

  @Override
  public void writeFloat(int id, String name, float value) {
    journal.add("writeFloat(" + id + ", " + name + ", " + value + ")");
  }

  @Override
  public void writeDouble(int id, String name, double value) {
    journal.add("writeDouble(" + id + ", " + name + ", " + value + ")");
  }

  @Override
  public void writeString(int id, String name, String value) {
    journal.add("writeString(" + id + ", " + name + ", " + value + ")");
  }

  @Override
  public void writeBytes(int id, String name, byte[] value) {
    journal.add("writeBytes(" + id + ", " + name + ", " + Arrays.toString(value) + ")");
  }

  @Override
  public <H> void writeHeader(TypeDescriptor<H> descriptor, H header) {
    journal.add("writeHeader(" + descriptor.descriptorName() + ", " + header + ")");
  }

  @Override
  public <F> void writeTerminator(TypeDescriptor<F> descriptor, F terminator) {
    journal.add("writeTerminator(" + descriptor.descriptorName() + ", " + terminator + ")");
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
  public void writeNull(int id, String name) {
    journal.add("writeNull(" + id + ", " + name + ")");
  }

  @Override
  public void writeUndefined(int id, String name) {
    journal.add("writeUndefined(" + id + ", " + name + ")");
  }

  @Override
  public void writeUnresolved(int id, String name) {
    journal.add("writeUnresolved(" + id + ", " + name + ")");
  }

  @Override
  public void writeError(int id, String name, Throwable throwable) {
    journal.add(
      "writeError(" + id + ", " + name + ", " + throwable.getClass().getName() + ": " + throwable.getMessage() + ")"
    );
  }

  @Override
  public void writeTombstone(int id, String name) {
    journal.add("writeTombstone(" + id + ", " + name + ")");
  }

  @Override
  public <T> void writeRecord(int id, String name, TypeDescriptor<T> descriptor, T value) {
    journal.add("writeRecord(" + id + ", " + name + ", " + descriptor.descriptorName() + ", " + value + ")");
  }

  @Override
  public void close() {
    journal.add("close()");
  }
}
