package dev.zems.lib.common._test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;

/**
 * Marks a test method as a <em>journey test</em>: it exercises a multi-class workflow, end-to-end round-trip,
 * byte-level fixture, or integration scenario across multiple production classes.
 *
 * <p>Each journey test must point at a specific issue and acceptance bullet under {@code 95_changes/}:
 * {@link #speakingId()} names the issue file (without the {@code .issue.md} suffix) and {@link #acceptance()} names
 * a stable {@code [id]} marker on one of that issue's {@code ## Acceptance} bullets. The
 * {@code check_journey_coverage.py} script enforces the link in both directions.
 *
 * <p>Filter via Maven Surefire: {@code mvn test -Dgroups=journey}. See the project rules in
 * {@code .claude/rules/java.md} for the contract/journey distinction.
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
