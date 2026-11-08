package dev.zems.lib.value.valueio.descriptor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Earlier wire names of an enum constant, accepted on read; the constant is always written under its current name.
 *
 * <p>
 * A blank alias, or a wire name claimed by two constants, is refused when the enum's descriptor is synthesised.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EnumAlias {
  /** The earlier names. */
  String[] value();
}
