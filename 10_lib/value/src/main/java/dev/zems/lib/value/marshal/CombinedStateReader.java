package dev.zems.lib.value.marshal;

import dev.zems.lib.value.ValueState;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A {@link StateReader} that delegates all operations to multiple sub-readers. The "master" reader's values are
 * returned; auxiliary readers receive the same calls but their results are discarded.
 *
 * <p>
 * <b>Auxiliary exceptions propagate</b> to the caller — symmetric with
 * {@link CombinedStateWriter}. If a divergent or corrupt auxiliary source matters to the use case, callers learn about
 * it rather than silently. Callers that genuinely want best-effort tee semantics should wrap each auxiliary in their
 * own {@code try / catch} before composing.
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
    delegateToOthers(r -> r.hasField(id, name));
    return master.hasField(id, name);
  }

  @Override
  public boolean readBoolean(int id, String name) {
    delegateToOthers(r -> r.readBoolean(id, name));
    return master.readBoolean(id, name);
  }

  @Override
  public char readChar(int id, String name) {
    delegateToOthers(r -> r.readChar(id, name));
    return master.readChar(id, name);
  }

  @Override
  public short readShort(int id, String name) {
    delegateToOthers(r -> r.readShort(id, name));
    return master.readShort(id, name);
  }

  @Override
  public int readInt(int id, String name) {
    delegateToOthers(r -> r.readInt(id, name));
    return master.readInt(id, name);
  }

  @Override
  public long readLong(int id, String name) {
    delegateToOthers(r -> r.readLong(id, name));
    return master.readLong(id, name);
  }

  @Override
  public float readFloat(int id, String name) {
    delegateToOthers(r -> r.readFloat(id, name));
    return master.readFloat(id, name);
  }

  @Override
  public double readDouble(int id, String name) {
    delegateToOthers(r -> r.readDouble(id, name));
    return master.readDouble(id, name);
  }

  @Override
  public String readString(int id, String name) {
    delegateToOthers(r -> r.readString(id, name));
    return master.readString(id, name);
  }

  @Override
  public byte[] readBytes(int id, String name) {
    delegateToOthers(r -> r.readBytes(id, name));
    return master.readBytes(id, name);
  }

  @Override
  public <T> T readRecord(int id, String name, TypeDescriptor<T> descriptor) {
    delegateToOthers(r -> r.readRecord(id, name, descriptor));
    return master.readRecord(id, name, descriptor);
  }

  @Override
  public <H> H readHeader(TypeDescriptor<H> descriptor) {
    delegateToOthers(r -> r.readHeader(descriptor));
    return master.readHeader(descriptor);
  }

  @Override
  public <F> F readTerminator(TypeDescriptor<F> descriptor) {
    delegateToOthers(r -> r.readTerminator(descriptor));
    return master.readTerminator(descriptor);
  }

  @Override
  public void beginNested(int id, String name) {
    delegateToOthers(r -> r.beginNested(id, name));
    master.beginNested(id, name);
  }

  @Override
  public void endNested(int id, String name) {
    delegateToOthers(r -> r.endNested(id, name));
    master.endNested(id, name);
  }

  @Override
  public ValueState peekValueStateOrNull(int id, String name) {
    delegateToOthers(r -> r.peekValueStateOrNull(id, name));
    return master.peekValueStateOrNull(id, name);
  }

  @Override
  @SuppressWarnings("ThrowableNotThrown") // intentional: auxiliaries' Throwable is consumed for sync only.
  public Throwable readError(int id, String name) {
    // Auxiliaries' returned Throwable is intentionally discarded — they're only invoked to keep
    // their wire position in lock-step with master. Only master's value flows back to the caller.
    delegateToOthers(r -> r.readError(id, name));
    return master.readError(id, name);
  }

  @Override
  public void close() {
    delegateToOthers(StateReader::close);
    master.close();
  }

  private void delegateToOthers(Consumer<StateReader> operation) {
    for (var reader : others) {
      operation.accept(reader);
    }
  }
}
