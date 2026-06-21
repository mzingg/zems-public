package dev.zems.lib.value.marshal.descriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Names.validate — slot/alias name rules")
@ContractTest
class NamesTest {

  @ParameterizedTest
  @ValueSource(
    strings = {
      "name",
      "myField",
      "_x",
      "x_y",
      "field0",
      "X-Y",
      "field.name",
      "a:b",
      "name with spaces",
      "ünıçôde",
      "πάδα",
      "🎯",
    }
  )
  @DisplayName("accepts a wide range of legal names")
  void acceptsLegalNames(String name) {
    assertThat(Names.validate(name, "slot")).isEqualTo(name);
  }

  @Test
  @DisplayName("rejects null")
  void rejectsNull() {
    assertThatThrownBy(() -> Names.validate(null, "slot"))
      .isInstanceOf(NullPointerException.class)
      .hasMessageContaining("slot name must not be null");
  }

  @Test
  @DisplayName("rejects empty")
  void rejectsEmpty() {
    assertThatThrownBy(() -> Names.validate("", "slot"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("slot name must not be empty");
  }

  @Test
  @DisplayName("rejects NUL character")
  void rejectsNul() {
    String name = "a" + (char) 0x00 + "b";
    assertThatThrownBy(() -> Names.validate(name, "slot"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("disallowed control character")
      .hasMessageContaining("U+0000");
  }

  @Test
  @DisplayName("rejects U+0001 control character")
  void rejectsLowControlChar() {
    String name = "a" + (char) 0x01 + "b";
    assertThatThrownBy(() -> Names.validate(name, "slot"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("U+0001");
  }

  @Test
  @DisplayName("rejects DEL (U+007F)")
  void rejectsDel() {
    String name = "a" + (char) 0x7F + "b";
    assertThatThrownBy(() -> Names.validate(name, "slot"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("U+007F");
  }

  @Test
  @DisplayName("accepts whitespace control chars (TAB/LF/CR)")
  void acceptsWhitespaceControls() {
    assertThat(Names.validate("a\tb", "slot")).isEqualTo("a\tb");
    assertThat(Names.validate("a\nb", "slot")).isEqualTo("a\nb");
    assertThat(Names.validate("a\rb", "slot")).isEqualTo("a\rb");
  }

  @Test
  @DisplayName("rejects lone high surrogate")
  void rejectsLoneHighSurrogate() {
    String name = "a" + (char) 0xD83C + "b";
    assertThatThrownBy(() -> Names.validate(name, "slot"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("lone high surrogate");
  }

  @Test
  @DisplayName("rejects lone low surrogate")
  void rejectsLoneLowSurrogate() {
    String name = "a" + (char) 0xDC00 + "b";
    assertThatThrownBy(() -> Names.validate(name, "slot"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("lone low surrogate");
  }

  @Test
  @DisplayName("paired surrogates (e.g. emoji) are accepted")
  void acceptsPairedSurrogates() {
    var emoji = new String(Character.toChars(0x1F389)); // 🎉
    assertThat(Names.validate(emoji, "slot")).isEqualTo(emoji);
  }

  @Test
  @DisplayName("error message uses the supplied kind label")
  void errorUsesKindLabel() {
    assertThatThrownBy(() -> Names.validate("", "alias")).hasMessageContaining("alias name must not be empty");
  }
}
