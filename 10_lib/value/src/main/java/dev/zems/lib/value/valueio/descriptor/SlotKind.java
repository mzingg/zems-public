package dev.zems.lib.value.valueio.descriptor;

import dev.zems.lib.value.valueio.StateReader;
import dev.zems.lib.value.valueio.StateWriter;
import java.util.List;
import java.util.Map;

/**
 * The {@link StateReader} and {@link StateWriter} primitive a {@link StructuredTypeDescriptor} slot dispatches to,
 * classified from the component's raw class by {@link #of(Class)}.
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

  /** Classifies a raw class; {@link #RECORD} is the catch-all for non-primitive, non-collection types. */
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
