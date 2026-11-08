package dev.zems.lib.common.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;

/**
 * Marks a test method as a <em>journey test</em>: it exercises a multi-class workflow, end-to-end round-trip,
 * byte-level fixture, or integration scenario across multiple production classes.
 *
 * <p>
 * Each journey test points at one issue and one acceptance bullet under {@code 95_changes/}: {@link #speakingId()} is
 * the issue file name without {@code .issue.md}, {@link #acceptance()} the {@code [id]} marker on one of its
 * {@code ## Acceptance} bullets. The issue linter checks the link in both directions. Filter with
 * {@code mvn test -Dgroups=journey}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Tag("journey")
public @interface JourneyTest {
  /** Speaking-id of the issue this test covers (filename stem of the {@code .issue.md}). */
  String speakingId();

  /** Acceptance-bullet id within that issue, e.g. {@code "a1"}. */
  String acceptance();
}
