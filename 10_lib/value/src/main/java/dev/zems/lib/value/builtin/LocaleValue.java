package dev.zems.lib.value.builtin;

import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.util.Locale;
import java.util.Objects;

public record LocaleValue(Locale locale) implements BuiltInValue<Locale> {
  public LocaleValue {
    Objects.requireNonNull(locale, "Locale must not be null");
    if (isUndetermined(locale)) {
      throw new IllegalArgumentException("Locale must not be the undetermined locale");
    }
  }

  /**
   * True for the undetermined locale, which the value layer rejects. BCP 47 {@code "und"} is the undetermined-locale
   * tag, and {@link Locale#ROOT} canonicalises to it (empty language → {@code toLanguageTag()} emits {@code "und"}), as
   * do {@code Locale.forLanguageTag("")} and the literal {@code "und"} locale. Private-use tags like {@code "und-x-foo"}
   * carry information and are accepted. This is the single spelling of the rule: the constructor, {@code Value.of(Locale)},
   * and the {@code Value.localeOf(String)} parser all defer to it so the rejection has exactly one definition.
   */
  public static boolean isUndetermined(Locale locale) {
    return Locale.ROOT.equals(locale) || "und".equals(locale.toLanguageTag());
  }

  @Override
  public TypeDescriptor<Locale> valueType() {
    return BuiltinTypeDescriptors.LOCALE;
  }
}
