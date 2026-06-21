package dev.zems.lib.value.marshal.descriptor;

import dev.zems.lib.value.marshal.StateReader;
import dev.zems.lib.value.marshal.StateWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Fluent builder for non-record types that don't fit the auto-synthesised slot-table model. Authors supply the
 * descriptor name (defaults to the class name), optional aliases and evolution policy, and the read/write lambdas, then
 * call {@link #build()} to produce a fully-wired {@link ScalarTypeDescriptor}.
 *
 * <p>
 * Obtained from {@link TypeDescriptor#builder(Class)}. For records, prefer {@link TypeDescriptor#of(Class, Object...)}
 * which auto-synthesises from the canonical constructor + record components.
 *
 * <pre>{@code
 * TypeDescriptor<Order> d = TypeDescriptor.builder(Order.class).withName("Order").withAliases("LegacyOrder")
 *     .reader(r -> new Order(r.readString("customer"), r.readInt("amount"))).writer((w, o) -> {
 *       w.writeString("customer", o.customer());
 *       w.writeInt("amount", o.amount());
 *     }).build();
 * }</pre>
 *
 * @param <T> the type the descriptor describes
 */
public final class TypeDescriptorBuilder<T> {

  private final Class<T> clazz;
  private final List<String> nameAliases = new ArrayList<>();
  private String descriptorName;
  private EvolutionPolicy evolutionPolicy = EvolutionPolicy.STRICT;
  private Function<StateReader, T> reader;
  private BiConsumer<StateWriter, T> writer;

  TypeDescriptorBuilder(Class<T> clazz) {
    this.clazz = Objects.requireNonNull(clazz, "clazz must not be null");
    this.descriptorName = clazz.getName();
  }

  /** Override the descriptor name (defaults to {@code clazz.getName()}). */
  public TypeDescriptorBuilder<T> withName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be null or blank");
    }
    this.descriptorName = name;
    return this;
  }

  /** Declare alternate descriptor names accepted on read (replaces any previous list). */
  public TypeDescriptorBuilder<T> withAliases(String... aliases) {
    Objects.requireNonNull(aliases, "aliases must not be null");
    nameAliases.clear();
    nameAliases.addAll(Arrays.asList(aliases));
    return this;
  }

  /** Set the read-time evolution policy (default {@link EvolutionPolicy#STRICT}). */
  public TypeDescriptorBuilder<T> withEvolutionPolicy(EvolutionPolicy policy) {
    this.evolutionPolicy = Objects.requireNonNull(policy, "policy must not be null");
    return this;
  }

  /** Read lambda — required. */
  public TypeDescriptorBuilder<T> reader(Function<StateReader, T> reader) {
    this.reader = Objects.requireNonNull(reader, "reader must not be null");
    return this;
  }

  /** Write lambda — required. */
  public TypeDescriptorBuilder<T> writer(BiConsumer<StateWriter, T> writer) {
    this.writer = Objects.requireNonNull(writer, "writer must not be null");
    return this;
  }

  /**
   * Materialises a {@link ScalarTypeDescriptor} from the configured state. Throws if the read or write lambda is
   * missing.
   */
  public ScalarTypeDescriptor<T> build() {
    if (reader == null) {
      throw new IllegalStateException("reader is required — call .reader(...) before build()");
    }
    if (writer == null) {
      throw new IllegalStateException("writer is required — call .writer(...) before build()");
    }
    var descriptor = ScalarTypeDescriptor.of(descriptorName, clazz, reader, writer);
    if (!nameAliases.isEmpty()) {
      descriptor = descriptor.withAliases(nameAliases.toArray(String[]::new));
    }
    if (evolutionPolicy != EvolutionPolicy.STRICT) {
      descriptor = descriptor.withEvolutionPolicy(evolutionPolicy);
    }
    return descriptor;
  }
}
