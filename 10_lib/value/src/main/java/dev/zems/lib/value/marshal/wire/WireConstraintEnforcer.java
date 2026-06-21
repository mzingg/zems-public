package dev.zems.lib.value.marshal.wire;

/**
 * Static check methods that throw {@link WireConstraintViolationException} when a {@link WireConstraints} bound is
 * exceeded. Centralised so format readers/writers stay free of ad-hoc validation logic and the violation message is
 * uniform.
 *
 * <p>
 * Public because the format implementations (binary / JSON) live in sibling packages and need to call these checks; the
 * class is otherwise stateless and final.
 */
public final class WireConstraintEnforcer {

  private WireConstraintEnforcer() {}

  // A wire-declared length/size is unsigned by intent; a negative value here means the count was decoded into a signed
  // slot and wrapped (e.g. a hostile size prefix). Reject it as a structured violation rather than letting it surface
  // as a generic JDK error (negative array capacity) or a silently empty result.
  private static void rejectNegative(String constraint, long observed) {
    if (observed < 0) {
      throw new WireConstraintViolationException(constraint, "negative declared length " + observed);
    }
  }

  public static void checkDepth(int newDepth, WireConstraints c) {
    if (newDepth > c.maxNestingDepth()) {
      throw new WireConstraintViolationException("maxNestingDepth", c.maxNestingDepth(), newDepth);
    }
  }

  public static void checkStringLength(long observed, WireConstraints c) {
    rejectNegative("maxStringLength", observed);
    if (observed > c.maxStringLength()) {
      throw new WireConstraintViolationException("maxStringLength", c.maxStringLength(), observed);
    }
  }

  public static void checkNumberLength(int observed, WireConstraints c) {
    if (observed > c.maxNumberLength()) {
      throw new WireConstraintViolationException("maxNumberLength", c.maxNumberLength(), observed);
    }
  }

  public static void checkArrayLength(int observed, WireConstraints c) {
    rejectNegative("maxArrayLength", observed);
    if (observed > c.maxArrayLength()) {
      throw new WireConstraintViolationException("maxArrayLength", c.maxArrayLength(), observed);
    }
  }

  public static void checkMapEntries(int observed, WireConstraints c) {
    rejectNegative("maxMapEntries", observed);
    if (observed > c.maxMapEntries()) {
      throw new WireConstraintViolationException("maxMapEntries", c.maxMapEntries(), observed);
    }
  }

  public static void checkDuplicateKey(String name, WireConstraints c) {
    if (c.duplicateKeyPolicy() == WireConstraints.DuplicateKeyPolicy.FAIL) {
      throw new WireConstraintViolationException("duplicateKey", "name '" + name + "' written twice in the same scope");
    }
  }

  public static void checkFinite(double value, WireConstraints c) {
    if (!c.allowNonFiniteNumbers() && !Double.isFinite(value)) {
      throw new WireConstraintViolationException("allowNonFiniteNumbers", "non-finite double " + value + " rejected");
    }
  }

  public static void checkFinite(float value, WireConstraints c) {
    if (!c.allowNonFiniteNumbers() && !Float.isFinite(value)) {
      throw new WireConstraintViolationException("allowNonFiniteNumbers", "non-finite float " + value + " rejected");
    }
  }
}
