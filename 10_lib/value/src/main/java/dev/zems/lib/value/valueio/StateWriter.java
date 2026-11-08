package dev.zems.lib.value.valueio;

import dev.zems.lib.value.ErrorValue;
import dev.zems.lib.value.NullValue;
import dev.zems.lib.value.TombstoneValue;
import dev.zems.lib.value.UndefinedValue;
import dev.zems.lib.value.UnresolvedValue;
import dev.zems.lib.value.UnsupportedValue;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import dev.zems.lib.value.valueio.wire.WireConstraints;
import java.util.Objects;

/**
 * Format-agnostic writer of marshalled state; use it in a try-with-resources block.
 *
 * <p>
 * Every slot method takes a non-negative slot {@code id} and a {@code name}: binary formats key by id, JSON by name.
 * Top-level and single-slot writes use id {@code 0}.
 */
public interface StateWriter extends AutoCloseable {
  String PAYLOAD_SLOT_NAME = "$payload";

  void writeBoolean(int id, String name, boolean value);

  void writeChar(int id, String name, char value);

  void writeShort(int id, String name, short value);

  void writeInt(int id, String name, int value);

  void writeLong(int id, String name, long value);

  void writeFloat(int id, String name, float value);

  void writeDouble(int id, String name, double value);

  void writeString(int id, String name, String value);

  void writeBytes(int id, String name, byte[] value);

  /** Writes the header; must be the first call on the stream. */
  <H> void writeHeader(TypeDescriptor<H> descriptor, H header);

  /** Writes the terminator; must be the last data call before {@link #close()}. */
  <F> void writeTerminator(TypeDescriptor<F> descriptor, F terminator);

  void beginNested(int id, String name);

  void endNested(int id, String name);

  /** Wire-level safety bounds the collection descriptors check before emitting any bytes. */
  default WireConstraints wireConstraints() {
    return WireConstraints.SECURE_DEFAULTS;
  }

  /**
   * Writes the top-level value with the descriptor inferred from {@link Value#valueType()}. Throws
   * {@link IllegalStateException} when the value cannot self-describe (bare state markers, empty or heterogeneous
   * collections); pass the descriptor to {@link #write(Value, TypeDescriptor)} instead.
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

  /** Writes the top-level value at slot {@code 0}. */
  default <T> void write(Value<T> value, TypeDescriptor<T> descriptor) {
    write(0, PAYLOAD_SLOT_NAME, value, descriptor);
  }

  /**
   * Writes the value at the slot: a state marker goes to its dedicated wire form, anything else is unboxed and written
   * through {@code descriptor}.
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
      case UnsupportedValue<T> unsupported -> writeUnsupported(id, name, unsupported.reason());
      default -> writeRecord(id, name, descriptor, value.unbox());
    }
  }

  void writeNull(int id, String name);

  void writeUndefined(int id, String name);

  void writeUnresolved(int id, String name);

  void writeError(int id, String name, Throwable throwable);

  void writeTombstone(int id, String name);

  /** Writes the unsupported marker and its reason; the expected-type descriptor stays on the reading side. */
  void writeUnsupported(int id, String name, String reason);

  <T> void writeRecord(int id, String name, TypeDescriptor<T> descriptor, T value);

  /** Flushes, writes the terminator where the mode has one, and releases resources; throws no checked exception. */
  @Override
  void close();
}
