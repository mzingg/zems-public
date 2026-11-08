package dev.zems.lib.value.valueio;

import dev.zems.lib.value.ValueState;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A {@link StateReader} that forwards every call to a master reader and to auxiliary readers. The master's results are
 * returned; the auxiliaries only advance in lock-step, and an exception from one of them propagates to the caller.
 */
public class CombinedStateReader implements StateReader {

  private final StateReader master;
  private final List<StateReader> others;

  public CombinedStateReader(StateReader master, StateReader... others) {
    this(master, List.of(others));
  }

  public CombinedStateReader(StateReader master, List<StateReader> others) {
    this.master = Objects.requireNonNull(master, "master must not be null");
    this.others = others == null ? List.of() : List.copyOf(others);
  }

  @Override
  public boolean hasField(int id, String name) {
    advanceAuxiliariesInLockStep(r -> r.hasField(id, name));
    return master.hasField(id, name);
  }

  @Override
  public boolean readBoolean(int id, String name) {
    advanceAuxiliariesInLockStep(r -> r.readBoolean(id, name));
    return master.readBoolean(id, name);
  }

  @Override
  public char readChar(int id, String name) {
    advanceAuxiliariesInLockStep(r -> r.readChar(id, name));
    return master.readChar(id, name);
  }

  @Override
  public short readShort(int id, String name) {
    advanceAuxiliariesInLockStep(r -> r.readShort(id, name));
    return master.readShort(id, name);
  }

  @Override
  public int readInt(int id, String name) {
    advanceAuxiliariesInLockStep(r -> r.readInt(id, name));
    return master.readInt(id, name);
  }

  @Override
  public long readLong(int id, String name) {
    advanceAuxiliariesInLockStep(r -> r.readLong(id, name));
    return master.readLong(id, name);
  }

  @Override
  public float readFloat(int id, String name) {
    advanceAuxiliariesInLockStep(r -> r.readFloat(id, name));
    return master.readFloat(id, name);
  }

  @Override
  public double readDouble(int id, String name) {
    advanceAuxiliariesInLockStep(r -> r.readDouble(id, name));
    return master.readDouble(id, name);
  }

  @Override
  public String readString(int id, String name) {
    advanceAuxiliariesInLockStep(r -> r.readString(id, name));
    return master.readString(id, name);
  }

  @Override
  public byte[] readBytes(int id, String name) {
    advanceAuxiliariesInLockStep(r -> r.readBytes(id, name));
    return master.readBytes(id, name);
  }

  @Override
  public <T> T readRecord(int id, String name, TypeDescriptor<T> descriptor) {
    advanceAuxiliariesInLockStep(r -> r.readRecord(id, name, descriptor));
    return master.readRecord(id, name, descriptor);
  }

  @Override
  public <H> H readHeader(TypeDescriptor<H> descriptor) {
    advanceAuxiliariesInLockStep(r -> r.readHeader(descriptor));
    return master.readHeader(descriptor);
  }

  @Override
  public <F> F readTerminator(TypeDescriptor<F> descriptor) {
    advanceAuxiliariesInLockStep(r -> r.readTerminator(descriptor));
    return master.readTerminator(descriptor);
  }

  @Override
  public void beginNested(int id, String name) {
    advanceAuxiliariesInLockStep(r -> r.beginNested(id, name));
    master.beginNested(id, name);
  }

  @Override
  public void endNested(int id, String name) {
    advanceAuxiliariesInLockStep(r -> r.endNested(id, name));
    master.endNested(id, name);
  }

  @Override
  public ValueState peekValueStateOrNull(int id, String name) {
    advanceAuxiliariesInLockStep(r -> r.peekValueStateOrNull(id, name));
    return master.peekValueStateOrNull(id, name);
  }

  @Override
  public Throwable readError(int id, String name) {
    advanceAuxiliariesInLockStep(r -> r.readError(id, name));
    return master.readError(id, name);
  }

  @Override
  public String readUnsupported(int id, String name) {
    advanceAuxiliariesInLockStep(r -> r.readUnsupported(id, name));
    return master.readUnsupported(id, name);
  }

  @Override
  public void close() {
    advanceAuxiliariesInLockStep(StateReader::close);
    master.close();
  }

  private void advanceAuxiliariesInLockStep(Consumer<StateReader> operation) {
    for (var reader : others) {
      operation.accept(reader);
    }
  }
}
