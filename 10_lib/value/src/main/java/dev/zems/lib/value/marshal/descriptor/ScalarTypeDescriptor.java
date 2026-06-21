package dev.zems.lib.value.marshal.descriptor;

import dev.zems.lib.value.CoreValue;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.StateReader;
import dev.zems.lib.value.marshal.StateWriter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Describes a scalar (non-collection) Java type. May be wired with read/write logic or carry only type metadata (in
 * which case {@link #read}/{@link #write} throw).
 */
public final class ScalarTypeDescriptor<T> implements TypeDescriptor<T> {

  private final String descriptorName;
  private final Class<T> clazz;
  private final Function<StateReader, T> reader;
  private final BiConsumer<StateWriter, T> writer;
  private final List<String> nameAliases;
  private final EvolutionPolicy evolutionPolicy;

  private ScalarTypeDescriptor(
    String descriptorName,
    Class<T> clazz,
    Function<StateReader, T> reader,
    BiConsumer<StateWriter, T> writer
  ) {
    this(descriptorName, clazz, reader, writer, List.of(), EvolutionPolicy.STRICT);
  }

  private ScalarTypeDescriptor(
    String descriptorName,
    Class<T> clazz,
    Function<StateReader, T> reader,
    BiConsumer<StateWriter, T> writer,
    List<String> nameAliases,
    EvolutionPolicy evolutionPolicy
  ) {
    this.descriptorName = descriptorName;
    this.clazz = clazz;
    this.reader = reader;
    this.writer = writer;
    this.nameAliases = List.copyOf(nameAliases);
    this.evolutionPolicy = evolutionPolicy;
  }

  /**
   * Metadata-only factory. If {@code clazz} matches a {@link BuiltinTypeDescriptors} entry, the built-in's read/write
   * logic is adopted (with {@code descriptorName} taking precedence).
   */
  static <T> ScalarTypeDescriptor<T> of(String descriptorName, Class<T> clazz) {
    Class<T> validated = validate(descriptorName, clazz);
    var builtIn = BuiltinTypeDescriptors.find(validated);
    if (builtIn.isPresent() && builtIn.get() instanceof ScalarTypeDescriptor<?> scalar) {
      @SuppressWarnings("unchecked") // builtIn was looked up by `validated` (Class<T>), so its read/write are <T>
      var typedReader = (Function<StateReader, T>) scalar.reader;
      @SuppressWarnings("unchecked")
      var typedWriter = (BiConsumer<StateWriter, T>) scalar.writer;
      return new ScalarTypeDescriptor<>(descriptorName, validated, typedReader, typedWriter);
    }
    return new ScalarTypeDescriptor<>(descriptorName, validated, null, null);
  }

  /** Wired scalar factory. */
  static <T> ScalarTypeDescriptor<T> of(
    String descriptorName,
    Class<T> clazz,
    Function<StateReader, T> reader,
    BiConsumer<StateWriter, T> writer
  ) {
    Class<T> validated = validate(descriptorName, clazz);
    Objects.requireNonNull(reader, "reader must not be null");
    Objects.requireNonNull(writer, "writer must not be null");
    return new ScalarTypeDescriptor<>(descriptorName, validated, reader, writer);
  }

  private static <T> Class<T> validate(String descriptorName, Class<T> clazz) {
    if (descriptorName == null || descriptorName.isBlank()) {
      throw new IllegalArgumentException("descriptorName must not be blank");
    }
    if (clazz == null) {
      throw new IllegalArgumentException("Class must not be null");
    }
    if (Value.class.isAssignableFrom(clazz) && !CoreValue.class.isAssignableFrom(clazz)) {
      throw new IllegalArgumentException("Cannot describe Value types - use the Value directly");
    }
    if (clazz.isArray()) {
      throw new IllegalArgumentException("Cannot describe array types - use List instead");
    }
    if (clazz == List.class) {
      throw new IllegalArgumentException("Cannot describe raw List - use TypeDescriptor.ofList() instead");
    }
    if (clazz == Map.class) {
      throw new IllegalArgumentException("Cannot describe raw Map - use TypeDescriptor.ofMap() instead");
    }
    return clazz.isPrimitive() ? boxed(clazz) : clazz;
  }

  @SuppressWarnings("unchecked")
  private static <T> Class<T> boxed(Class<T> primitive) {
    if (primitive == int.class) {
      return (Class<T>) Integer.class;
    }
    if (primitive == long.class) {
      return (Class<T>) Long.class;
    }
    if (primitive == double.class) {
      return (Class<T>) Double.class;
    }
    if (primitive == float.class) {
      return (Class<T>) Float.class;
    }
    if (primitive == boolean.class) {
      return (Class<T>) Boolean.class;
    }
    if (primitive == short.class) {
      return (Class<T>) Short.class;
    }
    if (primitive == char.class) {
      return (Class<T>) Character.class;
    }
    if (primitive == byte.class) {
      return (Class<T>) Byte.class;
    }
    throw new IllegalArgumentException("Cannot describe void type");
  }

  /** Returns the described class. */
  public Class<T> describedClass() {
    return clazz;
  }

  @Override
  public String qualifiedName() {
    return clazz.getName();
  }

  @Override
  public T read(StateReader stateReader) {
    if (reader == null) {
      throw new IllegalStateException("No marshalling logic registered for " + descriptorName);
    }
    return reader.apply(stateReader);
  }

  @Override
  public void write(StateWriter stateWriter, T value) {
    if (writer == null) {
      throw new IllegalStateException("No marshalling logic registered for " + descriptorName);
    }
    writer.accept(stateWriter, value);
  }

  @Override
  public String descriptorName() {
    return descriptorName;
  }

  @Override
  public List<String> nameAliases() {
    return nameAliases;
  }

  @Override
  public EvolutionPolicy evolutionPolicy() {
    return evolutionPolicy;
  }

  /**
   * Returns a new descriptor with the given name aliases (read-side; descriptors with these names on the wire are
   * accepted as compatible).
   */
  @Override
  public ScalarTypeDescriptor<T> withAliases(String... aliases) {
    return new ScalarTypeDescriptor<>(descriptorName, clazz, reader, writer, List.of(aliases), evolutionPolicy);
  }

  /** Returns a new descriptor with the given evolution policy. */
  @Override
  public ScalarTypeDescriptor<T> withEvolutionPolicy(EvolutionPolicy policy) {
    return new ScalarTypeDescriptor<>(
      descriptorName,
      clazz,
      reader,
      writer,
      nameAliases,
      Objects.requireNonNull(policy, "policy must not be null")
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(descriptorName, clazz);
  }

  @Override
  public boolean equals(Object o) {
    return (
      o instanceof ScalarTypeDescriptor<?> that &&
      clazz.equals(that.clazz) &&
      descriptorName.equals(that.descriptorName)
    );
  }

  @Override
  public String toString() {
    return ("ScalarTypeDescriptor[" + descriptorName + " (" + clazz.getName() + ")]");
  }
}
