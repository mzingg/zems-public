package dev.zems.lib.value.valueio;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.ValueState;
import dev.zems.lib.value.valueio.descriptor.SlotSpec;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import dev.zems.lib.value.valueio.wire.WireConstraints;
import java.util.Objects;

/**
 * Format-agnostic reader of marshalled state; use it in a try-with-resources block.
 *
 * <p>
 * Every slot method takes a non-negative slot {@code id} and a {@code name}: binary formats read by id, JSON by name.
 * Slot aliases ({@link SlotSpec#aliases()}) apply to name-keyed formats only and never pass through this interface.
 */
public interface StateReader extends AutoCloseable {
  default boolean readBooleanOr(int id, String name, boolean defaultValue) {
    return hasField(id, name) ? readBoolean(id, name) : defaultValue;
  }

  /** True when the wire has a slot for {@code id} (binary) or {@code name} (JSON), whichever the format keys by. */
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

  default <T> T readRecordOr(int id, String name, TypeDescriptor<T> descriptor, T defaultValue) {
    return hasField(id, name) ? readRecord(id, name, descriptor) : defaultValue;
  }

  <T> T readRecord(int id, String name, TypeDescriptor<T> descriptor);

  <H> H readHeader(TypeDescriptor<H> descriptor);

  <F> F readTerminator(TypeDescriptor<F> descriptor);

  /**
   * Wire signature of the record being read, or {@code ""} outside a record or when type verification is off. A
   * descriptor compares it with captured historical signatures to branch between old and new layouts.
   */
  default String recordSignature() {
    return "";
  }

  void beginNested(int id, String name);

  void endNested(int id, String name);

  /** Wire-level safety bounds the collection descriptors check before allocating. */
  default WireConstraints wireConstraints() {
    return WireConstraints.SECURE_DEFAULTS;
  }

  /**
   * Reads the top-level value with the descriptor found for {@code clazz}; throws {@link IllegalStateException} when
   * none is registered.
   */
  default <T> Value<T> read(Class<T> clazz) {
    Objects.requireNonNull(clazz, "clazz must not be null");
    @SuppressWarnings("unchecked")
    var td = (TypeDescriptor<T>) TypeDescriptor.find(clazz).orElseThrow(() ->
      new IllegalStateException("No TypeDescriptor registered for " + clazz.getName())
    );
    return read(td);
  }

  /** Reads the top-level value at slot {@code 0}. */
  default <T> Value<T> read(TypeDescriptor<T> descriptor) {
    return read(0, StateWriter.PAYLOAD_SLOT_NAME, descriptor);
  }

  /**
   * Reads the value at the slot: a state marker becomes the matching {@code Value} state, anything else is read through
   * {@code descriptor} and boxed.
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
        case UNSUPPORTED -> Value.unsupported(readUnsupported(id, name), descriptor);
      };
    }
    return descriptor.readValue(this, id, name);
  }

  /** The state marker at the slot, or {@code null} when a typed payload follows. */
  ValueState peekValueStateOrNull(int id, String name);

  /** Reads the throwable after {@link #peekValueStateOrNull} answered {@link ValueState#ERROR}. */
  Throwable readError(int id, String name);

  /** Reads the reason after {@link #peekValueStateOrNull} answered {@link ValueState#UNSUPPORTED}. */
  String readUnsupported(int id, String name);

  /** Releases resources and verifies the terminator where the mode has one; throws no checked exception. */
  @Override
  void close();
}
