package dev.zems.lib.value.marshal.descriptor;

import java.util.Objects;

/**
 * Bundles read-time evolution policy for a {@link TypeDescriptor}. Currently, carries only the unknown-slot policy;
 * intended to grow with additional knobs (forward-compat handling, migration hooks) without changing the descriptor
 * surface.
 *
 * <p>
 * Two pre-built constants cover both supported modes — strict (reject) and lenient (read what we can). For anything
 * bespoke, construct the record directly.
 */
public record EvolutionPolicy(UnknownSlotPolicy unknownSlots) {
  /** Default: reject any unknown slot on the wire. */
  public static final EvolutionPolicy STRICT = new EvolutionPolicy(UnknownSlotPolicy.FAIL);
  /** Forward-compatible: silently drop unknown slots and read what we can. */
  public static final EvolutionPolicy LENIENT = new EvolutionPolicy(UnknownSlotPolicy.SKIP);

  public EvolutionPolicy {
    Objects.requireNonNull(unknownSlots, "unknownSlots must not be null");
  }
}
