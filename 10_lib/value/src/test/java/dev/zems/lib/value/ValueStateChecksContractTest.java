package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.ContractTest;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ContractTest
@DisplayName("ValueStateChecks")
class ValueStateChecksContractTest {

  private static final Value<String> NULL = Value.nullValue();
  private static final Value<String> UNDEFINED = Value.undefined();
  private static final Value<String> UNRESOLVED = Value.unresolved();
  private static final Value<String> ERROR = Value.errorMessage("boom", String.class);
  private static final Value<String> TOMBSTONE = Value.tombstone();
  private static final Value<String> PRESENT = Value.of("hello");

  private static final List<Value<?>> ALL_MARKERS = List.of(NULL, UNDEFINED, UNRESOLVED, ERROR, TOMBSTONE);

  @Nested
  @DisplayName("Per-marker predicates")
  class PerMarker {

    @Test
    void isNullTrueOnlyForNullValue() {
      assertThat(NULL.isNull()).isTrue();
      assertThat(NULL.isNotNull()).isFalse();
      Stream.of(UNDEFINED, UNRESOLVED, ERROR, TOMBSTONE, PRESENT).forEach(v -> {
        assertThat(v.isNull()).as("%s.isNull", v).isFalse();
        assertThat(v.isNotNull()).as("%s.isNotNull", v).isTrue();
      });
    }

    @Test
    void isUndefinedTrueOnlyForUndefinedValue() {
      assertThat(UNDEFINED.isUndefined()).isTrue();
      assertThat(UNDEFINED.isDefined()).isFalse();
      Stream.of(NULL, UNRESOLVED, ERROR, TOMBSTONE, PRESENT).forEach(v -> {
        assertThat(v.isUndefined()).as("%s.isUndefined", v).isFalse();
        assertThat(v.isDefined()).as("%s.isDefined", v).isTrue();
      });
    }

    @Test
    void isUnresolvedTrueOnlyForUnresolvedValue() {
      assertThat(UNRESOLVED.isUnresolved()).isTrue();
      assertThat(UNRESOLVED.isResolved()).isFalse();
      Stream.of(NULL, UNDEFINED, ERROR, TOMBSTONE, PRESENT).forEach(v -> {
        assertThat(v.isUnresolved()).as("%s.isUnresolved", v).isFalse();
        assertThat(v.isResolved()).as("%s.isResolved", v).isTrue();
      });
    }

    @Test
    void isErrorTrueOnlyForErrorValue() {
      assertThat(ERROR.isError()).isTrue();
      assertThat(ERROR.isNotError()).isFalse();
      Stream.of(NULL, UNDEFINED, UNRESOLVED, TOMBSTONE, PRESENT).forEach(v -> {
        assertThat(v.isError()).as("%s.isError", v).isFalse();
        assertThat(v.isNotError()).as("%s.isNotError", v).isTrue();
      });
    }

    @Test
    void isTombstoneTrueOnlyForTombstoneValue() {
      assertThat(TOMBSTONE.isTombstone()).isTrue();
      assertThat(TOMBSTONE.isNotTombstone()).isFalse();
      Stream.of(NULL, UNDEFINED, UNRESOLVED, ERROR, PRESENT).forEach(v -> {
        assertThat(v.isTombstone()).as("%s.isTombstone", v).isFalse();
        assertThat(v.isNotTombstone()).as("%s.isNotTombstone", v).isTrue();
      });
    }
  }

  @Nested
  @DisplayName("Composite predicates")
  class Composite {

    static Stream<Arguments> everyValue() {
      return Stream.of(NULL, UNDEFINED, UNRESOLVED, ERROR, TOMBSTONE, PRESENT).map(Arguments::of);
    }

    @Test
    void isPresentTrueOnlyForRealValues() {
      assertThat(PRESENT.isPresent()).isTrue();
      assertThat(PRESENT.isAbsent()).isFalse();
      ALL_MARKERS.forEach(v -> {
        assertThat(v.isPresent()).as("%s.isPresent", v).isFalse();
        assertThat(v.isAbsent()).as("%s.isAbsent", v).isTrue();
      });
    }

    @Test
    void isStateMarkerTrueForEveryMarker() {
      ALL_MARKERS.forEach(v -> assertThat(v.isStateMarker()).as("%s.isStateMarker", v).isTrue());
      assertThat(PRESENT.isStateMarker()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("everyValue")
    @DisplayName("isAbsent is the exact complement of isStateMarker")
    void absentEquivalentToStateMarker(Value<?> v) {
      assertThat(v.isAbsent()).isEqualTo(v.isStateMarker());
    }
  }
}
