package dev.zems.lib.value.json.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("JsonString")
@ContractTest
class JsonStringTest {

  @Nested
  @DisplayName("toString")
  class ToString {

    @Test
    @DisplayName("a built string escapes the quote, the backslash and the control characters, nothing else")
    void builtStringEscapesMinimally() {
      JsonString string = JsonString.of("\"\\\b\f\n\r\t\u0000\u000B\u001F/\u007F\u2028\uD800é");

      assertThat(string.toString()).isEqualTo("\"\\\"\\\\\\b\\f\\n\\r\\t\\u0000\\u000B\\u001F/\u007F\u2028\uD800é\"");
    }

    @Test
    @DisplayName("a built string without special characters is only quoted")
    void builtPlainStringIsQuoted() {
      assertThat(JsonString.of("plain text").toString()).isEqualTo("\"plain text\"");
      assertThat(JsonString.of("").toString()).isEqualTo("\"\"");
    }

    @Test
    @DisplayName("a parsed string keeps its source text, escapes as written")
    void parsedStringKeepsSourceText() {
      JsonValue string = Json.parse(" \"caf\\u00E9 \\/ \\t\" ");

      assertThat(string.toString()).isEqualTo("\"caf\\u00E9 \\/ \\t\"");
      assertThat(string.asString()).isEqualTo("café / \t");
    }
  }

  @Nested
  @DisplayName("asString")
  class AsString {

    @Test
    @DisplayName("an escaped lone surrogate is accepted as one char, the source text kept")
    void escapedLoneSurrogateIsOneChar() {
      JsonValue string = Json.parse("\"\\uD800\"");

      assertThat(string.asString()).isEqualTo("\uD800");
      assertThat(string.toString()).isEqualTo("\"\\uD800\"");
    }

    @Test
    @DisplayName("two escaped surrogates decode to one supplementary character")
    void escapedSurrogatePairDecodes() {
      assertThat(Json.parse("\"\\uD83D\\uDE00\"").asString()).isEqualTo("\uD83D\uDE00");
    }

    @Test
    @DisplayName("raw DEL, line separator and non-ASCII characters are read unchanged")
    void rawNonControlCharactersAreKept() {
      assertThat(Json.parse("\"\u007F\u2028é\uD83D\uDE00\"").asString()).isEqualTo("\u007F\u2028é\uD83D\uDE00");
    }
  }

  @Nested
  @DisplayName("of")
  class Of {

    @Test
    @DisplayName("a null text is refused with NullPointerException")
    void refusesNullText() {
      assertThatThrownBy(() -> JsonString.of(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("text must not be null");
    }
  }
}
