package dev.zems.lib.value.marshal.descriptor;

import java.lang.invoke.MethodHandle;
import java.util.List;
import java.util.Objects;

/**
 * One slot in a {@link StructuredTypeDescriptor}'s slot table.
 *
 * <p>
 * Carries everything needed to read and write the slot:
 *
 * <ul>
 * <li>{@link #id} — non-negative integer identifying the slot on id-anchored wires (binary).
 * Auto-assigned by builder call order (record synthesis uses the component declaration index);
 * uniqueness within a descriptor is enforced at descriptor build time. Recommended to keep below
 * 24 so the CBOR map key fits in a single initial byte.
 * <li>{@link #name} — human-readable name. Used directly on the JSON wire (object key); never
 * written to binary. Validated via {@link Names#validate}.
 * <li>{@link #aliases} — alternate names accepted on read for JSON only (first-match-wins,
 * primary first). Useful for renames during schema evolution. Aliases follow the same
 * validation rule as primary names.
 * <li>{@link #descriptor} — the slot's {@link TypeDescriptor} (used by RECORD-kind slots and as
 * a structural-signature contributor for all kinds).
 * <li>{@link #defaultOrNull} — canonical record component (and public accessor);
 * {@code null} means "required slot", non-null means "optional with default". Stored as a
 * plain nullable field per the marshal-layer carve-out (see value-lib CLAUDE.md "Optional
 * usage"); callers null-check the accessor at the call site.
 * <li>{@link #accessor} — {@code MethodHandle} of type {@code (T_owner) -> T_slot} that
 * extracts the slot's value from a populated record on the writing side.
 * <li>{@link #kind} — read/write dispatch hint, classified at synthesis time from the raw
 * component {@link Class}.
 * </ul>
 *
 * @param <T> the slot's Java type (the type of the record component this slot represents)
 */
public record SlotSpec<T>(
  int id,
  String name,
  List<String> aliases,
  TypeDescriptor<T> descriptor,
  T defaultOrNull,
  MethodHandle accessor,
  SlotKind kind
) {
  public SlotSpec {
    if (id < 0) {
      throw new IllegalArgumentException("slot id must be non-negative, got " + id);
    }
    Objects.requireNonNull(name, "name must not be null");
    Names.validate(name, "slot");
    Objects.requireNonNull(aliases, "aliases must not be null");
    aliases = List.copyOf(aliases);
    for (String alias : aliases) {
      Names.validate(alias, "alias");
    }
    Objects.requireNonNull(descriptor, "descriptor must not be null");
    Objects.requireNonNull(accessor, "accessor must not be null");
    Objects.requireNonNull(kind, "kind must not be null");
    // defaultOrNull is intentionally nullable: null = required, non-null = optional with default.
  }

  /** True if {@code wireName} matches this slot's primary name or any of its aliases. */
  public boolean matches(String wireName) {
    return name.equals(wireName) || aliases.contains(wireName);
  }
}
