package dev.zems.lib.value.valueio.descriptor;

import java.util.Objects;

/** Read-time evolution policy of a {@link TypeDescriptor}. */
public record EvolutionPolicy(UnknownSlotPolicy unknownSlots, UnknownConstantPolicy unknownConstants) {
  /** The default: reject any unknown slot and any unknown enum constant on the wire. */
  public static final EvolutionPolicy STRICT = new EvolutionPolicy(UnknownSlotPolicy.FAIL, UnknownConstantPolicy.FAIL);
  /**
   * Forward-compatible for slots only: drop unknown slots and read what is known. An unknown enum constant still fails.
   */
  public static final EvolutionPolicy LENIENT = new EvolutionPolicy(UnknownSlotPolicy.SKIP, UnknownConstantPolicy.FAIL);

  public EvolutionPolicy {
    Objects.requireNonNull(unknownSlots, "unknownSlots must not be null");
    Objects.requireNonNull(unknownConstants, "unknownConstants must not be null");
  }

  /** Unknown constants fail. */
  public EvolutionPolicy(UnknownSlotPolicy unknownSlots) {
    this(unknownSlots, UnknownConstantPolicy.FAIL);
  }
}
