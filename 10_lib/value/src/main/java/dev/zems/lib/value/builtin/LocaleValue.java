package dev.zems.lib.value.builtin;

import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
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
   * True for the undetermined locale, which this type rejects: BCP 47 {@code "und"}, which {@link Locale#ROOT} and
   * {@code Locale.forLanguageTag("")} also canonicalise to. A private-use tag such as {@code "und-x-foo"} is accepted.
   */
  public static boolean isUndetermined(Locale locale) {
    return Locale.ROOT.equals(locale) || "und".equals(locale.toLanguageTag());
  }

  @Override
  public TypeDescriptor<Locale> valueType() {
    return BuiltinTypeDescriptors.LOCALE;
  }

  @Override
  public Locale unbox() {
    return locale;
  }
}
