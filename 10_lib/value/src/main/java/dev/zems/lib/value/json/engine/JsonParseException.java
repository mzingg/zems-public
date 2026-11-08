package dev.zems.lib.value.json.engine;

import dev.zems.lib.value.json.engine.internal.JsonMessages;
import java.io.Serial;

/**
 * Thrown when a text is not a strict RFC 8259 JSON document. The message names the line and column of the error
 * counted from one, the getters count from zero.
 */
public final class JsonParseException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  private final int line;
  private final int position;

  /**
   * Creates the exception for an error at the given zero-based line and position.
   *
   * @throws IllegalArgumentException if the line or the position is negative
   */
  public JsonParseException(String message, int line, int position) {
    super(message);
    if (line < 0 || position < 0) {
      throw new IllegalArgumentException(JsonMessages.negativeLocation(line, position));
    }
    this.line = line;
    this.position = position;
  }

  /** Answers the zero-based line of the error; a line ends at a line feed. */
  public int getErrorLine() {
    return line;
  }

  /** Answers the zero-based position of the error within its line, counted in UTF-16 code units. */
  public int getErrorPosition() {
    return position;
  }
}
