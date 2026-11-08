package dev.zems.lib.value.valueio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ContractTest
@DisplayName("StateWriter")
class StateWriterContractTest {

  private static final TypeDescriptor<String> STR = TypeDescriptor.of(String.class);

  static Stream<Arguments> dispatchCases() {
    return Stream.of(
      Arguments.of("NullValue → writeNull", Value.<String>nullValue(), "writeNull(0, $payload)"),
      Arguments.of("UndefinedValue → writeUndefined", Value.<String>undefined(), "writeUndefined(0, $payload)"),
      Arguments.of("UnresolvedValue → writeUnresolved", Value.<String>unresolved(), "writeUnresolved(0, $payload)"),
      Arguments.of("TombstoneValue → writeTombstone", Value.<String>tombstone(), "writeTombstone(0, $payload)"),
      Arguments.of(
        "UnsupportedValue → writeUnsupported",
        Value.unsupported("no conversion exists", String.class),
        "writeUnsupported(0, $payload, no conversion exists)"
      ),
      Arguments.of("StringValue → writeRecord", Value.of("hi"), "writeRecord(0, $payload, java.lang.String, hi)")
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("dispatchCases")
  void writeDispatchesByValueSubtype(String label, Value<String> value, String expectedEntry) {
    var w = new JournalingStateWriter();
    w.write(value, STR);
    assertThat(w.journal()).containsExactly(expectedEntry);
  }

  @Test
  @DisplayName("ErrorValue → writeError(throwable)")
  void errorValueRoutesToWriteError() {
    var w = new JournalingStateWriter();
    var cause = new IllegalStateException("boom");
    Value<String> err = Value.errorOf(cause, String.class);
    w.write(err, STR);
    assertThat(w.journal()).containsExactly("writeError(0, $payload, java.lang.IllegalStateException: boom)");
  }

  @Test
  @DisplayName("write(Value, descriptor) delegates to slot 0 / PAYLOAD_SLOT_NAME")
  void topLevelWriteUsesSlotZero() {
    var w = new JournalingStateWriter();
    w.write(Value.of("hi"), STR);
    assertThat(w.journal()).containsExactly("writeRecord(0, $payload, java.lang.String, hi)");
  }

  @Test
  @DisplayName("write(id, name, …) routes to caller-supplied slot id and name")
  void perSlotWriteUsesSuppliedIdAndName() {
    var w = new JournalingStateWriter();
    w.write(42, "field", Value.of("hi"), STR);
    assertThat(w.journal()).containsExactly("writeRecord(42, field, java.lang.String, hi)");
  }

  @Test
  @DisplayName("write(int, name=null, …) throws NPE")
  void writeRejectsNullName() {
    var w = new JournalingStateWriter();
    assertThatThrownBy(() -> w.write(0, null, Value.of("x"), STR))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("name must not be null");
  }

  @Test
  @DisplayName("write(int, name, value=null, …) throws NPE")
  void writeRejectsNullValue() {
    var w = new JournalingStateWriter();
    assertThatThrownBy(() -> w.write(0, "f", null, STR))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("value must not be null");
  }

  @Test
  @DisplayName("write(int, name, value, descriptor=null) throws NPE")
  void writeRejectsNullDescriptor() {
    var w = new JournalingStateWriter();
    assertThatThrownBy(() -> w.write(0, "f", Value.of("x"), null))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("descriptor must not be null");
  }

  @Test
  @DisplayName("PAYLOAD_SLOT_NAME constant matches the default top-level slot name")
  void valueSlotNameConstant() {
    var w = new JournalingStateWriter();
    w.write(Value.of("x"), STR);
    assertThat(w.journal().getFirst()).contains(", " + StateWriter.PAYLOAD_SLOT_NAME + ", ");
  }

  @Test
  @DisplayName("inferred write(Value) resolves descriptor via valueType() for built-in scalars")
  void inferredWriteResolvesBuiltInDescriptor() {
    var w = new JournalingStateWriter();
    w.write(Value.of("hi"));
    assertThat(w.journal()).containsExactly("writeRecord(0, $payload, java.lang.String, hi)");
  }

  @Test
  @DisplayName("inferred write(Value) routes state markers through their writeXxx methods")
  void inferredWriteRoutesStateMarkers() {
    var w = new JournalingStateWriter();
    assertThatThrownBy(() -> w.write(Value.<String>nullValue()))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("valueType() returned null")
      .hasMessageContaining("write(value, descriptor)");
  }

  @Test
  @DisplayName("inferred write(Value) rejects descriptor-less BoxedValue with a sharpened error")
  void inferredWriteRejectsBoxedValueWithoutDescriptor() {
    record OpaquePojo(String x) {}
    var w = new JournalingStateWriter();
    @SuppressWarnings({ "unchecked", "rawtypes" })
    Value<Object> bare = (Value) Value.of(new Object());
    assertThatThrownBy(() -> w.write(bare))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("valueType() returned null");
  }

  @Test
  @DisplayName("inferred write(Value) rejects null value")
  void inferredWriteRejectsNull() {
    var w = new JournalingStateWriter();
    assertThatThrownBy(() -> w.write((Value<?>) null))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("value must not be null");
  }
}
