package dev.zems.lib.value.valueio;

/**
 * Throwable reconstructed from the wire, carrying the original exception's class name and message; what
 * {@link StateReader#readError(int, String)} returns.
 */
public final class SerializedThrowable extends RuntimeException {

  private final String originalClassName;

  public SerializedThrowable(String originalClassName, String message) {
    super(message);
    this.originalClassName = originalClassName;
  }

  public String originalClassName() {
    return originalClassName;
  }

  /**
   * The class name that goes on the wire: for a {@code SerializedThrowable} the original class name, so a
   * write-read-write chain keeps the original class and the checksum feed matches the bytes.
   */
  public static String wireClassName(Throwable throwable) {
    return throwable instanceof SerializedThrowable st ? st.originalClassName() : throwable.getClass().getName();
  }
}
