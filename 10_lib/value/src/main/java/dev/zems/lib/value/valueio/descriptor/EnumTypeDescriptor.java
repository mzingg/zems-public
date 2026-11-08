package dev.zems.lib.value.valueio.descriptor;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.valueio.StateReader;
import dev.zems.lib.value.valueio.StateWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Synthesised descriptor of an enum: the wire form is the constant's {@link Enum#name()} in the scalar one-slot shape
 * (slot {@code 0}, name {@code "value"}), never the ordinal, so reordering constants keeps old payloads readable.
 * {@link EnumAlias} names are accepted on read. An unknown wire constant throws {@link IllegalStateException}; under
 * {@link UnknownConstantPolicy#ERROR_VALUE} a read that yields a {@link Value} answers an {@code ErrorValue} instead.
 * Equality is on the descriptor name and the enum class; aliases and the policy do not affect it.
 */
public final class EnumTypeDescriptor<E extends Enum<E>> implements TypeDescriptor<E> {

  private static final int SLOT_ID = 0;
  private static final String SLOT_NAME = "value";

  private final String descriptorName;
  private final Class<E> enumClass;
  private final Map<String, E> constantsByWireName;
  private final List<String> nameAliases;
  private final EvolutionPolicy evolutionPolicy;
  // Reads the record exactly as read() does, so the tolerant path keeps the checksum and type verification in step.
  private final TypeDescriptor<String> wireName;

  private EnumTypeDescriptor(
    String descriptorName,
    Class<E> enumClass,
    Map<String, E> constantsByWireName,
    List<String> nameAliases,
    EvolutionPolicy evolutionPolicy
  ) {
    this.descriptorName = descriptorName;
    this.enumClass = enumClass;
    this.constantsByWireName = constantsByWireName;
    this.nameAliases = List.copyOf(nameAliases);
    this.evolutionPolicy = evolutionPolicy;
    this.wireName = TypeDescriptor.of(
      descriptorName,
      String.class,
      r -> r.readString(SLOT_ID, SLOT_NAME),
      (w, v) -> w.writeString(SLOT_ID, SLOT_NAME, v)
    )
      .withAliases(this.nameAliases.toArray(String[]::new))
      .withEvolutionPolicy(evolutionPolicy);
  }

  private static <E extends Enum<E>> EnumTypeDescriptor<E> synthesize(Class<E> enumClass) {
    E[] constants = enumClass.getEnumConstants();
    var byWireName = new HashMap<String, E>();
    for (E constant : constants) {
      byWireName.put(constant.name(), constant);
    }
    for (E constant : constants) {
      for (String alias : aliasesOf(enumClass, constant)) {
        if (alias.isBlank()) {
          throw new IllegalStateException("Blank @EnumAlias on " + enumClass.getName() + "." + constant.name());
        }
        E prior = byWireName.putIfAbsent(alias, constant);
        if (prior != null && prior != constant) {
          E first = prior.ordinal() < constant.ordinal() ? prior : constant;
          E second = first == prior ? constant : prior;
          throw new IllegalStateException(
            "Ambiguous wire name '" +
              alias +
              "' in enum " +
              enumClass.getName() +
              ": " +
              first.name() +
              " and " +
              second.name()
          );
        }
      }
    }
    return new EnumTypeDescriptor<>(
      enumClass.getName(),
      enumClass,
      Map.copyOf(byWireName),
      List.of(),
      EvolutionPolicy.STRICT
    );
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  static EnumTypeDescriptor<?> synthesizeRaw(Class<?> enumClass) {
    return synthesize((Class) enumClass);
  }

  private static String[] aliasesOf(Class<?> enumClass, Enum<?> constant) {
    try {
      // Enum constants are always public fields; reading an annotation needs no access check.
      EnumAlias alias = enumClass.getField(constant.name()).getAnnotation(EnumAlias.class);
      return alias == null ? new String[0] : alias.value();
    } catch (NoSuchFieldException e) {
      throw new IllegalStateException(
        "Enum constant field not found: " + enumClass.getName() + "." + constant.name(),
        e
      );
    }
  }

  public Class<E> describedClass() {
    return enumClass;
  }

  @Override
  public String qualifiedName() {
    return enumClass.getName();
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

  /** Reads a constant in a raw position; an unknown wire constant always throws, whatever the policy. */
  @Override
  public E read(StateReader reader) {
    String wire = reader.readString(SLOT_ID, SLOT_NAME);
    E constant = constantsByWireName.get(wire);
    if (constant == null) {
      throw new IllegalStateException(unknownConstantMessage(wire));
    }
    return constant;
  }

  @Override
  public void write(StateWriter writer, E value) {
    writer.writeString(SLOT_ID, SLOT_NAME, value.name());
  }

  /**
   * Reads the record first, then resolves the constant: an unknown one throws, or under
   * {@link UnknownConstantPolicy#ERROR_VALUE} answers an {@code ErrorValue} with the same message.
   */
  @Override
  public Value<E> readValue(StateReader reader, int id, String name) {
    String wire = reader.readRecord(id, name, wireName);
    E constant = constantsByWireName.get(wire);
    if (constant != null) {
      return Value.of(constant);
    }
    if (evolutionPolicy.unknownConstants() == UnknownConstantPolicy.ERROR_VALUE) {
      return Value.errorMessage(unknownConstantMessage(wire), this);
    }
    throw new IllegalStateException(unknownConstantMessage(wire));
  }

  private String unknownConstantMessage(String wire) {
    return "Unknown constant '" + wire + "' for enum " + enumClass.getName();
  }

  @Override
  public EnumTypeDescriptor<E> withAliases(String... aliases) {
    return new EnumTypeDescriptor<>(descriptorName, enumClass, constantsByWireName, List.of(aliases), evolutionPolicy);
  }

  @Override
  public EnumTypeDescriptor<E> withEvolutionPolicy(EvolutionPolicy policy) {
    return new EnumTypeDescriptor<>(
      descriptorName,
      enumClass,
      constantsByWireName,
      nameAliases,
      Objects.requireNonNull(policy, "policy must not be null")
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(descriptorName, enumClass);
  }

  @Override
  public boolean equals(Object o) {
    return (
      o instanceof EnumTypeDescriptor<?> that &&
      enumClass.equals(that.enumClass) &&
      descriptorName.equals(that.descriptorName)
    );
  }

  @Override
  public String toString() {
    return "EnumTypeDescriptor[" + descriptorName + " (" + enumClass.getName() + ")]";
  }
}
