package dev.zems.lib.value.valueio.descriptor;

import dev.zems.lib.value.valueio.StateReader;
import dev.zems.lib.value.valueio.StateWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Builder for a wired {@link ScalarTypeDescriptor} of a non-record type, obtained from
 * {@link TypeDescriptor#builder(Class)}. The name defaults to the class name; {@link #build()} needs both a reader and
 * a writer.
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

  public TypeDescriptorBuilder<T> withName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be null or blank");
    }
    this.descriptorName = name;
    return this;
  }

  /** Alternate descriptor names accepted on read; replaces any previously declared list. */
  public TypeDescriptorBuilder<T> withAliases(String... aliases) {
    Objects.requireNonNull(aliases, "aliases must not be null");
    nameAliases.clear();
    nameAliases.addAll(Arrays.asList(aliases));
    return this;
  }

  public TypeDescriptorBuilder<T> withEvolutionPolicy(EvolutionPolicy policy) {
    this.evolutionPolicy = Objects.requireNonNull(policy, "policy must not be null");
    return this;
  }

  public TypeDescriptorBuilder<T> reader(Function<StateReader, T> reader) {
    this.reader = Objects.requireNonNull(reader, "reader must not be null");
    return this;
  }

  public TypeDescriptorBuilder<T> writer(BiConsumer<StateWriter, T> writer) {
    this.writer = Objects.requireNonNull(writer, "writer must not be null");
    return this;
  }

  /** Builds the descriptor; throws {@link IllegalStateException} when the reader or the writer is missing. */
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
