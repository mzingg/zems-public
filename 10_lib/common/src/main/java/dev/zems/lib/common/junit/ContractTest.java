package dev.zems.lib.common.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;

/**
 * Marks a test class as a <em>contract test</em>: it pins down the behaviour of exactly one Java source class,
 * interface, or abstract base in {@code src/main}. Other production types may appear only as fixtures. Filter with
 * {@code mvn test -Dgroups=contract}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("contract")
public @interface ContractTest {}
