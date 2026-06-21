package dev.zems.lib.value.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.ErrorValue;
import dev.zems.lib.value.Value;
import java.util.Locale;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LocaleValue")
@ContractTest
class LocaleValueTest {

  private static final String SAMPLE_TAG = "en-US";
  private static final Locale SAMPLE = Locale.forLanguageTag(SAMPLE_TAG);

  @Nested
  @DisplayName("of(Locale)")
  class OfLocale {

    @Test
    void createsWithProvided() {
      assertThat(new LocaleValue(SAMPLE).locale()).isEqualTo(SAMPLE);
    }

    @Test
    void throwsForNull() {
      assertThatThrownBy(() -> new LocaleValue(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Locale must not be null");
    }

    @Test
    void throwsForUndefinedLocale() {
      assertThatThrownBy(() -> new LocaleValue(Locale.forLanguageTag("")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Locale must not be the undetermined locale");
    }

    @Test
    void throwsForRootLocale() {
      assertThatThrownBy(() -> new LocaleValue(Locale.ROOT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Locale must not be the undetermined locale");
    }
  }

  @Nested
  @DisplayName("isUndetermined (single shared rejection rule)")
  class IsUndetermined {

    @Test
    void trueForRootAndUndeterminedTags() {
      assertThat(LocaleValue.isUndetermined(Locale.ROOT)).isTrue();
      assertThat(LocaleValue.isUndetermined(Locale.forLanguageTag("und"))).isTrue();
      assertThat(LocaleValue.isUndetermined(Locale.forLanguageTag(""))).isTrue();
    }

    @Test
    void falseForRealAndPrivateUseLocales() {
      assertThat(LocaleValue.isUndetermined(SAMPLE)).isFalse();
      // Private-use tags carry information, so "und-x-foo" is a determined locale.
      assertThat(LocaleValue.isUndetermined(Locale.forLanguageTag("und-x-foo"))).isFalse();
    }
  }

  @Nested
  @DisplayName("of(String)")
  class OfString {

    @Test
    void createsFromTag() {
      var result = Value.localeOf(SAMPLE_TAG);
      assertThat(result).isInstanceOf(LocaleValue.class);
      assertThat(((LocaleValue) result).locale()).isEqualTo(SAMPLE);
    }

    @Test
    void errorForNullString() {
      var result = Value.localeOf(null);
      assertThat(result).isInstanceOf(ErrorValue.class);
      assertThat(result.error())
        .get()
        .extracting(Throwable::getMessage)
        .isEqualTo("Locale tag must not be null or empty");
    }

    @Test
    void errorForBlank() {
      var result = Value.localeOf("  ");
      assertThat(result).isInstanceOf(ErrorValue.class);
      assertThat(result.error())
        .get()
        .extracting(Throwable::getMessage)
        .isEqualTo("Locale tag must not be null or empty");
    }

    @Test
    void errorForInvalid() {
      var result = Value.localeOf("@@@");
      assertThat(result).isInstanceOf(ErrorValue.class);
      assertThat(result.error())
        .get()
        .extracting(Throwable::getMessage, InstanceOfAssertFactories.STRING)
        .startsWith("Invalid Locale");
    }
  }

  @Nested
  @DisplayName("state accessors")
  class StateAccessors {

    @Test
    void isDefinedReturnsTrue() {
      assertThat(new LocaleValue(SAMPLE).isDefined()).isTrue();
    }

    @Test
    void isNotNullReturnsTrue() {
      assertThat(new LocaleValue(SAMPLE).isNotNull()).isTrue();
    }

    @Test
    void isResolvedReturnsTrue() {
      assertThat(new LocaleValue(SAMPLE).isResolved()).isTrue();
    }

    @Test
    void errorReturnsEmpty() {
      assertThat(new LocaleValue(SAMPLE).error()).isEmpty();
    }
  }

  @Nested
  @DisplayName("equals and hashCode")
  class EqualsAndHashCode {

    @Test
    void equalValues() {
      assertThat(new LocaleValue(SAMPLE)).isEqualTo(new LocaleValue(SAMPLE));
      assertThat(new LocaleValue(SAMPLE).hashCode()).isEqualTo(new LocaleValue(SAMPLE).hashCode());
    }

    @Test
    void differentValues() {
      assertThat(Value.localeOf("en-US")).isNotEqualTo(Value.localeOf("de-DE"));
    }
  }

  @Nested
  @DisplayName("toString")
  class ToStringTest {

    @Test
    void formattedString() {
      assertThat(new LocaleValue(SAMPLE).toString()).isEqualTo("LocaleValue[locale=" + SAMPLE + "]");
    }
  }

  @Nested
  @DisplayName("round-trip")
  class RoundTrip {

    @Test
    void parseToLanguageTagRoundTrips() {
      var original = new LocaleValue(SAMPLE);
      assertThat(Value.localeOf(original.locale().toLanguageTag())).isEqualTo(original);
    }
  }
}
