package dev.zems.lib.value.valueio.wire;

/** Static checks that throw {@link WireConstraintViolationException} when a {@link WireConstraints} bound is broken. */
public final class WireConstraintEnforcer {

  private WireConstraintEnforcer() {}

  // A negative count passes the maximum check and would surface later as a JDK negative-array-size error or an empty
  // result; rejecting it here keeps a wrapped or hostile size prefix a structured violation.
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
