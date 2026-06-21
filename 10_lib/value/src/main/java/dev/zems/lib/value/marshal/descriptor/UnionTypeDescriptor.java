package dev.zems.lib.value.marshal.descriptor;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.builtin.ListValue;
import dev.zems.lib.value.builtin.MapValue;
import dev.zems.lib.value.builtin.SetValue;
import dev.zems.lib.value.builtin.SortedMapValue;
import dev.zems.lib.value.marshal.StateReader;
import dev.zems.lib.value.marshal.StateWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;

/**
 * Explicit discriminated union ("oneOf") over a closed, author-supplied set of branch descriptors. The caller lists the
 * permitted branches; the wire discriminator is the branch's index in declared order — protobuf {@code oneof}, not an
 * implicit type tag synthesised from runtime reflection.
 *
 * <p>
 * The described type is {@code Object}: a {@link TypeDescriptor} describes the <em>unboxed</em> payload, and the
 * {@link Value} layer is supplied by {@link StateWriter#write(int, String, Value, TypeDescriptor)} / {@link #box(Object)}
 * around it. Composes with {@link ListTypeDescriptor} / {@link MapTypeDescriptor} so {@code ofList(oneOf(...))} and
 * {@code ofMap(key, oneOf(...))} marshal heterogeneous collections.
 *
 * <p>
 * Wire shape (inside the union's own record scope): slot {@code 0} = {@code int} branch index, slot {@code 1} = nested
 * record carrying that branch's payload. As a special case, a <b>single-branch</b> union writes no discriminator and
 * delegates straight to its sole branch — so {@code oneOf(name, E)} is wire-identical to {@code E}.
 */
public final class UnionTypeDescriptor implements TypeDescriptor<Object> {

  private static final int DISCRIMINATOR_ID = 0;
  private static final String DISCRIMINATOR_NAME = "$branch";
  private static final int PAYLOAD_ID = 1;
  private static final String PAYLOAD_NAME = "$value";

  private final String descriptorName;
  private final List<TypeDescriptor<?>> branches;
  private final Class<?>[] matchClasses;

  private UnionTypeDescriptor(String descriptorName, List<TypeDescriptor<?>> branches, Class<?>[] matchClasses) {
    this.descriptorName = descriptorName;
    this.branches = branches;
    this.matchClasses = matchClasses;
  }

  static UnionTypeDescriptor of(String descriptorName, List<TypeDescriptor<?>> branches) {
    if (descriptorName == null || descriptorName.isBlank()) {
      throw new IllegalArgumentException("descriptorName must not be blank");
    }
    Objects.requireNonNull(branches, "branches must not be null");
    if (branches.isEmpty()) {
      throw new IllegalArgumentException("oneOf requires at least one branch");
    }
    for (int i = 0; i < branches.size(); i++) {
      if (branches.get(i) == null) {
        throw new IllegalArgumentException("oneOf branch " + i + " must not be null");
      }
    }
    List<TypeDescriptor<?>> copy = List.copyOf(branches);
    Class<?>[] matchClasses = new Class<?>[copy.size()];
    for (int i = 0; i < copy.size(); i++) {
      matchClasses[i] = matchClassOf(copy.get(i));
    }
    return new UnionTypeDescriptor(descriptorName, copy, matchClasses);
  }

  /** Runtime class a raw payload must be an instance of to select the branch. */
  private static Class<?> matchClassOf(TypeDescriptor<?> branch) {
    return switch (branch) {
      case ScalarTypeDescriptor<?> s -> s.describedClass();
      case StructuredTypeDescriptor<?> s -> s.describedClass();
      case ListTypeDescriptor<?> _ -> List.class;
      case SetTypeDescriptor<?> _ -> Set.class;
      case SortedMapTypeDescriptor<?, ?> _ -> SortedMap.class;
      case MapTypeDescriptor<?, ?> _ -> Map.class;
      case UnionTypeDescriptor _ -> throw new IllegalArgumentException(
        "oneOf branches must not themselves be oneOf — flatten the union into its leaf branches"
      );
    };
  }

  /** The branch descriptors, in declared (discriminator) order. */
  public List<TypeDescriptor<?>> branches() {
    return branches;
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" }) // raw branch dispatch: the matched branch describes value's type
  public void write(StateWriter writer, Object value) {
    Objects.requireNonNull(value, "value must not be null");
    int idx = indexFor(value);
    TypeDescriptor branch = (TypeDescriptor) branches.get(idx);
    if (branches.size() == 1) {
      branch.write(writer, value); // single-branch: no discriminator (wire == the sole branch)
      return;
    }
    writer.writeInt(DISCRIMINATOR_ID, DISCRIMINATOR_NAME, idx);
    writer.writeRecord(PAYLOAD_ID, PAYLOAD_NAME, branch, value);
  }

  @Override
  public Object read(StateReader reader) {
    if (branches.size() == 1) {
      return branches.get(0).read(reader);
    }
    int idx = reader.readInt(DISCRIMINATOR_ID, DISCRIMINATOR_NAME);
    if (idx < 0 || idx >= branches.size()) {
      throw new IllegalStateException(
        "union branch index " + idx + " out of range [0, " + branches.size() + ") for " + descriptorName
      );
    }
    return reader.readRecord(PAYLOAD_ID, PAYLOAD_NAME, branches.get(idx));
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" }) // container wrapping mirrors the collection descriptors' box overrides
  public Value<Object> box(Object raw) {
    Value<?> boxed = switch (raw) {
      case SortedMap<?, ?> m -> new SortedMapValue(m);
      case Map<?, ?> m -> new MapValue(m);
      case Set<?> s -> new SetValue(s);
      case List<?> l -> new ListValue(l);
      default -> Value.of(raw);
    };
    return (Value<Object>) boxed;
  }

  private int indexFor(Object raw) {
    for (int i = 0; i < matchClasses.length; i++) {
      if (matchClasses[i].isInstance(raw)) {
        return i;
      }
    }
    throw new IllegalArgumentException(
      "value of type " + raw.getClass().getName() + " is not a member of " + qualifiedName()
    );
  }

  @Override
  public String descriptorName() {
    return descriptorName;
  }

  @Override
  public String qualifiedName() {
    StringBuilder sb = new StringBuilder("OneOf<");
    for (int i = 0; i < branches.size(); i++) {
      if (i > 0) {
        sb.append(" | ");
      }
      sb.append(branches.get(i).qualifiedName());
    }
    return sb.append('>').toString();
  }

  @Override
  public String signature() {
    List<String> branchSignatures = new ArrayList<>(branches.size());
    for (TypeDescriptor<?> branch : branches) {
      branchSignatures.add(branch.signature());
    }
    return Signatures.forUnion(descriptorName, branchSignatures);
  }

  @Override
  public int hashCode() {
    return Objects.hash(descriptorName, branches);
  }

  @Override
  public boolean equals(Object o) {
    return (
      o instanceof UnionTypeDescriptor that &&
      descriptorName.equals(that.descriptorName) &&
      branches.equals(that.branches)
    );
  }

  @Override
  public String toString() {
    return "UnionTypeDescriptor[" + descriptorName + " (" + qualifiedName() + ")]";
  }
}
