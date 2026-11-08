package dev.zems.lib.value.valueio.descriptor;

/**
 * Policy for an enum constant on the wire that the reading enum does not declare; applied via {@link EvolutionPolicy}.
 *
 * <p>
 * The tolerance applies only where the read yields a {@code Value}: a top-level read, a list or set element, a map
 * value. Raw positions (record components, map keys, union payloads) have no place for an error value and always throw.
 */
public enum UnknownConstantPolicy {
  /** The default: throw {@link IllegalStateException} naming the unknown constant and the enum. */
  FAIL,

  /** Answer an {@code ErrorValue} carrying the same message, once the record read has completed. */
  ERROR_VALUE,
}
