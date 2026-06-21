package dev.zems.lib.value.marshal.descriptor;

import dev.zems.lib.value.marshal.StateReader;
import dev.zems.lib.value.marshal.StateWriter;
import java.util.List;
import java.util.Map;

/**
 * Read/write dispatch hint for a slot in a {@link StructuredTypeDescriptor} slot table.
 *
 * <p>
 * Each slot's {@code kind} tells the structured descriptor which {@link StateReader} / {@link StateWriter} primitive to
 * call: a {@link #STRING} slot dispatches to {@link StateReader#readString(int, String)} /
 * {@link StateWriter#writeString(int, String, String)}; a {@link #RECORD} slot dispatches to
 * {@link StateReader#readRecord(int, String, TypeDescriptor)} /
 * {@link StateWriter#writeRecord(int, String, TypeDescriptor, Object)}; etc.
 *
 * <p>
 * The mapping is determined at synthesis time by inspecting the record component's raw {@link Class} via
 * {@link #of(Class)}.
 */
public enum SlotKind {
  BOOL,
  CHAR,
  BYTE,
  SHORT,
  INT,
  LONG,
  FLOAT,
  DOUBLE,
  STRING,
  BYTES,
  LIST,
  MAP,
  RECORD;

  /**
   * Classifies a raw component {@link Class} into the matching kind. {@link #RECORD} is the catch-all for
   * non-primitive, non-collection types (used for both records and non-record structured types).
   */
  public static SlotKind of(Class<?> raw) {
    if (raw == boolean.class || raw == Boolean.class) {
      return BOOL;
    }
    if (raw == char.class || raw == Character.class) {
      return CHAR;
    }
    if (raw == byte.class || raw == Byte.class) {
      return BYTE;
    }
    if (raw == short.class || raw == Short.class) {
      return SHORT;
    }
    if (raw == int.class || raw == Integer.class) {
      return INT;
    }
    if (raw == long.class || raw == Long.class) {
      return LONG;
    }
    if (raw == float.class || raw == Float.class) {
      return FLOAT;
    }
    if (raw == double.class || raw == Double.class) {
      return DOUBLE;
    }
    if (raw == String.class) {
      return STRING;
    }
    if (raw == byte[].class) {
      return BYTES;
    }
    if (List.class.isAssignableFrom(raw)) {
      return LIST;
    }
    if (Map.class.isAssignableFrom(raw)) {
      return MAP;
    }
    return RECORD;
  }
}
