package dev.zems.lib.value.marshal;

/**
 * Reconstructed throwable that preserves the original exception class name and message after a round-trip through a
 * wire format. Returned by {@link StateReader#readError(int, String)} and by the high-level
 * {@link StateReader#read(int, String, dev.zems.lib.value.marshal.descriptor.TypeDescriptor)} boundary when the slot
 * carries an {@code ErrorValue}.
 */
public final class SerializedThrowable extends RuntimeException {

  private final String originalClassName;

  public SerializedThrowable(String originalClassName, String message) {
    super(message);
    this.originalClassName = originalClassName;
  }

  /** The fully qualified class name of the throwable that was originally serialized. */
  public String originalClassName() {
    return originalClassName;
  }
}
