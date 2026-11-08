package dev.zems.lib.value.json.engine;

import java.io.Serial;

/** Thrown when a JSON value is read as a type it does not have, or asked for a member or element it does not hold. */
public final class JsonValueException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  public JsonValueException(String message) {
    super(message);
  }
}
