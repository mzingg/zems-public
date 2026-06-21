package dev.zems.lib.value;

import dev.zems.lib.value.builtin.BigDecimalValue;
import dev.zems.lib.value.builtin.BigIntegerValue;
import dev.zems.lib.value.builtin.BooleanValue;
import dev.zems.lib.value.builtin.ByteValue;
import dev.zems.lib.value.builtin.CharacterValue;
import dev.zems.lib.value.builtin.CurrencyValue;
import dev.zems.lib.value.builtin.DoubleValue;
import dev.zems.lib.value.builtin.DurationValue;
import dev.zems.lib.value.builtin.FloatValue;
import dev.zems.lib.value.builtin.InetAddressValue;
import dev.zems.lib.value.builtin.IntegerValue;
import dev.zems.lib.value.builtin.ListValue;
import dev.zems.lib.value.builtin.LocalDateTimeValue;
import dev.zems.lib.value.builtin.LocalDateValue;
import dev.zems.lib.value.builtin.LocalTimeValue;
import dev.zems.lib.value.builtin.LocaleValue;
import dev.zems.lib.value.builtin.LongValue;
import dev.zems.lib.value.builtin.MapValue;
import dev.zems.lib.value.builtin.OffsetDateTimeValue;
import dev.zems.lib.value.builtin.PeriodValue;
import dev.zems.lib.value.builtin.SetValue;
import dev.zems.lib.value.builtin.ShortValue;
import dev.zems.lib.value.builtin.SortedMapValue;
import dev.zems.lib.value.builtin.StringValue;
import dev.zems.lib.value.builtin.TimeInstantValue;
import dev.zems.lib.value.builtin.UrlValue;
import dev.zems.lib.value.builtin.UuidValue;
import dev.zems.lib.value.builtin.YearMonthValue;
import dev.zems.lib.value.builtin.YearValue;
import dev.zems.lib.value.builtin.ZoneIdValue;
import dev.zems.lib.value.builtin.ZonedDateTimeValue;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Static utility namespace holding the sealed-type switch that inverts {@link Value#of(Object)}. Splitting the switch
 * out of the {@code Value} source mirrors the same decomposition pattern as the four sibling role-interfaces
 * ({@link ValueStateChecks}, {@link ValuePredicates}, {@link ValueConversions}, {@link ValueAccessors}) — but here as a
 * plain utility, not a supertype of {@code Value}, because every entry point is static.
 *
 * <p>
 * External callers should reach unboxing through the {@link Value#unbox(Value) Value.unbox(...)} static overloads,
 * which surface the same operations on the single canonical {@code Value.*} entry point and pick the right return shape
 * per wrapper subtype.
 *
 * <ul>
 * <li>{@link #unbox(Value)} — non-recursive: collection wrappers return their stored
 * {@code List<Value<E>>} / {@code Set<Value<E>>} / {@code Map<K, Value<V>>} /
 * {@code SortedMap<K, Value<V>>} shape.</li>
 * <li>{@link #unboxList(ListValue)} / {@link #unboxSet(SetValue)} /
 * {@link #unboxMap(MapValue)} / {@link #unboxSortedMap(SortedMapValue)} — recursive: elements
 * (and map values) are themselves unboxed, producing fully-raw {@code List<E>} / {@code Set<E>} /
 * {@code Map<K, V>} / {@code SortedMap<K, V>}.</li>
 * </ul>
 *
 * <p>
 * State markers ({@link NullValue}, {@link UndefinedValue}, {@link UnresolvedValue},
 * {@link ErrorValue}, {@link TombstoneValue}) carry no payload and trigger
 * {@link IllegalStateException} — both directly from {@link #unbox(Value)} and indirectly via
 * the recursive overloads when encountered as an element/value.
 */
public interface ValueUnboxing {
  static <T> List<T> unboxList(ListValue<T> value) {
    return value.elements().stream().map(ValueUnboxing::unbox).toList();
  }

  /**
   * Inverse of {@link Value#of(Object)}: returns the raw JDK payload wrapped by this value.
   *
   * <ul>
   * <li>Built-in scalars (e.g. {@link StringValue}, {@link IntegerValue}) → their wrapped JDK
   * value.</li>
   * <li>Collection wrappers ({@link ListValue}, {@link SetValue}, {@link MapValue},
   * {@link SortedMapValue}) → their backing collection.</li>
   * <li>{@link BoxedValue} → its {@code inner()}.</li>
   * <li>{@link CoreValue} implementations → {@code this} (the value IS the payload).</li>
   * <li>State markers ({@link NullValue} / {@link UndefinedValue} / {@link UnresolvedValue} /
   * {@link ErrorValue} / {@link TombstoneValue}) carry no payload and throw
   * {@link IllegalStateException}. The marshalling layer dispatches state markers separately,
   * so this is reached only on misuse.</li>
   * </ul>
   */
  @SuppressWarnings("unchecked")
  static <T> T unbox(Value<T> value) {
    return (T) switch (value) {
      case BoxedValue<?> b -> b.inner();
      case ListValue<?> lv -> lv.elements();
      case SetValue<?> sv -> sv.elements();
      case MapValue<?, ?> mv -> mv.entries();
      case SortedMapValue<?, ?> sm -> sm.entries();
      case StringValue s -> s.string();
      case BooleanValue b -> b.bool();
      case CharacterValue c -> c.character();
      case IntegerValue i -> i.intValue();
      case LongValue l -> l.longValue();
      case DoubleValue d -> d.doubleValue();
      case FloatValue f -> f.floatValue();
      case ShortValue s -> s.shortValue();
      case ByteValue b -> b.byteValue();
      case BigIntegerValue v -> v.bigInteger();
      case BigDecimalValue v -> v.bigDecimal();
      case UuidValue v -> v.uuid();
      case UrlValue v -> v.uri();
      case TimeInstantValue v -> v.instant();
      case LocalDateValue v -> v.localDate();
      case LocalDateTimeValue v -> v.localDateTime();
      case ZonedDateTimeValue v -> v.zonedDateTime();
      case OffsetDateTimeValue v -> v.offsetDateTime();
      case LocalTimeValue v -> v.localTime();
      case YearValue v -> v.year();
      case YearMonthValue v -> v.yearMonth();
      case ZoneIdValue v -> v.zoneId();
      case DurationValue v -> v.duration();
      case PeriodValue v -> v.period();
      case LocaleValue v -> v.locale();
      case CurrencyValue v -> v.currency();
      case InetAddressValue v -> v.inetAddress();
      // CoreValue<Self> case (e.g. SourceNode implements CoreValue<SourceNode>): the Value IS the T.
      case CoreValue<?> c -> c;
      // State markers carry no payload; unbox is invalid for them.
      case
        NullValue<?> _,
        UndefinedValue<T> _,
        UnresolvedValue<T> _,
        ErrorValue<T> _,
        TombstoneValue<T> _ -> throw new IllegalStateException(
        "Cannot unbox state-marker value " + value.getClass().getSimpleName()
      );
    };
  }

  static <T> Set<T> unboxSet(SetValue<T> value) {
    return value.elements().stream().map(ValueUnboxing::unbox).collect(Collectors.toUnmodifiableSet());
  }

  static <K, E> SortedMap<K, E> unboxSortedMap(SortedMapValue<K, E> value) {
    return value
      .entries()
      .entrySet()
      .stream()
      .collect(
        Collectors.collectingAndThen(
          Collectors.toMap(
            Map.Entry::getKey,
            e -> unbox(e.getValue()),
            (_, _) -> {
              throw new IllegalStateException("duplicate key in SortedMapValue.entries()");
            },
            TreeMap::new
          ),
          Collections::unmodifiableSortedMap
        )
      );
  }

  static <K, E> Map<K, E> unboxMap(MapValue<K, E> value) {
    return value
      .entries()
      .entrySet()
      .stream()
      .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> unbox(e.getValue())));
  }
}
