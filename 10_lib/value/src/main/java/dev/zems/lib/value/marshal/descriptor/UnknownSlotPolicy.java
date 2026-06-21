package dev.zems.lib.value.marshal.descriptor;

import dev.zems.lib.value.marshal.AbstractStateReader;

/**
 * Policy for slots present on the wire that the descriptor does not consume.
 *
 * <p>
 * Applied per-descriptor via {@link EvolutionPolicy}. Enforced in {@link AbstractStateReader#readRecord} after the
 * descriptor's read lambda returns.
 *
 * @see EvolutionPolicy
 */
public enum UnknownSlotPolicy {
  /**
   * Throw {@link IllegalStateException} listing the unknown slot names. The default.
   *
   * <p>
   * Detection is read-order-independent on both readers: the JSON reader tracks which slots a descriptor consumed
   * (typed reads, record/nested opens, and peek-resolved state markers), so a record read out of order reports the
   * same unknown slots as one read in field order.
   */
  FAIL,

  /** Silently drop unknown slots. Use for descriptors that read forward-compatible payloads. */
  SKIP,
}
