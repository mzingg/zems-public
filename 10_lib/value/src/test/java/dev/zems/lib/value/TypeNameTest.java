package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ContractTest
@DisplayName("TypeName")
class TypeNameTest {

  @Nested
  @DisplayName("isQualifiedName — the single spelling of the shape rule")
  class IsQualifiedName {

    @ParameterizedTest(name = "accepts \"{0}\"")
    @ValueSource(
      strings = {
        "com.example.Person",
        "Person",
        "a.b.c.d.e.Deep",
        "com.example.Outer$Inner",
        "com.example.Outer$1",
        "int",
        "void",
        "com.example.class9",
        "_leading.$dollar",
      }
    )
    void acceptsCanonicalNames(String candidate) {
      assertThat(TypeName.isQualifiedName(candidate)).isTrue();
    }

    @ParameterizedTest(name = "rejects \"{0}\"")
    @ValueSource(
      strings = {
        "",
        " ",
        "   ",
        "a..b",
        ".a",
        "a.",
        ".",
        "not a name!",
        "[Ljava.lang.String;",
        "com.example.Foo$$Lambda/0x1",
        "1Foo",
        "com.example.1Person",
        "com.example.Person ",
      }
    )
    void rejectsMalformedNames(String candidate) {
      assertThat(TypeName.isQualifiedName(candidate)).isFalse();
    }

    @Test
    @DisplayName("a null candidate is not a name (the factories pre-check it for a friendlier message)")
    void rejectsNull() {
      assertThat(TypeName.isQualifiedName(null)).isFalse();
    }
  }

  @Nested
  @DisplayName("construction")
  class Construction {

    @ParameterizedTest(name = "rejects \"{0}\" with the shared message")
    @ValueSource(strings = { "", "   ", "a..b", "not a name!", "[Ljava.lang.String;" })
    void rejectsMalformedNameWithTheSharedMessage(String candidate) {
      assertThatThrownBy(() -> new TypeName(candidate))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid type name: " + candidate);
    }

    @Test
    @DisplayName("rejects null before the shape check, with its own message")
    void rejectsNullWithDocumentedMessage() {
      assertThatThrownBy(() -> new TypeName(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Type name must not be null");
    }
  }

  @Nested
  @DisplayName("toString")
  class ToStringForm {

    @Test
    @DisplayName("is the bare name — the canonical form the wire formatter and the String conversion both carry")
    void isTheBareName() {
      assertThat(new TypeName("com.example.Person")).hasToString("com.example.Person");
      assertThat(new TypeName("com.example.Outer$Inner")).hasToString("com.example.Outer$Inner");
      assertThat(new TypeName("int")).hasToString("int");
    }
  }
}
