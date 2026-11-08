package dev.zems.lib.value.json.engine.internal;

import dev.zems.lib.value.json.engine.JsonValue;
import java.util.List;
import java.util.Optional;

/** Renders where a value sits, in the words parse and value errors use; not part of the mirrored API. */
public final class JsonLocations {

  private JsonLocations() {}

  /**
   * Renders steps from the document root as a path: {@code $}, then {@code .name} or {@code ["name"]} per member and
   * {@code [i]} per element; more than 64 steps are elided to the first and the last 32.
   *
   * @param steps member names as {@link String}, element indices as {@link Integer}
   */
  public static String path(List<Object> steps) {
    return JsonMessages.path(steps);
  }

  /** Answers {@code "line L, column C"}, both counted from one, for a parsed value; empty for a built one. */
  public static Optional<String> lineAndColumn(JsonValue value) {
    if (value instanceof JsonSourced sourced && sourced.source() != null) {
      JsonLocator.Position position = JsonLocator.position(sourced.source(), sourced.offset());
      return Optional.of(JsonMessages.lineAndColumn(position.line() + 1, position.column() + 1));
    }
    return Optional.empty();
  }
}
