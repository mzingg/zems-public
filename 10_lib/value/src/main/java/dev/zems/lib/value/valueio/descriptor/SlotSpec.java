package dev.zems.lib.value.valueio.descriptor;

import java.lang.invoke.MethodHandle;
import java.util.List;
import java.util.Objects;

/**
 * One slot of a {@link StructuredTypeDescriptor}. The id is the binary wire identity and must be unique within a
 * descriptor (ids below 24 encode as a single CBOR byte); the name and aliases are JSON-only, primary name first. A
 * {@code null} {@code defaultOrNull} marks the slot as required; the accessor maps the owning record to the slot value.
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
  }

  /** True when {@code wireName} is this slot's primary name or one of its aliases. */
  public boolean matches(String wireName) {
    return name.equals(wireName) || aliases.contains(wireName);
  }
}
