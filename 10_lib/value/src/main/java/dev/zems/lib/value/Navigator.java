package dev.zems.lib.value;

/** Steps through a value's structure; a step never throws on data, a failure travels to the exit. */
public interface Navigator {
  /**
   * Steps to the member with the given name.
   *
   * @throws IllegalArgumentException if {@code name} is null
   */
  Navigator get(String name);

  /** Steps to the element at the given index. */
  Navigator get(int index);

  /** Answers the reached value, or the first failure. */
  Value<?> value();

  /**
   * Converts the reached value as {@link Value#convertTo(Class)} does; a failure passes through re-typed.
   *
   * @throws IllegalArgumentException if {@code target} is null
   */
  <R> Value<R> convertTo(Class<R> target);
}
