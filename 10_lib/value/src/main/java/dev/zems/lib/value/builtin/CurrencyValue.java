package dev.zems.lib.value.builtin;

import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.util.Currency;
import java.util.Objects;

public record CurrencyValue(Currency currency) implements BuiltInValue<Currency> {
  public CurrencyValue {
    Objects.requireNonNull(currency, "Currency must not be null");
  }

  @Override
  public TypeDescriptor<Currency> valueType() {
    return BuiltinTypeDescriptors.CURRENCY;
  }
}
