package dev.zems.lib.value.valueio.descriptor;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.valueio.StateReader;
import dev.zems.lib.value.valueio.StateWriter;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.UnaryOperator;

/**
 * Slot-table-driven descriptor for record-shaped types: each slot is dispatched by its {@link SlotKind} and the results
 * feed the canonical {@code constructor}. Aliases, defaults and the unknown-slot policy operate on the slot table, and
 * {@link #signature()} is derived from it. Slot ids must be unique within the descriptor.
 */
public record StructuredTypeDescriptor<T>(
  String descriptorName,
  List<String> nameAliases,
  EvolutionPolicy evolutionPolicy,
  Class<T> describedClass,
  List<SlotSpec<?>> slots,
  MethodHandle constructor
) implements TypeDescriptor<T> {
  public StructuredTypeDescriptor {
    Objects.requireNonNull(descriptorName, "descriptorName must not be null");
    if (descriptorName.isBlank()) {
      throw new IllegalArgumentException("descriptorName must not be blank");
    }
    Objects.requireNonNull(nameAliases, "nameAliases must not be null");
    nameAliases = List.copyOf(nameAliases);
    Objects.requireNonNull(evolutionPolicy, "evolutionPolicy must not be null");
    Objects.requireNonNull(describedClass, "describedClass must not be null");
    Objects.requireNonNull(slots, "slots must not be null");
    slots = List.copyOf(slots);
    Objects.requireNonNull(constructor, "constructor must not be null");
    requireUniqueSlotIds(slots);
  }

  private static void requireUniqueSlotIds(List<SlotSpec<?>> slots) {
    var seen = new HashMap<Integer, String>(slots.size());
    for (var slot : slots) {
      var prior = seen.put(slot.id(), slot.name());
      if (prior != null) {
        throw new IllegalArgumentException(
          "Duplicate slot id " + slot.id() + ": '" + prior + "' and '" + slot.name() + "'"
        );
      }
    }
  }

  private static void writeSlot(StateWriter w, SlotSpec<?> slot, Object value) {
    int id = slot.id();
    String name = slot.name();
    switch (slot.kind()) {
      case BOOL -> w.writeBoolean(id, name, (Boolean) value);
      case CHAR -> w.writeChar(id, name, (Character) value);
      case BYTE -> w.writeBytes(id, name, new byte[] { (Byte) value });
      case SHORT -> w.writeShort(id, name, (Short) value);
      case INT -> w.writeInt(id, name, (Integer) value);
      case LONG -> w.writeLong(id, name, (Long) value);
      case FLOAT -> w.writeFloat(id, name, (Float) value);
      case DOUBLE -> w.writeDouble(id, name, (Double) value);
      case STRING -> w.writeString(id, name, (String) value);
      case BYTES -> w.writeBytes(id, name, (byte[]) value);
      case LIST, MAP, RECORD -> writeRecordSlot(w, id, name, slot, value);
    }
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private static void writeRecordSlot(StateWriter w, int id, String name, SlotSpec<?> slot, Object value) {
    TypeDescriptor descriptor = slot.descriptor();
    w.writeRecord(id, name, descriptor, boxCollection(descriptor, value));
  }

  @SuppressWarnings("unchecked")
  private static Object readSlot(StateReader r, SlotSpec<?> slot) {
    int id = slot.id();
    String resolved = resolveName(r, id, slot);
    if (resolved == null) {
      Object def = slot.defaultOrNull();
      if (def != null) {
        return def;
      }
      throw new IllegalStateException(
        "required slot '" +
          slot.name() +
          "'" +
          (slot.aliases().isEmpty() ? "" : " (aliases: " + String.join(", ", slot.aliases()) + ")") +
          " not found"
      );
    }
    return switch (slot.kind()) {
      case BOOL -> r.readBoolean(id, resolved);
      case CHAR -> r.readChar(id, resolved);
      case BYTE -> readByteRequired(r, id, resolved);
      case SHORT -> r.readShort(id, resolved);
      case INT -> r.readInt(id, resolved);
      case LONG -> r.readLong(id, resolved);
      case FLOAT -> r.readFloat(id, resolved);
      case DOUBLE -> r.readDouble(id, resolved);
      case STRING -> r.readString(id, resolved);
      case BYTES -> r.readBytes(id, resolved);
      case LIST, MAP, RECORD -> unboxCollection(slot.descriptor(), r.readRecord(id, resolved, slot.descriptor()));
    };
  }

  private static Object boxCollection(TypeDescriptor<?> descriptor, Object raw) {
    return switch (descriptor) {
      case ListTypeDescriptor<?> _ -> boxElements((Collection<?>) raw, new ArrayList<>());
      case SetTypeDescriptor<?> _ -> boxElements((Collection<?>) raw, new LinkedHashSet<>());
      case SortedMapTypeDescriptor<?, ?> _ -> boxEntries((Map<?, ?>) raw, new TreeMap<>());
      case MapTypeDescriptor<?, ?> _ -> boxEntries((Map<?, ?>) raw, new LinkedHashMap<>());
      default -> raw;
    };
  }

  private static Object unboxCollection(TypeDescriptor<?> descriptor, Object boxed) {
    return switch (descriptor) {
      case ListTypeDescriptor<?> _ -> unboxElements((Collection<?>) boxed, new ArrayList<>());
      case SetTypeDescriptor<?> _ -> unboxElements((Collection<?>) boxed, new LinkedHashSet<>());
      case SortedMapTypeDescriptor<?, ?> _ -> unboxEntries((Map<?, ?>) boxed, new TreeMap<>());
      case MapTypeDescriptor<?, ?> _ -> unboxEntries((Map<?, ?>) boxed, new LinkedHashMap<>());
      default -> boxed;
    };
  }

  private static <C extends Collection<Value<?>>> C boxElements(Collection<?> raw, C target) {
    for (Object element : raw) {
      target.add(Value.of(element));
    }
    return target;
  }

  private static <M extends Map<Object, Value<?>>> M boxEntries(Map<?, ?> raw, M target) {
    for (var entry : raw.entrySet()) {
      target.put(entry.getKey(), Value.of(entry.getValue()));
    }
    return target;
  }

  private static <C extends Collection<Object>> C unboxElements(Collection<?> boxed, C target) {
    for (Object element : boxed) {
      target.add(((Value<?>) element).unbox());
    }
    return target;
  }

  private static <M extends Map<Object, Object>> M unboxEntries(Map<?, ?> boxed, M target) {
    for (var entry : boxed.entrySet()) {
      target.put(entry.getKey(), ((Value<?>) entry.getValue()).unbox());
    }
    return target;
  }

  private static String resolveName(StateReader r, int id, SlotSpec<?> slot) {
    if (r.hasField(id, slot.name())) {
      return slot.name();
    }
    for (String alias : slot.aliases()) {
      if (r.hasField(id, alias)) {
        return alias;
      }
    }
    return null;
  }

  private static byte readByteRequired(StateReader r, int id, String name) {
    byte[] bytes = r.readBytes(id, name);
    if (bytes.length != 1) {
      throw new IllegalStateException("Byte slot '" + name + "' expected 1 byte but got " + bytes.length);
    }
    return bytes[0];
  }

  @Override
  public String qualifiedName() {
    return describedClass.getName();
  }

  @Override
  @SuppressWarnings("unchecked")
  public T read(StateReader r) {
    Object[] args = new Object[slots.size()];
    for (int i = 0; i < slots.size(); i++) {
      args[i] = readSlot(r, slots.get(i));
    }
    try {
      return (T) constructor.invokeWithArguments(args);
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable e) {
      throw new IllegalStateException("Failed to construct " + describedClass.getName() + " from slot values", e);
    }
  }

  @Override
  public void write(StateWriter w, T value) {
    for (var slot : slots) {
      Object slotValue;
      try {
        slotValue = slot.accessor().invoke(value);
      } catch (RuntimeException | Error e) {
        throw e;
      } catch (Throwable e) {
        throw new IllegalStateException(
          "Failed to read slot '" + slot.name() + "' from " + describedClass.getName(),
          e
        );
      }
      writeSlot(w, slot, slotValue);
    }
  }

  @Override
  public String signature() {
    var entries = new ArrayList<Signatures.SlotEntry>(slots.size());
    for (var s : slots) {
      entries.add(new Signatures.SlotEntry(s.id(), s.descriptor().signature()));
    }
    return Signatures.forStructured(descriptorName, entries);
  }

  @Override
  public StructuredTypeDescriptor<T> withAliases(String... aliases) {
    return new StructuredTypeDescriptor<>(
      descriptorName,
      List.of(aliases),
      evolutionPolicy,
      describedClass,
      slots,
      constructor
    );
  }

  @Override
  public StructuredTypeDescriptor<T> withEvolutionPolicy(EvolutionPolicy policy) {
    return new StructuredTypeDescriptor<>(
      descriptorName,
      nameAliases,
      Objects.requireNonNull(policy, "policy must not be null"),
      describedClass,
      slots,
      constructor
    );
  }

  /**
   * A descriptor with the named slot changed by {@code configurer}; throws {@link IllegalArgumentException} when no
   * slot has that name.
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public StructuredTypeDescriptor<T> withSlot(String slotName, UnaryOperator<SlotConfigurer<?>> configurer) {
    Objects.requireNonNull(slotName, "slotName must not be null");
    Objects.requireNonNull(configurer, "configurer must not be null");
    var newSlots = new ArrayList<SlotSpec<?>>(slots.size());
    boolean found = false;
    for (var s : slots) {
      if (s.name().equals(slotName)) {
        var cfg = new SlotConfigurer<>(s);
        var updated = (SlotConfigurer) configurer.apply(cfg);
        newSlots.add(updated.applyTo(s));
        found = true;
      } else {
        newSlots.add(s);
      }
    }
    if (!found) {
      throw new IllegalArgumentException(
        "No slot named '" +
          slotName +
          "' on " +
          descriptorName +
          "; existing slots: " +
          slots.stream().map(SlotSpec::name).toList()
      );
    }
    return new StructuredTypeDescriptor<>(
      descriptorName,
      nameAliases,
      evolutionPolicy,
      describedClass,
      newSlots,
      constructor
    );
  }
}
