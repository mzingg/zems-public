package dev.zems.lib.value.builtin;

/**
 * Class-load probe: {@link ProbedType} flips a flag on this holder from its static initialiser, so a test can tell
 * whether something resolved the probed class by name.
 *
 * <p>
 * Naming {@code ProbedType.class} from a test is safe: a class literal loads and links the class but does not run its
 * static initialiser (JLS 12.4.1), while {@code Class.forName(name)} does. The flag is JVM-global and never resets, so
 * exactly one test method may name {@link ProbedType}.
 */
public final class TypeNameProbe {

  private static boolean probedTypeInitialised;

  private TypeNameProbe() {}

  public static boolean probedTypeInitialised() {
    return probedTypeInitialised;
  }

  static void markProbedTypeInitialised() {
    probedTypeInitialised = true;
  }

  /** The class whose qualified name a {@code TypeValue} carries but which nothing may ever resolve. */
  public static final class ProbedType {

    static {
      markProbedTypeInitialised();
    }

    private ProbedType() {}
  }
}
