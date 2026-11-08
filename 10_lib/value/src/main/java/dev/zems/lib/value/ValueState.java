package dev.zems.lib.value;

/**
 * Wire discriminator for the six state markers in the {@link Value} hierarchy. A new constant may only be appended:
 * the checksum feeds {@code ordinal() + 1}, so any other position changes the checksum byte of an existing state.
 */
public enum ValueState {
  NULL,
  UNDEFINED,
  UNRESOLVED,
  ERROR,
  TOMBSTONE,
  UNSUPPORTED,
}
