package dev.zems.lib.value;

/**
 * Discriminator for the explicit-state values in the {@link Value} hierarchy.
 *
 * <p>
 * Used by {@link dev.zems.lib.value.marshal.StateWriter StateWriter} and
 * {@link dev.zems.lib.value.marshal.StateReader StateReader} to round-trip state values on the wire — readers peek for
 * one of these markers before falling back to a typed-payload read.
 */
public enum ValueState {
  NULL,
  UNDEFINED,
  UNRESOLVED,
  ERROR,
  TOMBSTONE,
}
