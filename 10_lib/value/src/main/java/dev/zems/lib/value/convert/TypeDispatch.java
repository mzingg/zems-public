package dev.zems.lib.value.convert;

import dev.zems.lib.value.SlotShape;
import dev.zems.lib.value.TypeName;
import dev.zems.lib.value.Value;
import java.util.Map;

/** The dispatching stage of {@link Converter#convert(Value, BoundTypes, String)}: the type slot picks a bound class. */
final class TypeDispatch {

  private TypeDispatch() {}

  static <T> Value<T> convert(Converter converter, Value<?> source, BoundTypes<T> types, String typeSlot) {
    Object payload = source.unbox();
    Class<T> base = types.base();
    SlotShape<?> shape = SlotShapes.forPayloadOrNull(payload);
    if (shape == null) {
      return ConversionErrors.noConversion(payload.getClass(), base);
    }
    Class<?> sourceType = shape.type();
    Value<?> slot = slotsOf(shape, payload).get(typeSlot);
    if (slot == null || slot.isUndefined() || slot.isTombstone()) {
      return ConversionErrors.failure(sourceType, base, "type slot '" + typeSlot + "' is missing");
    }
    if (!holdsTypeNameOrString(slot)) {
      return noTypeName(sourceType, base, typeSlot, slot);
    }
    Value<TypeName> name = converter.convert(slot, TypeName.class);
    if (!name.isPresent()) {
      return noTypeName(sourceType, base, typeSlot, name);
    }
    String typeName = name.unbox().name();
    Class<? extends T> matched = types.classOrNull(typeName);
    if (matched == null) {
      return ConversionErrors.failure(
        sourceType,
        base,
        "type name '" + typeName + "' is not in the bound set " + types
      );
    }
    return widened(converter.convert(source, matched));
  }

  private static boolean holdsTypeNameOrString(Value<?> slot) {
    return !slot.isStateMarker() && (slot.unbox() instanceof TypeName || slot.unbox() instanceof String);
  }

  private static <T> Value<T> noTypeName(Class<?> sourceType, Class<T> base, String typeSlot, Value<?> reading) {
    return ConversionErrors.failure(
      sourceType,
      base,
      "type slot '" + typeSlot + "' does not hold a type name",
      reading.error().orElse(null)
    );
  }

  @SuppressWarnings("unchecked") // forPayloadOrNull checked that the shape reads this payload
  private static <S> Map<String, Value<?>> slotsOf(SlotShape<S> shape, Object payload) {
    return shape.slots((S) payload);
  }

  @SuppressWarnings("unchecked") // values are immutable, so a value of a bound subtype reads safely as one of its base
  private static <T> Value<T> widened(Value<? extends T> converted) {
    return (Value<T>) converted;
  }
}
