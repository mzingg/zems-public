package dev.zems.lib.value.marshal.descriptor;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Mutable per-slot override accumulator handed to a {@link StructuredTypeDescriptor#withSlot} lambda. Authors call the
 * chain methods to declare aliases, defaults, or replace the inferred descriptor for a single slot:
 *
 * <pre>{@code
 * TypeDescriptor.of(Person.class).withSlot("age", s -> s.aliases("years", "yearsOld").defaultValue(0)).withSlot("email", s -> s.defaultValue("n/a"))
 *     .withSlot("name", s -> s.descriptor(customNameDescriptor));
 * }</pre>
 *
 * <p>
 * Returned from the {@code withSlot} lambda; consumed by {@code StructuredTypeDescriptor} to produce a new
 * {@link SlotSpec} replacing the original.
 *
 * @param <T> the slot's Java type
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

  /** Declare alternate on-wire names accepted on read. First match wins (primary, then aliases). */
  public SlotConfigurer<T> aliases(String... aliases) {
    Objects.requireNonNull(aliases, "aliases must not be null");
    this.aliases = List.copyOf(Arrays.asList(aliases));
    return this;
  }

  /**
   * Mark the slot optional with the given value used when no candidate name matches on read. Pass {@code null} to
   * revert to required.
   */
  public SlotConfigurer<T> defaultValue(T defaultValue) {
    this.defaultValue = defaultValue;
    return this;
  }

  /** Replace the inferred descriptor with a custom one (e.g. to swap in a domain wrapper). */
  public SlotConfigurer<T> descriptor(TypeDescriptor<T> descriptor) {
    this.descriptor = Objects.requireNonNull(descriptor, "descriptor must not be null");
    return this;
  }

  /** Package-private materializer — produces a new {@link SlotSpec} from {@code source} + overrides. */
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
