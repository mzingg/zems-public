package dev.zems.lib.value;

import org.assertj.core.api.Assertions;

/**
 * Entry point for {@link Value} assertions. Extends AssertJ's {@link Assertions} so a single static import —
 * {@code import static dev.zems.lib.value.ValueAssertions.assertThat;} — gives a test both {@link #assertThat(Value)}
 * and every standard AssertJ {@code assertThat} overload, with no ambiguity (AssertJ has no {@code assertThat(Object)}
 * catch-all, and {@code Value} is not one of its specialised types).
 */
public class ValueAssertions extends Assertions {

  protected ValueAssertions() {
    // entry point: use the static factory; ctor is protected to match AssertJ's own Assertions.
  }

  /** Starts a fluent assertion on a {@link Value}. */
  public static ValueAssert assertThat(Value<?> actual) {
    return new ValueAssert(actual);
  }
}
