package dev.zems.lib.value.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.ErrorValue;
import dev.zems.lib.value.Value;
import java.net.URI;
import java.net.URISyntaxException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("UrlValue")
@ContractTest
class UrlValueTest {

  @Nested
  @DisplayName("of(URI)")
  class OfUri {

    @Test
    @DisplayName("creates UrlValue with valid URI")
    void createsUrlValueWithValidUri() {
      var uri = URI.create("https://example.com/path");
      var urlValue = new UrlValue(uri);

      assertThat(urlValue.uri()).isEqualTo(uri);
    }

    @Test
    @DisplayName("throws NullPointerException for null URI")
    void throwsForNullUri() {
      assertThatThrownBy(() -> new UrlValue(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("URI must not be null");
    }
  }

  @Nested
  @DisplayName("of(String)")
  class OfString {

    @Test
    @DisplayName("creates UrlValue with valid URI string")
    void createsUrlValueWithValidUriString() {
      var urlValue = Value.urlOf("https://example.com/path");

      assertThat(urlValue).isInstanceOf(UrlValue.class);
      assertThat(((UrlValue) urlValue).uri()).isEqualTo(URI.create("https://example.com/path"));
    }

    @Test
    @DisplayName("creates UrlValue with file URI")
    void createsUrlValueWithFileUri() {
      var urlValue = Value.urlOf("file:///path/to/file");

      assertThat(urlValue).isInstanceOf(UrlValue.class);
      assertThat(((UrlValue) urlValue).uri()).isEqualTo(URI.create("file:///path/to/file"));
    }

    @Test
    @DisplayName("returns ErrorValue for null string")
    void returnsErrorValueForNull() {
      var result = Value.urlOf(null);

      assertThat(result).isInstanceOf(ErrorValue.class);
      assertThat(result.error()).isPresent();
      assertThat(result.error())
        .get()
        .extracting(Throwable::getMessage)
        .isEqualTo("URI string must not be null or empty");
    }

    @Test
    @DisplayName("returns ErrorValue for empty string")
    void returnsErrorValueForEmpty() {
      var result = Value.urlOf("");

      assertThat(result).isInstanceOf(ErrorValue.class);
      assertThat(result.error()).isPresent();
      assertThat(result.error())
        .get()
        .extracting(Throwable::getMessage)
        .isEqualTo("URI string must not be null or empty");
    }

    @Test
    @DisplayName("returns ErrorValue for blank string")
    void returnsErrorValueForBlank() {
      var result = Value.urlOf("   ");

      assertThat(result).isInstanceOf(ErrorValue.class);
      assertThat(result.error()).isPresent();
      assertThat(result.error())
        .get()
        .extracting(Throwable::getMessage)
        .isEqualTo("URI string must not be null or empty");
    }

    @Test
    @DisplayName("returns ErrorValue for invalid URI syntax")
    void returnsErrorValueForInvalidUri() {
      var result = Value.urlOf("not a valid uri with spaces");

      assertThat(result).isInstanceOf(ErrorValue.class);
      assertThat(result.error()).isPresent();
      assertThat(result.error()).get().isInstanceOf(URISyntaxException.class);
    }
  }

  @Nested
  @DisplayName("state accessors")
  class StateAccessors {

    @Test
    @DisplayName("isDefined returns true")
    void isDefinedReturnsTrue() {
      assertThat(aUrlValue().isDefined()).isTrue();
    }

    private Value<URI> aUrlValue() {
      return Value.urlOf("https://example.com");
    }

    @Test
    @DisplayName("isNotNull returns true")
    void isNotNullReturnsTrue() {
      assertThat(aUrlValue().isNotNull()).isTrue();
    }

    @Test
    @DisplayName("isResolved returns true")
    void isResolvedReturnsTrue() {
      assertThat(aUrlValue().isResolved()).isTrue();
    }

    @Test
    @DisplayName("error returns empty Optional")
    void errorReturnsEmpty() {
      assertThat(aUrlValue().error()).isEmpty();
    }
  }

  @Nested
  @DisplayName("equals and hashCode")
  class EqualsAndHashCode {

    @Test
    @DisplayName("equal UrlValues have same URI")
    void equalUrlValuesHaveSameUri() {
      var urlValue1 = Value.urlOf("https://example.com");
      var urlValue2 = Value.urlOf("https://example.com");

      assertThat(urlValue1).isEqualTo(urlValue2);
      assertThat(urlValue1.hashCode()).isEqualTo(urlValue2.hashCode());
    }

    @Test
    @DisplayName("different URIs produce different UrlValues")
    void differentUrisProduceDifferentUrlValues() {
      var urlValue1 = Value.urlOf("https://example.com/path1");
      var urlValue2 = Value.urlOf("https://example.com/path2");

      assertThat(urlValue1).isNotEqualTo(urlValue2);
    }

    @Test
    @DisplayName("UrlValue is not equal to null")
    void notEqualToNull() {
      var urlValue = Value.urlOf("https://example.com");
    }

    @Test
    @DisplayName("UrlValue is not equal to different type")
    void notEqualToDifferentType() {
      var urlValue = Value.urlOf("https://example.com");
    }
  }

  @Nested
  @DisplayName("toString")
  class ToString {

    @Test
    @DisplayName("returns formatted string with URI")
    void returnsFormattedStringWithUri() {
      var urlValue = Value.urlOf("https://example.com/path");

      assertThat(urlValue.toString()).isEqualTo("UrlValue[uri=https://example.com/path]");
    }
  }
}
