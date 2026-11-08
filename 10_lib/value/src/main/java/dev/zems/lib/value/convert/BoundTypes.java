package dev.zems.lib.value.convert;

import dev.zems.lib.value.TypeName;
import dev.zems.lib.value.Value;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The classes a caller is willing to build from data, under a common base type. A dispatching conversion matches a
 * type name only against these classes, by {@link Class#getName()}; a name outside the set is refused and never looked
 * up anywhere else.
 */
public final class BoundTypes<T> {

  private final Class<T> base;
  private final Map<String, Class<? extends T>> byName;

  private BoundTypes(Class<T> base, Map<String, Class<? extends T>> byName) {
    this.base = base;
    this.byName = byName;
  }

  /**
   * Binds {@code members} under {@code base}. Binding one class twice binds it once; an empty set refuses every name.
   *
   * @throws IllegalArgumentException if {@code base}, the array or a member is {@code null}; a member is not a subtype
   *     of {@code base}; a member's name is not a qualified type name (an array or hidden class); or two distinct
   *     classes share one name
   */
  @SafeVarargs
  public static <T> BoundTypes<T> of(Class<T> base, Class<? extends T>... members) {
    if (base == null) {
      throw new IllegalArgumentException("Base class must not be null");
    }
    if (members == null) {
      throw new IllegalArgumentException("Bound classes must not be null");
    }
    Map<String, Class<? extends T>> byName = new LinkedHashMap<>();
    for (Class<? extends T> member : members) {
      String name = boundNameOf(base, member);
      Class<? extends T> bound = byName.putIfAbsent(name, member);
      if (bound != null && bound != member) {
        throw new IllegalArgumentException("Cannot bind " + name + " twice: two distinct classes share that name");
      }
    }
    return new BoundTypes<>(base, Collections.unmodifiableMap(byName));
  }

  Class<T> base() {
    return base;
  }

  Class<? extends T> classOrNull(String name) {
    return byName.get(name);
  }

  /** The bound names in bind order, as {@code [a.B, c.D]}. */
  @Override
  public String toString() {
    return byName.keySet().toString();
  }

  private static String boundNameOf(Class<?> base, Class<?> member) {
    if (member == null) {
      throw new IllegalArgumentException("Bound class must not be null");
    }
    if (!base.isAssignableFrom(member)) {
      throw new IllegalArgumentException("Cannot bind " + member.getName() + ": it is not a " + base.getName());
    }
    Value<TypeName> name = Value.typeOf(member);
    if (name.isError()) {
      throw new IllegalArgumentException("Cannot bind " + member.getName() + ": its name is not a qualified type name");
    }
    return name.unbox().name();
  }
}
