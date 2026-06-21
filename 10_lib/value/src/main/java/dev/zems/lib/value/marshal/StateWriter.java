package dev.zems.lib.value.marshal;

import dev.zems.lib.value.BoxedValue;
import dev.zems.lib.value.CoreValue;
import dev.zems.lib.value.ErrorValue;
import dev.zems.lib.value.NullValue;
import dev.zems.lib.value.TombstoneValue;
import dev.zems.lib.value.UndefinedValue;
import dev.zems.lib.value.UnresolvedValue;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import dev.zems.lib.value.marshal.wire.WireConstraints;
import java.util.Objects;

/**
 * Format-agnostic interface for writing state during marshalling.
 *
 * <p>
 * Concrete implementations (binary, JSON) handle the actual encoding. Use within a try-with-resources block —
 * {@link #close()} flushes buffers, releases handles, and finalizes any pending state.
 *
 * <p>
 * Every operation takes both an explicit slot {@code id} (non-negative; the descriptor-local wire identity) and a
 * human-readable {@code name}. Id-anchored wire formats (binary) encode the id; name-keyed formats (JSON) use the name.
 * For top-level/single-slot writes the conventional id is {@code 0}.
 */
public interface StateWriter extends AutoCloseable {
  String PAYLOAD_SLOT_NAME = "$payload";

  // ============ Primitives ============

  void writeBoolean(int id, String name, boolean value);

  void writeChar(int id, String name, char value);

  void writeShort(int id, String name, short value);

  void writeInt(int id, String name, int value);

  void writeLong(int id, String name, long value);

  void writeFloat(int id, String name, float value);

  void writeDouble(int id, String name, double value);

  void writeString(int id, String name, String value);

  void writeBytes(int id, String name, byte[] value);

  // ============ Header / Terminator ============

  /**
   * Writes a typed header at the very start of the stream. Must be the first call.
   */
  <H> void writeHeader(TypeDescriptor<H> descriptor, H header);

  /**
   * Writes a typed terminator at the very end of the stream. Must be the last data call before {@link #close()}.
   */
  <F> void writeTerminator(TypeDescriptor<F> descriptor, F terminator);

  // ============ Structure ============

  void beginNested(int id, String name);

  void endNested(int id, String name);

  // ============ High-level Value boundary (default methods) ============

  /**
   * Wire-level safety bounds applied during write. The default returns {@link WireConstraints#SECURE_DEFAULTS};
   * protocol-bound writers (the typical {@link AbstractStateWriter} subclasses) override to expose their underlying
   * protocol's constraints. Used by the collection descriptors to reject an over-large list/map before any wire bytes
   * are emitted — the symmetric counterpart of {@link StateReader#wireConstraints()}.
   */
  default WireConstraints wireConstraints() {
    return WireConstraints.SECURE_DEFAULTS;
  }

  /**
   * Inferred top-level Value write — resolves the descriptor from {@link Value#valueType()} and delegates to
   * {@link #write(Value, TypeDescriptor)}. Useful when the caller doesn't have the descriptor at hand and the value can
   * self-describe (built-in scalars, records via {@link BoxedValue}, typed collections, {@link CoreValue} implementors
   * with a registered descriptor).
   *
   * <p>
   * Two failure paths, both sharpened:
   *
   * <ul>
   * <li><b>{@code valueType()} returns {@code null}.</b> Bare state markers
   * ({@link NullValue}, {@link UndefinedValue}, {@link UnresolvedValue}, {@link TombstoneValue})
   * carry no payload type; empty / heterogeneous collections and tree-layer markers also return
   * {@code null}. Throws {@link IllegalStateException} naming the value's runtime class.
   * <li><b>{@code valueType()} returns a non-null descriptor.</b> Accepted — round-trips through
   * the existing {@code write(value, descriptor)} path.
   * </ul>
   *
   * <p>
   * In both cases the explicit {@code write(value, descriptor)} overload is the escape hatch:
   * pass the descriptor explicitly when inference can't resolve it.
   */
  default <T> void write(Value<T> value) {
    Objects.requireNonNull(value, "value must not be null");
    TypeDescriptor<T> descriptor = value.valueType();
    if (descriptor == null) {
      throw new IllegalStateException(
        "Cannot infer TypeDescriptor for " +
          value.getClass().getSimpleName() +
          " — valueType() returned null. " +
          "Pass an explicit descriptor via write(value, descriptor)."
      );
    }
    write(value, descriptor);
  }

  /**
   * Top-level Value write at slot {@code 0} / {@link #PAYLOAD_SLOT_NAME}.
   */
  default <T> void write(Value<T> value, TypeDescriptor<T> descriptor) {
    write(0, PAYLOAD_SLOT_NAME, value, descriptor);
  }

  /**
   * Writes a {@link Value} at the given slot, dispatching state markers (NULL/UNDEFINED/UNRESOLVED/ERROR) to their
   * dedicated wire forms or routing through {@link Value#unbox(Value)} + {@link #writeRecord} for typed payloads.
   *
   * <p>
   * Canonical recursive entry point for the high-level Value boundary. Collection descriptors (List, Map) call this
   * default per-element/per-entry-value with their own slot ids; the top-level form
   * {@link #write(Value, TypeDescriptor)} delegates here with slot 0 / {@link #PAYLOAD_SLOT_NAME}.
   */
  default <T> void write(int id, String name, Value<T> value, TypeDescriptor<T> descriptor) {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(value, "value must not be null");
    Objects.requireNonNull(descriptor, "descriptor must not be null");
    switch (value) {
      case NullValue<T> _ -> writeNull(id, name);
      case UndefinedValue<T> _ -> writeUndefined(id, name);
      case UnresolvedValue<T> _ -> writeUnresolved(id, name);
      case ErrorValue<T> e -> writeError(id, name, e.throwable());
      case TombstoneValue<T> _ -> writeTombstone(id, name);
      default -> writeRecord(id, name, descriptor, Value.unbox(value));
    }
  }

  // ============ State markers ============

  /**
   * Writes a {@link dev.zems.lib.value.ValueState#NULL} marker at the named slot.
   */
  void writeNull(int id, String name);

  /**
   * Writes a {@link dev.zems.lib.value.ValueState#UNDEFINED} marker at the named slot.
   */
  void writeUndefined(int id, String name);

  /**
   * Writes a {@link dev.zems.lib.value.ValueState#UNRESOLVED} marker at the named slot.
   */
  void writeUnresolved(int id, String name);

  /**
   * Writes a {@link dev.zems.lib.value.ValueState#ERROR} marker (with throwable info) at the named slot.
   */
  void writeError(int id, String name, Throwable throwable);

  /**
   * Writes a {@link dev.zems.lib.value.ValueState#TOMBSTONE} marker at the named slot.
   */
  void writeTombstone(int id, String name);

  // ============ Composite records ============

  /**
   * Writes a typed composite record under the named slot.
   */
  <T> void writeRecord(int id, String name, TypeDescriptor<T> descriptor, T value);

  // ============ Lifecycle ============

  /**
   * Flushes buffers, releases resources, and finalizes any pending state. No checked exceptions.
   */
  @Override
  void close();
}
