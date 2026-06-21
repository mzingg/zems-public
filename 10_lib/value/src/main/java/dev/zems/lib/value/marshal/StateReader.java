package dev.zems.lib.value.marshal;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.ValueState;
import dev.zems.lib.value.marshal.descriptor.SlotSpec;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import dev.zems.lib.value.marshal.wire.WireConstraints;
import java.util.Objects;

/**
 * Format-agnostic interface for reading state during unmarshalling. Use within a try-with-resources block —
 * {@link #close()} releases handles and validates any pending state (e.g. terminator verification on a
 * {@link Protocol}-wrapped reader).
 *
 * <p>
 * Every operation takes both an explicit slot {@code id} (non-negative; the descriptor-local wire identity) and a
 * human-readable {@code name}. Id-anchored wire formats (binary) read by id; name-keyed formats (JSON) read by name.
 * Aliases for renames live on {@link SlotSpec#aliases()} and are consulted by JSON only — binary uses the id as the
 * sole identity, so aliases are never threaded through this interface.
 */
public interface StateReader extends AutoCloseable {
  // ============ Primitives ============

  default boolean readBooleanOr(int id, String name, boolean defaultValue) {
    return hasField(id, name) ? readBoolean(id, name) : defaultValue;
  }

  /**
   * True when the wire has a slot identifiable by either {@code id} (binary) or {@code name} (JSON). Format
   * implementations consult whichever is meaningful for their wire shape; the other parameter serves as a defensive
   * label.
   */
  boolean hasField(int id, String name);

  boolean readBoolean(int id, String name);

  default char readCharOr(int id, String name, char defaultValue) {
    return hasField(id, name) ? readChar(id, name) : defaultValue;
  }

  char readChar(int id, String name);

  default short readShortOr(int id, String name, short defaultValue) {
    return hasField(id, name) ? readShort(id, name) : defaultValue;
  }

  short readShort(int id, String name);

  default int readIntOr(int id, String name, int defaultValue) {
    return hasField(id, name) ? readInt(id, name) : defaultValue;
  }

  int readInt(int id, String name);

  default long readLongOr(int id, String name, long defaultValue) {
    return hasField(id, name) ? readLong(id, name) : defaultValue;
  }

  long readLong(int id, String name);

  default float readFloatOr(int id, String name, float defaultValue) {
    return hasField(id, name) ? readFloat(id, name) : defaultValue;
  }

  float readFloat(int id, String name);

  default double readDoubleOr(int id, String name, double defaultValue) {
    return hasField(id, name) ? readDouble(id, name) : defaultValue;
  }

  double readDouble(int id, String name);

  default String readStringOr(int id, String name, String defaultValue) {
    return hasField(id, name) ? readString(id, name) : defaultValue;
  }

  String readString(int id, String name);

  default byte[] readBytesOr(int id, String name, byte[] defaultValue) {
    return hasField(id, name) ? readBytes(id, name) : defaultValue;
  }

  byte[] readBytes(int id, String name);

  // ============ Composite records ============

  /** Composite-record read with a default returned when no candidate slot matches. */
  default <T> T readRecordOr(int id, String name, TypeDescriptor<T> descriptor, T defaultValue) {
    return hasField(id, name) ? readRecord(id, name, descriptor) : defaultValue;
  }

  /** Reads a typed composite record nested under {@code (id, name)}. */
  <T> T readRecord(int id, String name, TypeDescriptor<T> descriptor);

  // ============ Header / Terminator ============

  <H> H readHeader(TypeDescriptor<H> descriptor);

  <F> F readTerminator(TypeDescriptor<F> descriptor);

  // ============ Migration support ============

  /**
   * Wire signature of the record currently being read, or {@code ""} when not inside a record or when type verification
   * is off. Set from the {@code descriptorName@signature} suffix carried by each record when
   * {@link Protocol.V1#typeVerificationEnabled()} is on.
   *
   * <p>
   * Descriptors performing migration branching capture historical signatures as constants and compare against this
   * value to switch between old and new field-layout reads.
   */
  default String recordSignature() {
    return "";
  }

  // ============ Structure ============

  void beginNested(int id, String name);

  void endNested(int id, String name);

  // ============ High-level Value boundary (default methods) ============

  /**
   * Wire-level safety bounds applied during read. The default returns {@link WireConstraints#SECURE_DEFAULTS};
   * protocol-bound readers (the typical {@link AbstractStateReader} subclasses) override to expose their underlying
   * protocol's constraints. Used by collection descriptors to bound size-driven allocations before they happen.
   */
  default WireConstraints wireConstraints() {
    return WireConstraints.SECURE_DEFAULTS;
  }

  /** Top-level Value read with descriptor lookup by class. */
  default <T> Value<T> read(Class<T> clazz) {
    Objects.requireNonNull(clazz, "clazz must not be null");
    @SuppressWarnings("unchecked")
    var td = (TypeDescriptor<T>) TypeDescriptor.find(clazz).orElseThrow(() ->
      new IllegalStateException("No TypeDescriptor registered for " + clazz.getName())
    );
    return read(td);
  }

  /** Top-level Value read at slot {@code 0} / {@link StateWriter#PAYLOAD_SLOT_NAME}. */
  default <T> Value<T> read(TypeDescriptor<T> descriptor) {
    return read(0, StateWriter.PAYLOAD_SLOT_NAME, descriptor);
  }

  /**
   * Reads a {@link Value} at the given slot, dispatching state markers (NULL/UNDEFINED/UNRESOLVED/ERROR) before falling
   * through to a typed-record read driven by {@code descriptor}. The result is then lifted into the {@code Value}
   * hierarchy via {@link Value#of(Object)}.
   *
   * <p>
   * This is the canonical recursive entry point for the high-level Value boundary. Collection descriptors (List, Map)
   * call this default per-element with element-specific slot ids; the top-level form {@link #read(TypeDescriptor)}
   * delegates here with slot 0 / {@link StateWriter#PAYLOAD_SLOT_NAME}.
   */
  default <T> Value<T> read(int id, String name, TypeDescriptor<T> descriptor) {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(descriptor, "descriptor must not be null");
    var state = peekValueStateOrNull(id, name);
    if (state != null) {
      return switch (state) {
        case NULL -> Value.nullValue();
        case UNDEFINED -> Value.undefined();
        case UNRESOLVED -> Value.unresolved();
        case ERROR -> Value.errorOf(readError(id, name), descriptor);
        case TOMBSTONE -> Value.tombstone();
      };
    }
    return descriptor.box(readRecord(id, name, descriptor));
  }

  // ============ State markers ============

  /** Returns the state marker at the named slot, or {@code null} if next is a typed payload. */
  ValueState peekValueStateOrNull(int id, String name);

  /** Reads error throwable info after {@link #peekValueStateOrNull} returned {@link ValueState#ERROR}. */
  Throwable readError(int id, String name);

  // ============ Lifecycle ============

  /** Releases resources and validates any pending state. No checked exceptions. */
  @Override
  void close();
}
