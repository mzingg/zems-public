package dev.zems.lib.value.json;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.json.engine.Json;
import dev.zems.lib.value.json.engine.JsonNull;

/** Emits JSON documents as text; the null marker emits {@code null}, any other state marker answers itself re-typed. */
public final class JsonText {

  private JsonText() {}

  /**
   * Emits the document as compact JSON; a value that is not JSON answers an unsupported value.
   *
   * @throws IllegalArgumentException if {@code document} is null
   */
  public static Value<String> compact(Value<?> document) {
    if (document == null) {
      throw new IllegalArgumentException(JsonValueMessages.DOCUMENT_NULL);
    }
    var engine = JsonWrapping.engineOrNull(document);
    return engine == null ? notEmitted(document) : Value.of(engine.toString());
  }

  /**
   * Emits the document as indented JSON, one member or element per line; a value that is not JSON answers an
   * unsupported value.
   *
   * @throws IllegalArgumentException if {@code document} or {@code indent} is null, or the indent holds anything but
   *     spaces, tabs, line feeds and carriage returns
   */
  public static Value<String> indented(Value<?> document, String indent) {
    if (document == null) {
      throw new IllegalArgumentException(JsonValueMessages.DOCUMENT_NULL);
    }
    if (indent == null) {
      throw new IllegalArgumentException(JsonValueMessages.INDENT_NULL);
    }
    // The engine checks the indent only while it prints; printing a null first refuses a bad indent with the engine's
    // own message before any document is looked at.
    Json.toDisplayString(JsonNull.of(), indent);
    var engine = JsonWrapping.engineOrNull(document);
    return engine == null ? notEmitted(document) : Value.of(Json.toDisplayString(engine, indent));
  }

  private static Value<String> notEmitted(Value<?> document) {
    if (document.isStateMarker()) {
      return document.convertTo(String.class);
    }
    return Value.unsupported(JsonValueMessages.notJson(document), String.class);
  }
}
