package dev.zems.lib.value;

import dev.zems.lib.value.builtin.BooleanValue;
import dev.zems.lib.value.builtin.BuiltInValue;
import dev.zems.lib.value.builtin.ByteValue;
import dev.zems.lib.value.builtin.CurrencyValue;
import dev.zems.lib.value.builtin.DoubleValue;
import dev.zems.lib.value.builtin.FloatValue;
import dev.zems.lib.value.builtin.InetAddressValue;
import dev.zems.lib.value.builtin.IntegerValue;
import dev.zems.lib.value.builtin.LocaleValue;
import dev.zems.lib.value.builtin.LongValue;
import dev.zems.lib.value.builtin.ShortValue;
import dev.zems.lib.value.builtin.StringValue;
import java.util.Optional;

/**
 * Cross-type conversion facet of {@link Value}: polymorphic {@code asString}, {@code asInteger}, {@code asDouble},
 * {@code asBoolean} that walk multiple {@code BuiltInValue} cases and return the canonical round-trippable form (e.g.
 * ISO-8601 for {@code java.time.*}).
 *
 * <p>
 * {@code Value<T>} extends this interface so callers see these methods directly on {@code Value}; this type exists
 * purely to keep the {@link Value} source readable.
 */
public interface ValueConversions {
  /**
   * Returns the value as a String — directly for {@link StringValue}, or as the canonical round-trippable form for
   * every other typed wrapper. Collection wrappers, {@link BoxedValue}, {@link CoreValue}, and state markers return
   * {@link Optional#empty()}.
   */
  default Optional<String> asString() {
    if (this instanceof Value<?> v && (v.isList() || v.isSet() || v.isMap() || v.isSortedMap())) {
      return Optional.empty();
    }
    if (!(this instanceof BuiltInValue<?> b)) {
      return Optional.empty();
    }
    return switch (b) {
      case LocaleValue v -> Optional.of(v.locale().toLanguageTag());
      case CurrencyValue v -> Optional.of(v.currency().getCurrencyCode());
      case InetAddressValue v -> Optional.of(v.inetAddress().getHostAddress());
      default -> Optional.of(Value.unbox((Value<?>) b).toString());
    };
  }

  /**
   * Returns the value as an Integer if directly a numeric {@link BuiltInValue} (via {@code intValue()}), or via
   * compatible conversion from {@link StringValue} ({@code parseInt}).
   */
  default Optional<Integer> asInteger() {
    var asNumber = asNumber();
    if (asNumber.isPresent()) {
      return Optional.of(asNumber.get().intValue());
    }
    if (this instanceof StringValue(String string)) {
      try {
        return Optional.of(Integer.parseInt(string));
      } catch (NumberFormatException _) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  /**
   * Returns the value as a Number when this is one of the six primitive numeric wrappers ({@code IntegerValue},
   * {@code LongValue}, {@code DoubleValue}, {@code FloatValue}, {@code ShortValue}, {@code ByteValue}); empty
   * otherwise.
   *
   * <p>
   * Declared here so {@link #asInteger} and {@link #asDouble} can delegate without coupling to {@code ValueAccessors};
   * {@code ValueAccessors} inherits this default unchanged.
   */
  default Optional<Number> asNumber() {
    return switch (this) {
      case IntegerValue v -> Optional.of(v.intValue());
      case LongValue v -> Optional.of(v.longValue());
      case DoubleValue v -> Optional.of(v.doubleValue());
      case FloatValue v -> Optional.of(v.floatValue());
      case ShortValue v -> Optional.of(v.shortValue());
      case ByteValue v -> Optional.of(v.byteValue());
      default -> Optional.empty();
    };
  }

  /**
   * Returns the value as a Double if directly a numeric {@link BuiltInValue} (via {@code doubleValue()}), or via
   * compatible conversion from {@link StringValue} ({@code parseDouble}).
   */
  default Optional<Double> asDouble() {
    var asNumber = asNumber();
    if (asNumber.isPresent()) {
      return Optional.of(asNumber.get().doubleValue());
    }
    if (this instanceof StringValue(String string)) {
      try {
        return Optional.of(Double.parseDouble(string));
      } catch (NumberFormatException _) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  /**
   * Returns the value as a Boolean if directly a {@link BooleanValue}, or via compatible conversion from
   * {@link StringValue} ({@code "true"}/{@code "false"}, case-insensitive).
   */
  default Optional<Boolean> asBoolean() {
    return switch (this) {
      case BooleanValue b -> Optional.of(b.bool());
      case StringValue s -> {
        String str = s.string();
        if ("true".equalsIgnoreCase(str)) {
          yield Optional.of(true);
        } else if ("false".equalsIgnoreCase(str)) {
          yield Optional.of(false);
        } else {
          yield Optional.empty();
        }
      }
      default -> Optional.empty();
    };
  }
}
