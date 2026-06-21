package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.ContractTest;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("ValueErrorHandling")
class ValueErrorHandlingContractTest {

  private static final Value<String> NULL = Value.nullValue();
  private static final Value<String> UNDEFINED = Value.undefined();
  private static final Value<String> UNRESOLVED = Value.unresolved();
  private static final Value<String> ERROR = Value.errorMessage("boom", String.class);
  private static final Value<String> TOMBSTONE = Value.tombstone();
  private static final Value<String> PRESENT = Value.of("hello");

  @Test
  void errorPopulatedOnlyForErrorValue() {
    assertThat(ERROR.error()).get().extracting(Throwable::getMessage).isEqualTo("boom");
    Stream.of(NULL, UNDEFINED, UNRESOLVED, TOMBSTONE, PRESENT).forEach(v ->
      assertThat(v.error()).as("%s.error", v).isEmpty()
    );
  }

  @Test
  void errorMessagePopulatedOnlyForErrorValue() {
    assertThat(ERROR.errorMessage()).hasValue("boom");
    Stream.of(NULL, UNDEFINED, UNRESOLVED, TOMBSTONE, PRESENT).forEach(v ->
      assertThat(v.errorMessage()).as("%s.errorMessage", v).isEmpty()
    );
  }
}
