package dev.zems.lib.value.junit;

import dev.zems.lib.value.Value;
import org.assertj.core.api.Assertions;

/**
 * Entry point for {@link Value} assertions. Extends AssertJ's {@link Assertions}, so one static import of
 * {@code ValueAssertions.assertThat} gives a test both {@link #assertThat(Value)} and every standard overload.
 */
public class ValueAssertions extends Assertions {

  protected ValueAssertions() {
    // Protected rather than private so a module can extend it, as AssertJ's own Assertions allows.
  }

  /** Starts a fluent assertion on a {@link Value}. */
  public static ValueAssert assertThat(Value<?> actual) {
    return new ValueAssert(actual);
  }
}
