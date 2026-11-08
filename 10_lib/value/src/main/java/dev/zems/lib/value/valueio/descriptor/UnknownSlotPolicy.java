package dev.zems.lib.value.valueio.descriptor;

/** Policy for slots present on the wire that the descriptor does not consume; applied via {@link EvolutionPolicy}. */
public enum UnknownSlotPolicy {
  /**
   * The default: throw {@link IllegalStateException} listing the unknown slots once the record has been read. A record
   * read out of order reports the same unknown slots as one read in field order.
   */
  FAIL,

  /** Drop unknown slots and read what is known. */
  SKIP,
}
