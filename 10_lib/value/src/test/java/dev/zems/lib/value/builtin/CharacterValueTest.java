package dev.zems.lib.value.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.Protocol;
import dev.zems.lib.value.marshal.ValueIo;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CharacterValue")
@ContractTest
class CharacterValueTest {

  @Test
  void valueOfCharacterProducesCharacterValue() {
    Value<Character> v = Value.of('x');
    assertThat(v).isInstanceOf(CharacterValue.class);
    assertThat(((CharacterValue) v).character()).isEqualTo('x');
  }

  @Test
  void valueOfNullProducesError() {
    Value<Character> v = Value.of((Character) null);
    assertThat(v.error()).isPresent();
  }

  @Test
  void asCharacterReturnsWrappedChar() {
    Value<Character> v = Value.of('Z');
    assertThat(v.asCharacter()).contains('Z');
  }

  @Test
  void asCharacterEmptyForOtherTypes() {
    assertThat(Value.of("not-a-char").asCharacter()).isEmpty();
  }

  @Test
  void isCharacterTrueForCharacterValue() {
    assertThat(Value.of('a').isCharacter()).isTrue();
    assertThat(Value.of("a").isCharacter()).isFalse();
  }

  @Test
  void asStringReturnsCanonicalForm() {
    assertThat(Value.of('Q').asString()).contains("Q");
  }

  @Test
  void binaryRoundTrip() {
    var bos = new ByteArrayOutputStream();
    try (var w = ValueIo.framed().binaryWriter(bos)) {
      w.write(Value.of('K'), TypeDescriptor.of(Character.class));
    }
    try (var r = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
      Value<Character> v = r.read(Character.class);
      assertThat(v).isInstanceOf(CharacterValue.class);
      assertThat(((CharacterValue) v).character()).isEqualTo('K');
    }
  }

  @Test
  void jsonRoundTrip() {
    var sw = new StringWriter();
    try (var w = ValueIo.framed().jsonWriter(sw)) {
      w.write(Value.of('μ'), TypeDescriptor.of(Character.class));
    }
    try (var r = ValueIo.framed().jsonReader(new StringReader(sw.toString()))) {
      Value<Character> v = r.read(Character.class);
      assertThat(((CharacterValue) v).character()).isEqualTo('μ');
    }
  }
}
