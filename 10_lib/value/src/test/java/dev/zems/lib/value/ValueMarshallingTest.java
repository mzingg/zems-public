package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.JourneyTest;
import dev.zems.lib.value.marshal.Protocol;
import dev.zems.lib.value.marshal.SerializedThrowable;
import dev.zems.lib.value.marshal.ValueIo;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Value.writeTo / Value.read round-trip")
class ValueMarshallingTest {

  private static final String FRAME = "test";

  private static <T> Value<T> roundTrip(Value<T> value, Class<T> clazz) {
    return roundTrip(value, clazz, false);
  }

  private static <T> Value<T> roundTrip(Value<T> value, Class<T> clazz, boolean typeVerification) {
    var bos = new ByteArrayOutputStream();
    var protocol = typeVerification ? ValueIo.framed().usingTypeVerification() : ValueIo.framed();
    var descriptor = TypeDescriptor.of(clazz);
    try (var sw = protocol.binaryWriter(bos)) {
      sw.write(value, descriptor);
    }
    try (var sr = protocol.binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
      return sr.read(clazz);
    }
  }

  @Nested
  @DisplayName("State values")
  class StateValues {

    @Test
    @JourneyTest(speakingId = "protocol-v1-framed-binary-state-roundtrips", acceptance = "a1")
    void nullValueRoundTrips() {
      Value<String> v = roundTrip(Value.nullValue(), String.class);
      assertThat(v).isInstanceOf(NullValue.class);
    }

    @Test
    @JourneyTest(speakingId = "protocol-v1-framed-binary-state-roundtrips", acceptance = "a2")
    void undefinedRoundTrips() {
      Value<String> v = roundTrip(Value.undefined(), String.class);
      assertThat(v).isInstanceOf(UndefinedValue.class);
    }

    @Test
    @JourneyTest(speakingId = "protocol-v1-framed-binary-state-roundtrips", acceptance = "a3")
    void unresolvedRoundTrips() {
      Value<String> v = roundTrip(Value.unresolved(), String.class);
      assertThat(v).isInstanceOf(UnresolvedValue.class);
    }

    @Test
    @JourneyTest(speakingId = "protocol-v1-framed-binary-state-roundtrips", acceptance = "a4")
    void errorRoundTripsWithThrowableInfo() {
      Value<String> original = Value.errorOf(new IOException("boom"), String.class);
      Value<String> v = roundTrip(original, String.class);
      assertThat(v).isInstanceOf(ErrorValue.class);
      Throwable t = ((ErrorValue<String>) v).throwable();
      assertThat(t).isInstanceOf(SerializedThrowable.class).hasMessage("boom");
      assertThat(((SerializedThrowable) t).originalClassName()).isEqualTo("java.io.IOException");
    }
  }

  @Nested
  @DisplayName("BuiltIn typed values")
  class TypedValues {

    @Test
    @JourneyTest(speakingId = "protocol-v1-framed-binary-scalar-roundtrips", acceptance = "a1")
    void stringRoundTrips() {
      Value<String> v = roundTrip(Value.of("hello"), String.class);
      assertThat(v.asString()).contains("hello");
    }

    @Test
    @JourneyTest(speakingId = "protocol-v1-framed-binary-scalar-roundtrips", acceptance = "a2")
    void integerRoundTrips() {
      Value<Integer> v = roundTrip(Value.of(42), Integer.class);
      assertThat(v.asInteger()).contains(42);
    }

    @Test
    @JourneyTest(speakingId = "protocol-v1-framed-binary-scalar-roundtrips", acceptance = "a3")
    void uuidRoundTrips() {
      var uuid = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
      Value<UUID> v = roundTrip(Value.of(uuid), UUID.class);
      assertThat(v.asUuid()).contains(uuid);
    }
  }

  @Nested
  @DisplayName("Type verification")
  class TypeVerification {

    @Test
    @JourneyTest(speakingId = "protocol-v1-type-verification", acceptance = "a1")
    void roundTripsWithVerifiedMode() {
      Value<String> v = roundTrip(Value.of("hello"), String.class, true);
      assertThat(v.asString()).contains("hello");
    }

    @Test
    @JourneyTest(speakingId = "protocol-v1-type-verification", acceptance = "a2")
    void mismatchedTypeThrows() {
      var bos = new ByteArrayOutputStream();
      try (var sw = ValueIo.framed().usingTypeVerification().binaryWriter(bos)) {
        sw.write(Value.of(123), TypeDescriptor.of(Integer.class));
      }
      try (
        var sr = ValueIo.framed().usingTypeVerification().binaryReader(new ByteArrayInputStream(bos.toByteArray()))
      ) {
        assertThatThrownBy(() -> sr.read(String.class))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Type mismatch")
          .hasMessageContaining("java.lang.String");
      } catch (RuntimeException closeError) {
        // Close-time terminator failure is downstream of the body throw — expected.
      }
    }
  }
}
