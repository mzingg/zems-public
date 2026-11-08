package dev.zems.lib.value.valueio.descriptor;

import dev.zems.lib.value.CoreValue;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.valueio.StateReader;
import dev.zems.lib.value.valueio.StateWriter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Describes a scalar (non-collection) Java type. May be wired with read/write logic or carry only type metadata (in
 * which case {@link #read}/{@link #write} throw). A descriptor with a tolerant text parse, such as
 * {@link BuiltinTypeDescriptors#QUANTITY}, answers an {@code ErrorValue} for bad text wherever a read yields a
 * {@link Value} (a top-level read, a list or set element, a map value) and throws in raw positions.
 */
public final class ScalarTypeDescriptor<T> implements TypeDescriptor<T> {

  private static final int TEXT_SLOT_ID = 0;
  private static final String TEXT_SLOT_NAME = "value";

  private final String descriptorName;
  private final Class<T> clazz;
  private final Function<StateReader, T> reader;
  private final BiConsumer<StateWriter, T> writer;
  private final List<String> nameAliases;
  private final EvolutionPolicy evolutionPolicy;
  private final TolerantText<T> tolerantText;

  private ScalarTypeDescriptor(
    String descriptorName,
    Class<T> clazz,
    Function<StateReader, T> reader,
    BiConsumer<StateWriter, T> writer
  ) {
    this(descriptorName, clazz, reader, writer, List.of(), EvolutionPolicy.STRICT, null);
  }

  private ScalarTypeDescriptor(
    String descriptorName,
    Class<T> clazz,
    Function<StateReader, T> reader,
    BiConsumer<StateWriter, T> writer,
    List<String> nameAliases,
    EvolutionPolicy evolutionPolicy,
    Function<String, Value<T>> tolerantParse
  ) {
    this.descriptorName = descriptorName;
    this.clazz = clazz;
    this.reader = reader;
    this.writer = writer;
    this.nameAliases = List.copyOf(nameAliases);
    this.evolutionPolicy = evolutionPolicy;
    // A string twin under the same descriptor name reads the record exactly as read does, so the checksum and type
    // verification stay in step.
    this.tolerantText =
      tolerantParse == null
        ? null
        : new TolerantText<>(textTwin(descriptorName, this.nameAliases, evolutionPolicy), tolerantParse);
  }

  private record TolerantText<T>(TypeDescriptor<String> wireText, Function<String, Value<T>> parse) {}

  private static TypeDescriptor<String> textTwin(
    String descriptorName,
    List<String> nameAliases,
    EvolutionPolicy evolutionPolicy
  ) {
    return new ScalarTypeDescriptor<>(
      descriptorName,
      String.class,
      r -> r.readString(TEXT_SLOT_ID, TEXT_SLOT_NAME),
      (w, v) -> w.writeString(TEXT_SLOT_ID, TEXT_SLOT_NAME, v),
      nameAliases,
      evolutionPolicy,
      null
    );
  }

  static <T> ScalarTypeDescriptor<T> of(String descriptorName, Class<T> clazz) {
    Class<T> validated = validateAndReturnBoxedWhenNeeded(descriptorName, clazz);
    var builtIn = BuiltinTypeDescriptors.find(validated);
    if (builtIn.isPresent() && builtIn.get() instanceof ScalarTypeDescriptor<?> scalar) {
      @SuppressWarnings("unchecked") // builtIn was looked up by `validated` (Class<T>), so its read/write are <T>
      var typedReader = (Function<StateReader, T>) scalar.reader;
      @SuppressWarnings("unchecked")
      var typedWriter = (BiConsumer<StateWriter, T>) scalar.writer;
      @SuppressWarnings("unchecked")
      var typedParse = (Function<String, Value<T>>) (Function<String, ?>) scalar.tolerantParse();
      return new ScalarTypeDescriptor<>(
        descriptorName,
        validated,
        typedReader,
        typedWriter,
        List.of(),
        EvolutionPolicy.STRICT,
        typedParse
      );
    }
    return new ScalarTypeDescriptor<>(descriptorName, validated, null, null);
  }

  static <T> ScalarTypeDescriptor<T> of(
    String descriptorName,
    Class<T> clazz,
    Function<StateReader, T> reader,
    BiConsumer<StateWriter, T> writer
  ) {
    Class<T> validated = validateAndReturnBoxedWhenNeeded(descriptorName, clazz);
    Objects.requireNonNull(reader, "reader must not be null");
    Objects.requireNonNull(writer, "writer must not be null");
    return new ScalarTypeDescriptor<>(descriptorName, validated, reader, writer);
  }

  static <T> ScalarTypeDescriptor<T> ofTolerantText(
    Class<T> clazz,
    Function<String, T> parser,
    Function<String, Value<T>> tolerantParse
  ) {
    Class<T> validated = validateAndReturnBoxedWhenNeeded(clazz.getName(), clazz);
    return new ScalarTypeDescriptor<>(
      validated.getName(),
      validated,
      r -> parser.apply(r.readString(TEXT_SLOT_ID, TEXT_SLOT_NAME)),
      (w, v) -> w.writeString(TEXT_SLOT_ID, TEXT_SLOT_NAME, v.toString()),
      List.of(),
      EvolutionPolicy.STRICT,
      Objects.requireNonNull(tolerantParse, "tolerantParse must not be null")
    );
  }

  private static <T> Class<T> validateAndReturnBoxedWhenNeeded(String descriptorName, Class<T> clazz) {
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
  public Value<T> readValue(StateReader stateReader, int id, String name) {
    if (tolerantText == null) {
      return TypeDescriptor.super.readValue(stateReader, id, name);
    }
    return tolerantText.parse().apply(stateReader.readRecord(id, name, tolerantText.wireText()));
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

  @Override
  public ScalarTypeDescriptor<T> withAliases(String... aliases) {
    return new ScalarTypeDescriptor<>(
      descriptorName,
      clazz,
      reader,
      writer,
      List.of(aliases),
      evolutionPolicy,
      tolerantParse()
    );
  }

  @Override
  public ScalarTypeDescriptor<T> withEvolutionPolicy(EvolutionPolicy policy) {
    return new ScalarTypeDescriptor<>(
      descriptorName,
      clazz,
      reader,
      writer,
      nameAliases,
      Objects.requireNonNull(policy, "policy must not be null"),
      tolerantParse()
    );
  }

  private Function<String, Value<T>> tolerantParse() {
    return tolerantText == null ? null : tolerantText.parse();
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
    return "ScalarTypeDescriptor[" + descriptorName + " (" + clazz.getName() + ")]";
  }
}
