package dev.zems.lib.value.marshal.descriptor;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.StateReader;
import dev.zems.lib.value.marshal.StateWriter;
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
 * Slot-table-driven descriptor for record-shaped types. Read and write are mechanical: iterate the {@link #slots} list,
 * dispatch each slot by its {@link SlotKind} to the matching {@link StateReader}/{@link StateWriter} primitive, then
 * invoke {@link #constructor} to build the result.
 *
 * <p>
 * Aliases, defaults, and unknown-slot policy all operate uniformly on this slot table — there is no hand-written
 * read/write lambda. {@link #signature()} is derived from the slot table (primary slot names + per-slot descriptor
 * signatures), so any wire-shape change to the record bumps the signature.
 *
 * <p>
 * Constructed via {@link RecordSynthesis#synthesize(Class, Object...)} (typical) or directly for tests.
 * {@link #withAliases}, {@link #withEvolutionPolicy}, and {@link #withSlot} return new descriptor instances —
 * descriptors are immutable, friendly to JEP 401 Valhalla.
 *
 * @param <T> the type the descriptor describes
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
    // Each slot id must be unique within the descriptor. Auto-assignment by RecordSynthesis
    // produces sequential ids; uniqueness only fails when a user-supplied slot list collides.
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

  // ============ slot dispatch ============

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
    // Aliases bridge wire-key renames on JSON. Binary is id-anchored: hasField(id, name)
    // matches by id and ignores name+aliases there, so the alias loop is a no-op for that
    // format; on JSON it walks the candidate names against the wire's object keys.
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

  // A record's collection component is a raw List<E> / Set<E> / Map<K,V> / SortedMap<K,V>, but the matching collection
  // descriptor reads and writes the state-augmented form (List<Value<E>>, Map<K, Value<V>>, ...). Bridge the two: box
  // the raw elements / map values into Value on the way out, unbox them on the way back. Map keys stay raw both ways,
  // matching the descriptors. A plain (non-collection) record slot passes through unchanged.
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
      target.add(Value.unbox((Value<?>) element));
    }
    return target;
  }

  private static <M extends Map<Object, Object>> M unboxEntries(Map<?, ?> boxed, M target) {
    for (var entry : boxed.entrySet()) {
      target.put(entry.getKey(), Value.unbox((Value<?>) entry.getValue()));
    }
    return target;
  }

  /** Returns the first name (primary or alias) for which {@code hasField} is true, or {@code null}. */
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

  /** Byte is encoded as a 1-element {@code byte[]} on the wire (no dedicated readByte primitive). */
  private static byte readByteRequired(StateReader r, int id, String name) {
    byte[] bytes = r.readBytes(id, name);
    if (bytes.length != 1) {
      throw new IllegalStateException("Byte slot '" + name + "' expected 1 byte but got " + bytes.length);
    }
    return bytes[0];
  }

  // ============ read / write ============

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

  // ============ with* (immutable updates) ============

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
   * Returns a new descriptor with a single slot updated by the given configurer. Throws if {@code slotName} doesn't
   * match any existing slot.
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
