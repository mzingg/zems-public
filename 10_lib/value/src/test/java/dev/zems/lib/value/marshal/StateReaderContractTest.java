package dev.zems.lib.value.marshal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.ErrorValue;
import dev.zems.lib.value.NullValue;
import dev.zems.lib.value.TombstoneValue;
import dev.zems.lib.value.UndefinedValue;
import dev.zems.lib.value.UnresolvedValue;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.ValueState;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import dev.zems.lib.value.marshal.wire.WireConstraints;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Contract test for the {@link StateReader} interface — pins the default-method dispatch:
 * {@link StateReader#read(int, String, TypeDescriptor)} routes state markers exposed by {@code peekValueStateOrNull} to
 * {@link Value#nullValue()} / {@link Value#undefined()} / {@link Value#unresolved()} /
 * {@link Value#errorOf(Throwable, TypeDescriptor)} / {@link Value#tombstone()} or falls through to {@code readRecord},
 * the {@code *Or} convenience defaults dispatch on {@link StateReader#hasField(int, String)}, and
 * {@link StateReader#wireConstraints()} returns SECURE_DEFAULTS by default.
 *
 * <p>
 * Concrete reader classes (NoOpStateReader, JournalingStateReader, CombinedStateReader, BinaryStateReader, …) carry
 * implementation-specific behaviour; their per-class behaviour lives in dedicated tests.
 */
@ContractTest
@DisplayName("StateReader")
class StateReaderContractTest {

  private static final TypeDescriptor<String> STR = TypeDescriptor.of(String.class);

  static Stream<OrCase<?>> orVariants() {
    return Stream.of(
      new OrCase<>("readBooleanOr", r -> r.booleanResult = true, r -> r.readBooleanOr(0, "f", false), true, false),
      new OrCase<>("readCharOr", r -> r.charResult = 'X', r -> r.readCharOr(0, "f", '?'), 'X', '?'),
      new OrCase<>(
        "readShortOr",
        r -> r.shortResult = (short) 42,
        r -> r.readShortOr(0, "f", (short) 99),
        (short) 42,
        (short) 99
      ),
      new OrCase<>("readIntOr", r -> r.intResult = 42, r -> r.readIntOr(0, "f", 99), 42, 99),
      new OrCase<>("readLongOr", r -> r.longResult = 42L, r -> r.readLongOr(0, "f", 99L), 42L, 99L),
      new OrCase<>("readFloatOr", r -> r.floatResult = 1.5f, r -> r.readFloatOr(0, "f", 9.5f), 1.5f, 9.5f),
      new OrCase<>("readDoubleOr", r -> r.doubleResult = 1.5d, r -> r.readDoubleOr(0, "f", 9.5d), 1.5d, 9.5d),
      new OrCase<>(
        "readStringOr",
        r -> r.stringResult = "payload",
        r -> r.readStringOr(0, "f", "fallback"),
        "payload",
        "fallback"
      ),
      new OrCase<>(
        "readBytesOr",
        r -> r.bytesResult = new byte[] { 1, 2, 3 },
        r -> r.readBytesOr(0, "f", new byte[] { 9, 9 }),
        new byte[] { 1, 2, 3 },
        new byte[] { 9, 9 }
      ),
      new OrCase<>(
        "readRecordOr",
        r -> r.recordResult = "payload",
        r -> r.readRecordOr(0, "f", STR, "fallback"),
        "payload",
        "fallback"
      )
    );
  }

  @Test
  @DisplayName("read(...) returns NullValue when peekValueStateOrNull reports NULL")
  void readDispatchesNull() {
    var r = new StubReader(ValueState.NULL);
    Value<String> result = r.read(0, "v", STR);
    assertThat(result).isInstanceOf(NullValue.class);
  }

  @Test
  @DisplayName("read(...) returns UndefinedValue when peekValueStateOrNull reports UNDEFINED")
  void readDispatchesUndefined() {
    var r = new StubReader(ValueState.UNDEFINED);
    Value<String> result = r.read(0, "v", STR);
    assertThat(result).isInstanceOf(UndefinedValue.class);
  }

  @Test
  @DisplayName("read(...) returns UnresolvedValue when peekValueStateOrNull reports UNRESOLVED")
  void readDispatchesUnresolved() {
    var r = new StubReader(ValueState.UNRESOLVED);
    Value<String> result = r.read(0, "v", STR);
    assertThat(result).isInstanceOf(UnresolvedValue.class);
  }

  @Test
  @DisplayName("read(...) returns TombstoneValue when peekValueStateOrNull reports TOMBSTONE")
  void readDispatchesTombstone() {
    var r = new StubReader(ValueState.TOMBSTONE);
    Value<String> result = r.read(0, "v", STR);
    assertThat(result).isInstanceOf(TombstoneValue.class);
  }

  @Test
  @DisplayName("read(...) returns ErrorValue when peekValueStateOrNull reports ERROR; readError is invoked")
  void readDispatchesError() {
    var cause = new IllegalStateException("boom");
    var r = new StubReader(ValueState.ERROR, null, cause);
    Value<String> result = r.read(0, "v", STR);
    assertThat(result).isInstanceOf(ErrorValue.class);
    assertThat(result.error()).hasValue(cause);
  }

  @Test
  @DisplayName("read(...) falls through to readRecord when no state marker is present")
  void readFallsThroughToReadRecord() {
    var r = new StubReader(null, "payload", null);
    Value<String> result = r.read(0, "v", STR);
    assertThat(result.asString()).hasValue("payload");
  }

  @Test
  @DisplayName("read(TypeDescriptor) delegates to slot 0 / PAYLOAD_SLOT_NAME")
  void topLevelReadUsesSlotZero() {
    var r = new StubReader(null, "payload", null);
    Value<String> result = r.read(STR);
    assertThat(result.asString()).hasValue("payload");
    assertThat(r.lastReadId).isZero();
    assertThat(r.lastReadName).isEqualTo(StateWriter.PAYLOAD_SLOT_NAME);
  }

  @Test
  @DisplayName("read(Class) looks up TypeDescriptor.find and delegates")
  void readByClassResolvesDescriptor() {
    var r = new StubReader(null, "payload", null);
    Value<String> result = r.read(String.class);
    assertThat(result.asString()).hasValue("payload");
  }

  @Test
  @DisplayName("read(Class) throws ISE when no descriptor is registered")
  void readByUnregisteredClassThrows() {
    var r = new StubReader(null, null, null);
    // Plain (non-record, non-builtin, no DESCRIPTOR field) class — TypeDescriptor.find
    // returns empty, so read(Class) raises ISE.
    assertThatThrownBy(() -> r.read(Thread.class))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("No TypeDescriptor registered");
  }

  // ============ *Or convenience defaults ============

  @Test
  @DisplayName("read rejects null name / null descriptor")
  void readRejectsNulls() {
    var r = new StubReader(null, null, null);
    assertThatThrownBy(() -> r.read(0, null, STR))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("name must not be null");
    assertThatThrownBy(() -> r.read(0, "v", null))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("descriptor must not be null");
    assertThatThrownBy(() -> r.read((Class<String>) null))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("clazz must not be null");
  }

  /**
   * Parameterised dispatch test covering every {@code read*Or} default. Production code only exercises
   * {@code readStringOr} (with {@code readBooleanOr} reachable through one fixture), so the rest of the family has no
   * organic call sites — this is the single point that pins their hasField/default contract.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("orVariants")
  @DisplayName("read*Or(...) returns underlying value when hasField is true, default otherwise")
  @SuppressWarnings({ "unchecked", "rawtypes" })
  void readOrDispatchesOnHasField(OrCase variant) {
    var r = new StubReader(null, null, null);
    variant.setup().accept(r);

    r.hasFieldResult = true;
    assertThat(variant.invoke().apply(r)).isEqualTo(variant.expectedFromUnderlying());

    r.hasFieldResult = false;
    assertThat(variant.invoke().apply(r)).isEqualTo(variant.expectedDefault());
  }

  @Test
  @DisplayName("wireConstraints() defaults to SECURE_DEFAULTS")
  void wireConstraintsDefault() {
    var r = new StubReader(null, null, null);
    assertThat(r.wireConstraints()).isSameAs(WireConstraints.SECURE_DEFAULTS);
  }

  // ============ wireConstraints / recordSignature defaults ============

  @Test
  @DisplayName("recordSignature() defaults to empty string")
  void recordSignatureDefault() {
    var r = new StubReader(null, null, null);
    assertThat(r.recordSignature()).isEmpty();
  }

  /**
   * Test case for the {@link #readOrDispatchesOnHasField} parameterisation. {@code toString} is the parameterised
   * display name.
   */
  private record OrCase<T>(
    String label,
    Consumer<StubReader> setup,
    Function<StubReader, T> invoke,
    T expectedFromUnderlying,
    T expectedDefault
  ) {
    @Override
    public String toString() {
      return label;
    }
  }

  // ============ Test stub ============

  /**
   * Minimal StateReader stub — implements only what's needed to drive default-method dispatch. Records last read
   * id+name; returns configured peekValueStateOrNull / readRecord / readError / hasField / readInt; everything else
   * returns the zero-value.
   */
  private static final class StubReader implements StateReader {

    private final ValueState peekState;
    Object recordResult;
    Throwable errorResult;
    boolean hasFieldResult;
    boolean booleanResult;
    char charResult;
    short shortResult;
    int intResult;
    long longResult;
    float floatResult;
    double doubleResult;
    String stringResult;
    byte[] bytesResult = new byte[0];
    int lastReadId;
    String lastReadName;

    StubReader(ValueState peekState) {
      this(peekState, null, null);
    }

    StubReader(ValueState peekState, Object recordResult, Throwable errorResult) {
      this.peekState = peekState;
      this.recordResult = recordResult;
      this.errorResult = errorResult;
    }

    @Override
    public boolean hasField(int id, String name) {
      return hasFieldResult;
    }

    @Override
    public boolean readBoolean(int id, String name) {
      return booleanResult;
    }

    @Override
    public char readChar(int id, String name) {
      return charResult;
    }

    @Override
    public short readShort(int id, String name) {
      return shortResult;
    }

    @Override
    public int readInt(int id, String name) {
      return intResult;
    }

    @Override
    public long readLong(int id, String name) {
      return longResult;
    }

    @Override
    public float readFloat(int id, String name) {
      return floatResult;
    }

    @Override
    public double readDouble(int id, String name) {
      return doubleResult;
    }

    @Override
    public String readString(int id, String name) {
      return stringResult;
    }

    @Override
    public byte[] readBytes(int id, String name) {
      return bytesResult;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T readRecord(int id, String name, TypeDescriptor<T> descriptor) {
      lastReadId = id;
      lastReadName = name;
      return (T) recordResult;
    }

    @Override
    public <H> H readHeader(TypeDescriptor<H> descriptor) {
      return null;
    }

    @Override
    public <F> F readTerminator(TypeDescriptor<F> descriptor) {
      return null;
    }

    @Override
    public void beginNested(int id, String name) {}

    @Override
    public void endNested(int id, String name) {}

    @Override
    public ValueState peekValueStateOrNull(int id, String name) {
      lastReadId = id;
      lastReadName = name;
      return peekState;
    }

    @Override
    public Throwable readError(int id, String name) {
      return errorResult;
    }

    @Override
    public void close() {}
  }
}
