package dev.zems.lib.value.convert;

import dev.zems.lib.value.SlotShape;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** Finds the {@link SlotShape} a type declares as {@code public static final SLOTS}. */
final class SlotShapes {

  private static final String FIELD_NAME = "SLOTS";

  private SlotShapes() {}

  static SlotShape<?> forTargetOrNull(Class<?> target) {
    SlotShape<?> shape = declaredOrNull(target);
    // A subtype named as target must not pick up its supertype's shape, whose build may answer any subtype.
    return shape != null && target.isAssignableFrom(shape.type()) ? shape : null;
  }

  static SlotShape<?> forPayloadOrNull(Object payload) {
    SlotShape<?> shape = declaredOrNull(payload.getClass());
    return shape != null && shape.type().isInstance(payload) ? shape : null;
  }

  private static SlotShape<?> declaredOrNull(Class<?> type) {
    Field field;
    try {
      field = type.getField(FIELD_NAME);
    } catch (NoSuchFieldException ignored) {
      return null;
    }
    if (!Modifier.isStatic(field.getModifiers())) {
      return null;
    }
    if (!SlotShape.class.isAssignableFrom(field.getType())) {
      // Answering null here would hide a misdeclared constant behind a plain "no conversion exists".
      throw new IllegalStateException(
        type.getName() +
          "." +
          FIELD_NAME +
          " is declared as " +
          field.getType().getName() +
          " which is not a SlotShape — declare it as SlotShape or rename the field"
      );
    }
    Class<?> declaringClass = field.getDeclaringClass();
    Object held;
    try {
      held = MethodHandles.publicLookup().findStaticGetter(declaringClass, FIELD_NAME, field.getType()).invoke();
    } catch (NoSuchFieldException | IllegalAccessException ignored) {
      return null;
    } catch (Error e) {
      throw e;
    } catch (Throwable ignored) {
      return null;
    }
    if (held == null) {
      throw new IllegalStateException(declaringClass.getName() + "." + FIELD_NAME + " holds null");
    }
    return (SlotShape<?>) held;
  }
}
