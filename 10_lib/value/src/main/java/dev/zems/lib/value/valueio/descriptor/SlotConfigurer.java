package dev.zems.lib.value.valueio.descriptor;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Per-slot overrides applied inside a {@link StructuredTypeDescriptor#withSlot} lambda, for example
 * {@code descriptor.withSlot("age", s -> s.aliases("years").defaultValue(0))}.
 */
public final class SlotConfigurer<T> {

  private List<String> aliases;
  private T defaultValue;
  private TypeDescriptor<T> descriptor;

  SlotConfigurer(SlotSpec<T> source) {
    this.aliases = source.aliases();
    this.defaultValue = source.defaultOrNull();
    this.descriptor = source.descriptor();
  }

  /** Alternate wire names accepted on read; the primary name is tried first, then the aliases in order. */
  public SlotConfigurer<T> aliases(String... aliases) {
    Objects.requireNonNull(aliases, "aliases must not be null");
    this.aliases = List.copyOf(Arrays.asList(aliases));
    return this;
  }

  /** Makes the slot optional, read as {@code defaultValue} when no name matches; {@code null} makes it required. */
  public SlotConfigurer<T> defaultValue(T defaultValue) {
    this.defaultValue = defaultValue;
    return this;
  }

  public SlotConfigurer<T> descriptor(TypeDescriptor<T> descriptor) {
    this.descriptor = Objects.requireNonNull(descriptor, "descriptor must not be null");
    return this;
  }

  SlotSpec<T> applyTo(SlotSpec<T> source) {
    return new SlotSpec<>(
      source.id(),
      source.name(),
      aliases,
      descriptor,
      defaultValue,
      source.accessor(),
      source.kind()
    );
  }
}
