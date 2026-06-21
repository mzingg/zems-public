package dev.zems.lib.value.marshal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.JourneyTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * End-to-end round-trip for {@link dev.zems.lib.value.builtin.SetValue} through every wire format
 * ({@code ValueIo.framed(...)}). Verifies that the descriptor / state-reader-writer stack handles the new
 * collection type identically to {@link dev.zems.lib.value.builtin.ListValue}.
 */
@DisplayName("SetValue round-trip across all wire formats")
class SetValueJourneyTest {

  static Stream<RoundTripFormatHarness.Format> formats() {
    return RoundTripFormatHarness.all();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "set-and-sortedmap-value-first-class", acceptance = "a1")
  @DisplayName("round-trips a non-empty SetValue<String>")
  void roundTripsSetOfStrings(RoundTripFormatHarness.Format format) {
    TypeDescriptor<Set<Value<String>>> descriptor = TypeDescriptor.ofSet(
      "test.set.string",
      BuiltinTypeDescriptors.STRING
    );
    Value<Set<Value<String>>> original = Value.setOf("a", "b", "c");

    byte[] wire = format.write(original, descriptor);
    Value<Set<Value<String>>> reread = format.read(wire, descriptor);

    assertThat(Value.unbox(reread)).containsExactlyInAnyOrder(Value.of("a"), Value.of("b"), Value.of("c"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "set-and-sortedmap-value-first-class", acceptance = "a2")
  @DisplayName("round-trips an empty SetValue<String>")
  void roundTripsEmptySet(RoundTripFormatHarness.Format format) {
    TypeDescriptor<Set<Value<String>>> descriptor = TypeDescriptor.ofSet(
      "test.set.empty",
      BuiltinTypeDescriptors.STRING
    );
    Value<Set<Value<String>>> original = Value.emptySet();

    byte[] wire = format.write(original, descriptor);
    Value<Set<Value<String>>> reread = format.read(wire, descriptor);

    assertThat(Value.unbox(reread)).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "set-and-sortedmap-value-first-class", acceptance = "a3")
  @DisplayName("round-trips a SetValue<Integer>")
  void roundTripsSetOfInts(RoundTripFormatHarness.Format format) {
    TypeDescriptor<Set<Value<Integer>>> descriptor = TypeDescriptor.ofSet(
      "test.set.int",
      BuiltinTypeDescriptors.INTEGER
    );
    Value<Set<Value<Integer>>> original = Value.setOf(1, 2, 3);

    byte[] wire = format.write(original, descriptor);
    Value<Set<Value<Integer>>> reread = format.read(wire, descriptor);

    assertThat(Value.unbox(reread)).containsExactlyInAnyOrder(Value.of(1), Value.of(2), Value.of(3));
  }
}
