package dev.zems.lib.value;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.JourneyTest;
import dev.zems.lib.value.convert.Converter;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UnsupportedValue — a refusal about the type pair or the operation, not the data")
class UnsupportedValueJourneyTest {

  private static final String UUID_TO_INTEGER = "Cannot convert UUID to Integer: no conversion exists";

  @Test
  @JourneyTest(speakingId = "unsupported-value-state", acceptance = "a1")
  @DisplayName("the factory answers an UnsupportedValue carrying the reason and the expected-type descriptor")
  void factoryCarriesReasonAndExpectedType() {
    Value<Integer> marker = Value.unsupported(UUID_TO_INTEGER, Integer.class);

    assertThat(marker).isInstanceOf(UnsupportedValue.class);
    assertThat(((UnsupportedValue<Integer>) marker).reason()).isEqualTo(UUID_TO_INTEGER);
    assertThat(((UnsupportedValue<Integer>) marker).expectedType().qualifiedName()).isEqualTo("java.lang.Integer");
    assertThat(marker.valueType().qualifiedName()).isEqualTo("java.lang.Integer");
  }

  @Test
  @JourneyTest(speakingId = "unsupported-value-state", acceptance = "a1")
  @DisplayName("equality is by reason and expected type — a different reason or a different type is a different value")
  void equalityIsByReasonAndExpectedType() {
    Value<Integer> marker = Value.unsupported(UUID_TO_INTEGER, Integer.class);
    Value<Integer> sameReasonSameType = Value.unsupported(UUID_TO_INTEGER, Integer.class);
    Value<Integer> otherReason = Value.unsupported("Cannot convert UUID to Integer: not whole", Integer.class);
    Value<Long> otherType = Value.unsupported(UUID_TO_INTEGER, Long.class);

    assertThat(marker).isEqualTo(sameReasonSameType);
    assertThat(marker.hashCode()).isEqualTo(sameReasonSameType.hashCode());
    assertThat(marker).isNotEqualTo(otherReason);
    assertThat(marker).isNotEqualTo(otherType);
  }

  @Test
  @JourneyTest(speakingId = "unsupported-value-state", acceptance = "a1")
  @DisplayName("the Class overload and the TypeDescriptor overload build the same value")
  void classAndDescriptorFactoriesAgree() {
    Value<Integer> fromClass = Value.unsupported(UUID_TO_INTEGER, Integer.class);
    Value<Integer> fromDescriptor = Value.unsupported(
      UUID_TO_INTEGER,
      TypeDescriptor.of("java.lang.Integer", Integer.class)
    );

    assertThat(fromClass).isEqualTo(fromDescriptor);
  }

  @Test
  @JourneyTest(speakingId = "unsupported-value-state", acceptance = "a2")
  @DisplayName("isUnsupported() claims it and no other state check does")
  void onlyIsUnsupportedClaimsTheMarker() {
    Value<Integer> marker = Value.unsupported(UUID_TO_INTEGER, Integer.class);

    assertThat(marker.isUnsupported()).as("isUnsupported").isTrue();
    assertThat(marker.isStateMarker()).as("isStateMarker").isTrue();
    assertThat(marker.isAbsent()).as("isAbsent").isTrue();

    assertThat(marker.isSupported()).as("isSupported").isFalse();
    assertThat(marker.isPresent()).as("isPresent").isFalse();
    assertThat(marker.isError()).as("isError").isFalse();
    assertThat(marker.isNull()).as("isNull").isFalse();
    assertThat(marker.isUndefined()).as("isUndefined").isFalse();
    assertThat(marker.isUnresolved()).as("isUnresolved").isFalse();
    assertThat(marker.isTombstone()).as("isTombstone").isFalse();

    assertThat(marker.isNotError()).as("isNotError").isTrue();
    assertThat(marker.isNotNull()).as("isNotNull").isTrue();
    assertThat(marker.isDefined()).as("isDefined").isTrue();
    assertThat(marker.isResolved()).as("isResolved").isTrue();
    assertThat(marker.isNotTombstone()).as("isNotTombstone").isTrue();
  }

  @Test
  @JourneyTest(speakingId = "unsupported-value-state", acceptance = "a2")
  @DisplayName("unboxing refuses it the way it refuses every other state marker")
  void unboxingRefusesTheMarker() {
    Value<Integer> marker = Value.unsupported(UUID_TO_INTEGER, Integer.class);

    assertThatThrownBy(() -> marker.unbox())
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Cannot unbox state-marker value UnsupportedValue");
  }

  @Test
  @JourneyTest(speakingId = "unsupported-value-state", acceptance = "a3")
  @DisplayName("map, flatMap and filter propagate it by identity and never call the caller's function")
  void mapFlatMapAndFilterPropagateByIdentity() {
    Value<Integer> marker = Value.unsupported(UUID_TO_INTEGER, Integer.class);
    AtomicInteger calls = new AtomicInteger();

    Value<String> mapped = marker.map(number -> {
      calls.incrementAndGet();
      return "mapped";
    });
    Value<String> flatMapped = marker.flatMap(number -> {
      calls.incrementAndGet();
      return Value.of("flat");
    });
    Value<Integer> filtered = marker.filter(number -> {
      calls.incrementAndGet();
      return false;
    });

    assertThat(mapped).isSameAs(marker);
    assertThat(flatMapped).isSameAs(marker);
    assertThat(filtered).isSameAs(marker);
    assertThat(calls).hasValue(0);
  }

  @Test
  @JourneyTest(speakingId = "unsupported-value-state", acceptance = "a3")
  @DisplayName("or fires the supplier, so another path answers in the same chain")
  void orFiresTheSupplier() {
    Value<Integer> marker = Value.unsupported(UUID_TO_INTEGER, Integer.class);
    Value<Integer> alternative = Value.of(7);

    assertThat(marker.or(() -> alternative)).isSameAs(alternative);
  }

  @Test
  @JourneyTest(speakingId = "unsupported-value-state", acceptance = "a3")
  @DisplayName("recover replaces it with the fallback, as it does for an error")
  void recoverReplacesTheMarker() {
    Value<Integer> marker = Value.unsupported(UUID_TO_INTEGER, Integer.class);

    assertThat(marker.recover(0)).hasIntValue(0);
  }

  @Test
  @JourneyTest(speakingId = "unsupported-value-state", acceptance = "a3")
  @DisplayName("recoverWith hands the function an UnsupportedOperationException carrying the reason")
  void recoverWithHandsOverAnUnsupportedOperationException() {
    Value<Integer> marker = Value.unsupported(UUID_TO_INTEGER, Integer.class);
    AtomicReference<Throwable> seen = new AtomicReference<>();

    Value<Integer> recovered = marker.recoverWith(throwable -> {
      seen.set(throwable);
      return Value.of(42);
    });

    assertThat(recovered).hasIntValue(42);
    assertThat(seen.get()).isInstanceOf(UnsupportedOperationException.class).hasMessage(UUID_TO_INTEGER).hasNoCause();
  }

  @Test
  @JourneyTest(speakingId = "unsupported-value-state", acceptance = "a3")
  @DisplayName("orElseThrow throws an UnsupportedOperationException whose message is the reason")
  void orElseThrowThrowsUnsupportedOperationException() {
    Value<Integer> marker = Value.unsupported(UUID_TO_INTEGER, Integer.class);

    assertThatThrownBy(marker::orElseThrow)
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage(UUID_TO_INTEGER);
  }

  @Test
  @JourneyTest(speakingId = "unsupported-value-state", acceptance = "a3")
  @DisplayName("a caller falls back to another path in one chain: convert, then recover")
  void conversionRefusalRecoversInOneChain() {
    Value<Integer> fallback = Value.of(UUID.randomUUID()).convertTo(Integer.class).recover(0);

    assertThat(fallback).hasIntValue(0);
  }

  @Test
  @JourneyTest(speakingId = "unsupported-value-state", acceptance = "a5")
  @DisplayName("a type pair with no conversion answers an unsupported value carrying the refusal frame")
  void typePairRefusalIsUnsupported() {
    Value<Integer> refused = Value.of(UUID.randomUUID()).convertTo(Integer.class);

    assertThat(refused).isUnsupported().hasUnsupportedReason(UUID_TO_INTEGER).hasExpectedTypeName("java.lang.Integer");
  }

  @Test
  @JourneyTest(speakingId = "unsupported-value-state", acceptance = "a5")
  @DisplayName("a refusal about this value stays an error, with the same frame wording")
  void perValueRefusalStaysAnError() {
    Value<Integer> refused = Value.of(5_000_000_000L).convertTo(Integer.class);

    assertThat(refused)
      .isError()
      .hasErrorMessage("Cannot convert Long to Integer: 5000000000 overflows Integer")
      .hasExpectedTypeName("java.lang.Integer");
  }

  @Test
  @JourneyTest(speakingId = "unsupported-value-state", acceptance = "a5")
  @DisplayName("the converter draws the same line as the Value surface does")
  void converterDrawsTheSameLine() {
    Converter converter = Converter.standard();

    Value<Integer> typePairRefusal = converter.convert(Value.of(UUID.randomUUID()), Integer.class);
    Value<Integer> perValueRefusal = converter.convert(Value.of(5_000_000_000L), Integer.class);

    assertThat(typePairRefusal).isUnsupported().hasUnsupportedReason(UUID_TO_INTEGER);
    assertThat(perValueRefusal)
      .isError()
      .hasErrorMessage("Cannot convert Long to Integer: 5000000000 overflows Integer");
  }
}
