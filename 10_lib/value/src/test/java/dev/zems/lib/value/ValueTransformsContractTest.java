package dev.zems.lib.value;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ContractTest
@DisplayName("ValueTransforms")
class ValueTransformsContractTest {

  private static final Value<String> PRESENT = Value.of("hello");
  private static final Value<String> NULL_MARKER = Value.nullValue();
  private static final Value<String> UNDEFINED_MARKER = Value.undefined();
  private static final Value<String> UNRESOLVED_MARKER = Value.unresolved();
  private static final Value<String> ERROR_MARKER = Value.errorMessage("boom", String.class);
  private static final Value<String> TOMBSTONE_MARKER = Value.tombstone();
  private static final Value<String> UNSUPPORTED_MARKER = Value.unsupported("no conversion exists", String.class);

  static Stream<Value<String>> markers() {
    return Stream.of(
      NULL_MARKER,
      UNDEFINED_MARKER,
      UNRESOLVED_MARKER,
      ERROR_MARKER,
      TOMBSTONE_MARKER,
      UNSUPPORTED_MARKER
    );
  }

  static Stream<Value<String>> nonErrorMarkers() {
    return Stream.of(NULL_MARKER, UNDEFINED_MARKER, UNRESOLVED_MARKER, TOMBSTONE_MARKER, UNSUPPORTED_MARKER);
  }

  static Stream<Value<String>> nonRecoverableMarkers() {
    return Stream.of(NULL_MARKER, UNDEFINED_MARKER, UNRESOLVED_MARKER, TOMBSTONE_MARKER);
  }

  @Nested
  @DisplayName("map")
  class Mapping {

    @Test
    @DisplayName("applies the mapper to a present payload and wraps the result")
    void appliesMapperToPresentPayload() {
      assertThat(Value.of("x").map(String::length)).hasIntValue(1);
    }

    @Test
    @DisplayName("coerces a null mapper result to nullValue(), never a literal null Value")
    void nullMapperResultBecomesNullValue() {
      assertThat(Value.of("x").map(letter -> null)).isNullValue();
    }

    @Test
    @DisplayName("a throwing mapper yields an error carrying the exception, expected type java.lang.Object")
    void throwingMapperYieldsErrorWithObjectExpectedType() {
      assertThat(Value.of("abc").map(Integer::parseInt))
        .hasThrowableInstanceOf(NumberFormatException.class)
        .hasExpectedTypeName("java.lang.Object");
    }

    @Test
    @DisplayName("an Error is not a data condition and escapes the chain")
    void errorFromMapperIsNotCaught() {
      assertThatThrownBy(() ->
        Value.of("x").map(letter -> {
          throw new StackOverflowError("too deep");
        })
      )
        .isInstanceOf(StackOverflowError.class)
        .hasMessage("too deep");
    }

    @Test
    @DisplayName("a raw collection result throws out of the chain, pointing at Value.listOf")
    void rawCollectionResultThrowsOutOfTheChain() {
      assertThatThrownBy(() -> Value.of("x").map(letter -> List.of(1, 2)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Value.of(...) does not accept List - use Value.listOf(...) instead");
    }

    @Test
    @DisplayName("a mapper answering a Value collapses instead of nesting (Value.of is idempotent)")
    void valueResultCollapsesInsteadOfNesting() {
      assertThat(Value.of("x").map(letter -> Value.of(7))).hasIntValue(7);
    }

    @Test
    @DisplayName("a propagated error keeps its original expected type — it is never re-minted")
    void errorPropagatesByIdentityKeepingExpectedType() {
      Value<String> error = Value.errorMessage("load failed", String.class);

      Value<Integer> mapped = error.map(text -> 1);

      assertThat(mapped).isSameAs(error).hasExpectedTypeName("java.lang.String");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.zems.lib.value.ValueTransformsContractTest#markers")
    @DisplayName("every state marker passes through unchanged")
    void markersPassThroughUnchanged(Value<String> marker) {
      assertThat(marker.map(String::length)).isSameAs(marker);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.zems.lib.value.ValueTransformsContractTest#markers")
    @DisplayName("the mapper is never invoked on a state marker")
    void mapperNotInvokedOnMarker(Value<String> marker) {
      AtomicInteger calls = new AtomicInteger();

      marker.map(text -> calls.incrementAndGet());

      assertThat(calls).hasValue(0);
    }
  }

  @Nested
  @DisplayName("flatMap")
  class FlatMap {

    @Test
    @DisplayName("answers the mapper's own Value rather than wrapping it again")
    void answersMappersOwnValue() {
      Value<Integer> inner = Value.of(7);

      assertThat(Value.of("x").flatMap(letter -> inner)).isSameAs(inner);
    }

    @Test
    @DisplayName("a mapper answering an error hands that error straight back")
    void answersMappersErrorUnchanged() {
      Value<Integer> inner = Value.errorMessage("parse failed", Integer.class);

      assertThat(Value.of("x").flatMap(letter -> inner))
        .isSameAs(inner)
        .hasErrorMessage("parse failed");
    }

    @Test
    @DisplayName("coerces a null returned reference to nullValue()")
    void nullResultBecomesNullValue() {
      assertThat(Value.of("x").flatMap(letter -> null)).isNullValue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.zems.lib.value.ValueTransformsContractTest#markers")
    @DisplayName("every state marker passes through unchanged")
    void markersPassThroughUnchanged(Value<String> marker) {
      assertThat(marker.flatMap(text -> Value.of(1))).isSameAs(marker);
    }
  }

  @Nested
  @DisplayName("filter")
  class Filter {

    @Test
    @DisplayName("keeps a present payload the predicate accepts")
    void keepsAcceptedPresentPayload() {
      Value<Integer> port = Value.of(8443);

      assertThat(port.filter(candidate -> candidate < 65_536)).isSameAs(port);
    }

    @Test
    @DisplayName("a present payload the predicate rejects becomes undefined()")
    void rejectedPresentPayloadBecomesUndefined() {
      assertThat(Value.of(70_000).filter(candidate -> candidate < 65_536)).isUndefined();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.zems.lib.value.ValueTransformsContractTest#markers")
    @DisplayName("a state marker passes through and never becomes undefined() — a null stays null")
    void markersPassThroughAndNeverBecomeUndefined(Value<String> marker) {
      assertThat(marker.filter(text -> false)).isSameAs(marker);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.zems.lib.value.ValueTransformsContractTest#markers")
    @DisplayName("the predicate is never invoked on a state marker")
    void predicateNotInvokedOnMarker(Value<String> marker) {
      AtomicInteger calls = new AtomicInteger();

      marker.filter(text -> calls.incrementAndGet() > 0);

      assertThat(calls).hasValue(0);
    }
  }

  @Nested
  @DisplayName("orElse / orElseGet")
  class OrElse {

    @Test
    @DisplayName("orElse answers the payload when present")
    void orElseAnswersPayloadWhenPresent() {
      assertThat(PRESENT.orElse("spare")).isEqualTo("hello");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.zems.lib.value.ValueTransformsContractTest#markers")
    @DisplayName("orElse answers the fallback on every state marker")
    void orElseAnswersFallbackOnEveryMarker(Value<String> marker) {
      assertThat(marker.orElse("spare")).isEqualTo("spare");
    }

    @Test
    @DisplayName("orElse accepts a null fallback — a null there is legitimate data, not a guarded argument")
    void orElseAcceptsNullFallback() {
      assertThat(NULL_MARKER.orElse(null)).isNull();
    }

    @Test
    @DisplayName("orElseGet answers the payload when present and never calls the supplier")
    void orElseGetAnswersPayloadWithoutCallingSupplier() {
      AtomicInteger calls = new AtomicInteger();

      String result = PRESENT.orElseGet(() -> {
        calls.incrementAndGet();
        return "spare";
      });

      assertThat(result).isEqualTo("hello");
      assertThat(calls).hasValue(0);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.zems.lib.value.ValueTransformsContractTest#markers")
    @DisplayName("orElseGet calls the supplier on every state marker")
    void orElseGetCallsSupplierOnEveryMarker(Value<String> marker) {
      assertThat(marker.orElseGet(() -> "spare")).isEqualTo("spare");
    }
  }

  @Nested
  @DisplayName("orElseThrow")
  class OrElseThrow {

    @Test
    @DisplayName("answers the payload when present")
    void answersPayloadWhenPresent() {
      assertThat(PRESENT.orElseThrow()).isEqualTo("hello");
    }

    @Test
    @DisplayName("an unchecked carried throwable is rethrown as it stands, not re-wrapped")
    void uncheckedCauseIsRethrownAsIs() {
      Value<Integer> error = Value.errorMessage("boom", Integer.class);

      assertThatThrownBy(error::orElseThrow).isInstanceOf(IllegalStateException.class).hasMessage("boom").hasNoCause();
    }

    @Test
    @DisplayName("a checked carried throwable is wrapped in an IllegalStateException that keeps it as the cause")
    void checkedCauseIsWrappedKeepingTheCause() {
      IOException carried = new IOException("disk");
      Value<Integer> error = Value.errorOf(carried, Integer.class);

      assertThatThrownBy(error::orElseThrow).isInstanceOf(IllegalStateException.class).cause().isSameAs(carried);
    }

    @Test
    @DisplayName("an unsupported marker throws UnsupportedOperationException carrying its reason")
    void unsupportedMarkerThrowsUnsupportedOperationException() {
      assertThatThrownBy(UNSUPPORTED_MARKER::orElseThrow)
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("no conversion exists")
        .hasNoCause();
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("nonErrorMarkerMessages")
    @DisplayName("a non-error marker throws NoSuchElementException naming the state it found")
    void nonErrorMarkerThrowsNoSuchElement(Value<String> marker, String expectedMessage) {
      assertThatThrownBy(marker::orElseThrow).isInstanceOf(NoSuchElementException.class).hasMessage(expectedMessage);
    }

    static Stream<Arguments> nonErrorMarkerMessages() {
      return Stream.of(
        Arguments.of(NULL_MARKER, "Value is not present: NullValue[]"),
        Arguments.of(UNDEFINED_MARKER, "Value is not present: UndefinedValue[]"),
        Arguments.of(UNRESOLVED_MARKER, "Value is not present: UnresolvedValue[]"),
        Arguments.of(TOMBSTONE_MARKER, "Value is not present: TombstoneValue[]")
      );
    }

    @Test
    @DisplayName("the supplier overload answers the payload when present")
    void supplierOverloadAnswersPayloadWhenPresent() {
      assertThat(PRESENT.orElseThrow(() -> new IllegalStateException("unused"))).isEqualTo("hello");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.zems.lib.value.ValueTransformsContractTest#markers")
    @DisplayName("the supplier overload throws the caller's exception on every state marker")
    void supplierOverloadThrowsCallersExceptionOnEveryMarker(Value<String> marker) {
      assertThatThrownBy(() -> marker.orElseThrow(() -> new IllegalArgumentException("caller chose this")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("caller chose this");
    }

    @Test
    @DisplayName("the supplier overload ignores a carried cause — the caller chose the exception")
    void supplierOverloadIgnoresCarriedCause() {
      Value<String> error = Value.errorOf(new IOException("disk"), String.class);

      assertThatThrownBy(() -> error.orElseThrow(() -> new IllegalArgumentException("caller chose this")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("caller chose this")
        .hasNoCause();
    }
  }

  @Nested
  @DisplayName("or")
  class Or {

    @Test
    @DisplayName("keeps a present value and never calls the supplier")
    void keepsPresentValueWithoutCallingSupplier() {
      AtomicInteger calls = new AtomicInteger();

      Value<String> result = PRESENT.or(() -> {
        calls.incrementAndGet();
        return Value.of("spare");
      });

      assertThat(result).isSameAs(PRESENT);
      assertThat(calls).hasValue(0);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.zems.lib.value.ValueTransformsContractTest#markers")
    @DisplayName("fires on every non-present state, an error included")
    void firesOnEveryMarkerIncludingError(Value<String> marker) {
      Value<String> alternative = Value.of("spare");

      assertThat(marker.or(() -> alternative)).isSameAs(alternative);
    }

    @Test
    @DisplayName("coerces a null supplier result to nullValue()")
    void nullSupplierResultBecomesNullValue() {
      assertThat(UNDEFINED_MARKER.or(() -> null)).isNullValue();
    }
  }

  @Nested
  @DisplayName("ifPresent / ifPresentOrElse")
  class IfPresent {

    @Test
    @DisplayName("ifPresent hands the payload to the consumer when present")
    void ifPresentHandsPayloadToConsumer() {
      AtomicReference<String> seen = new AtomicReference<>();

      PRESENT.ifPresent(seen::set);

      assertThat(seen).hasValue("hello");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.zems.lib.value.ValueTransformsContractTest#markers")
    @DisplayName("ifPresent does nothing on a state marker")
    void ifPresentDoesNothingOnMarker(Value<String> marker) {
      AtomicInteger calls = new AtomicInteger();

      marker.ifPresent(text -> calls.incrementAndGet());

      assertThat(calls).hasValue(0);
    }

    @Test
    @DisplayName("ifPresentOrElse runs the consumer and not the runnable when present")
    void ifPresentOrElseRunsConsumerWhenPresent() {
      AtomicReference<String> seen = new AtomicReference<>();
      AtomicInteger absentCalls = new AtomicInteger();

      PRESENT.ifPresentOrElse(seen::set, absentCalls::incrementAndGet);

      assertThat(seen).hasValue("hello");
      assertThat(absentCalls).hasValue(0);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.zems.lib.value.ValueTransformsContractTest#markers")
    @DisplayName("ifPresentOrElse runs the runnable on every state marker")
    void ifPresentOrElseRunsRunnableOnEveryMarker(Value<String> marker) {
      AtomicInteger presentCalls = new AtomicInteger();
      AtomicInteger absentCalls = new AtomicInteger();

      marker.ifPresentOrElse(text -> presentCalls.incrementAndGet(), absentCalls::incrementAndGet);

      assertThat(presentCalls).hasValue(0);
      assertThat(absentCalls).hasValue(1);
    }
  }

  @Nested
  @DisplayName("onError / ifAbsent")
  class Taps {

    @Test
    @DisplayName("onError hands the carried throwable over and answers the same value for chaining")
    void onErrorHandsThrowableOverAndChains() {
      IOException carried = new IOException("disk");
      Value<String> error = Value.errorOf(carried, String.class);
      AtomicReference<Throwable> seen = new AtomicReference<>();

      Value<String> result = error.onError(seen::set);

      assertThat(seen).hasValue(carried);
      assertThat(result).isSameAs(error);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.zems.lib.value.ValueTransformsContractTest#nonErrorMarkers")
    @DisplayName("onError does not fire on a non-error marker but still chains")
    void onErrorDoesNotFireOnNonErrorMarker(Value<String> marker) {
      AtomicInteger calls = new AtomicInteger();

      Value<String> result = marker.onError(throwable -> calls.incrementAndGet());

      assertThat(calls).hasValue(0);
      assertThat(result).isSameAs(marker);
    }

    @Test
    @DisplayName("onError does not fire on a present value")
    void onErrorDoesNotFireOnPresentValue() {
      AtomicInteger calls = new AtomicInteger();

      Value<String> result = PRESENT.onError(throwable -> calls.incrementAndGet());

      assertThat(calls).hasValue(0);
      assertThat(result).isSameAs(PRESENT);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.zems.lib.value.ValueTransformsContractTest#markers")
    @DisplayName("ifAbsent fires on every state marker and answers the same value for chaining")
    void ifAbsentFiresOnEveryMarkerAndChains(Value<String> marker) {
      AtomicInteger calls = new AtomicInteger();

      Value<String> result = marker.ifAbsent(calls::incrementAndGet);

      assertThat(calls).hasValue(1);
      assertThat(result).isSameAs(marker);
    }

    @Test
    @DisplayName("ifAbsent does not fire on a present value")
    void ifAbsentDoesNotFireOnPresentValue() {
      AtomicInteger calls = new AtomicInteger();

      Value<String> result = PRESENT.ifAbsent(calls::incrementAndGet);

      assertThat(calls).hasValue(0);
      assertThat(result).isSameAs(PRESENT);
    }
  }

  @Nested
  @DisplayName("recover / recoverWith / mapError")
  class Recover {

    @Test
    @DisplayName("recover replaces an error with the wrapped fallback")
    void recoverReplacesError() {
      assertThat(Value.errorMessage("boom", Integer.class).recover(8080)).hasIntValue(8080);
    }

    @Test
    @DisplayName("recover coerces a null fallback to nullValue()")
    void recoverCoercesNullFallback() {
      assertThat(ERROR_MARKER.recover(null)).isNullValue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.zems.lib.value.ValueTransformsContractTest#nonRecoverableMarkers")
    @DisplayName("recover leaves every non-recoverable marker untouched — absence is a state, not a failure")
    void recoverLeavesNonRecoverableMarkersUntouched(Value<String> marker) {
      assertThat(marker.recover("spare")).isSameAs(marker);
    }

    @Test
    @DisplayName("recover leaves a present value untouched")
    void recoverLeavesPresentValueUntouched() {
      assertThat(PRESENT.recover("spare")).isSameAs(PRESENT);
    }

    @Test
    @DisplayName("recoverWith derives the replacement from the carried throwable")
    void recoverWithDerivesReplacementFromThrowable() {
      Value<String> error = Value.errorOf(new IOException("disk"), String.class);

      assertThat(error.recoverWith(throwable -> Value.of("recovered from " + throwable.getMessage()))).hasStringValue(
        "recovered from disk"
      );
    }

    @Test
    @DisplayName("recoverWith coerces a null result to nullValue()")
    void recoverWithCoercesNullResult() {
      assertThat(ERROR_MARKER.recoverWith(throwable -> null)).isNullValue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.zems.lib.value.ValueTransformsContractTest#nonRecoverableMarkers")
    @DisplayName("recoverWith leaves every non-recoverable marker untouched")
    void recoverWithLeavesNonRecoverableMarkersUntouched(Value<String> marker) {
      AtomicInteger calls = new AtomicInteger();

      Value<String> result = marker.recoverWith(throwable -> {
        calls.incrementAndGet();
        return Value.of("spare");
      });

      assertThat(calls).hasValue(0);
      assertThat(result).isSameAs(marker);
    }

    @Test
    @DisplayName("recover replaces an unsupported marker — the caller takes another path, not a retry")
    void recoverReplacesAnUnsupportedMarker() {
      assertThat(Value.unsupported("no conversion exists", Integer.class).recover(8080)).hasIntValue(8080);
    }

    @Test
    @DisplayName("recover coerces a null fallback to nullValue() on an unsupported marker too")
    void recoverCoercesNullFallbackOnUnsupportedMarker() {
      assertThat(UNSUPPORTED_MARKER.recover(null)).isNullValue();
    }

    @Test
    @DisplayName("recoverWith hands an unsupported marker an UnsupportedOperationException carrying the reason")
    void recoverWithHandsUnsupportedOperationExceptionForUnsupportedMarker() {
      AtomicReference<Throwable> seen = new AtomicReference<>();

      Value<String> recovered = UNSUPPORTED_MARKER.recoverWith(throwable -> {
        seen.set(throwable);
        return Value.of("spare");
      });

      assertThat(recovered).hasStringValue("spare");
      assertThat(seen.get())
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("no conversion exists")
        .hasNoCause();
    }

    @Test
    @DisplayName("mapError and onError never fire on an unsupported marker — it is not an error")
    void mapErrorAndOnErrorLeaveAnUnsupportedMarkerUntouched() {
      AtomicInteger calls = new AtomicInteger();

      Value<String> remapped = UNSUPPORTED_MARKER.mapError(throwable -> {
        calls.incrementAndGet();
        return new IllegalStateException("unused");
      });
      Value<String> tapped = UNSUPPORTED_MARKER.onError(throwable -> calls.incrementAndGet());

      assertThat(remapped).isSameAs(UNSUPPORTED_MARKER);
      assertThat(tapped).isSameAs(UNSUPPORTED_MARKER);
      assertThat(calls).hasValue(0);
    }

    @Test
    @DisplayName("mapError replaces the throwable and keeps the expected type")
    void mapErrorReplacesThrowableKeepingExpectedType() {
      Value<String> error = Value.errorOf(new IOException("disk"), String.class);

      Value<String> remapped = error.mapError(throwable ->
        new IllegalStateException("wrapped: " + throwable.getMessage())
      );

      assertThat(remapped)
        .hasErrorMessage("wrapped: disk")
        .hasThrowableInstanceOf(IllegalStateException.class)
        .hasExpectedTypeName("java.lang.String");
    }

    @Test
    @DisplayName("mapError treats a null mapped throwable as a caller bug — errorOf's guard throws")
    void mapErrorNullMappedThrowableIsRefused() {
      assertThatThrownBy(() -> ERROR_MARKER.mapError(throwable -> null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Error cannot be null");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.zems.lib.value.ValueTransformsContractTest#nonErrorMarkers")
    @DisplayName("mapError leaves every non-error marker untouched")
    void mapErrorLeavesNonErrorMarkersUntouched(Value<String> marker) {
      assertThat(marker.mapError(throwable -> new IllegalStateException("unused"))).isSameAs(marker);
    }
  }

  @Nested
  @DisplayName("fold")
  class Fold {

    @Test
    @DisplayName("routes a present payload to the present handler")
    void routesPresentPayloadToPresentHandler() {
      String label = PRESENT.fold(payload -> "ok: " + payload, marker -> "no value");

      assertThat(label).isEqualTo("ok: hello");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.zems.lib.value.ValueTransformsContractTest#markers")
    @DisplayName("routes every state marker to the marker handler")
    void routesEveryMarkerToMarkerHandler(Value<String> marker) {
      String label = marker.fold(payload -> "ok: " + payload, found -> "no value");

      assertThat(label).isEqualTo("no value");
    }

    @Test
    @DisplayName("the marker handler receives the Value itself, so it can branch on isError()")
    void markerHandlerCanBranchOnError() {
      String label = ERROR_MARKER.fold(
        payload -> "ok: " + payload,
        marker -> marker.isError() ? "error: " + marker.errorMessage().orElse("?") : "no value"
      );

      assertThat(label).isEqualTo("error: boom");
    }

    @Test
    @DisplayName("folds to a collection result, the case map refuses to wrap")
    void foldsToCollectionResultMapRefusesToWrap() {
      List<Integer> lengths = Value.of("abc").fold(payload -> List.of(payload.length()), marker -> List.of());

      assertThat(lengths).containsExactly(3);
    }
  }

  @Nested
  @DisplayName("stream")
  class Streaming {

    @Test
    @DisplayName("a present payload streams as a single element")
    void presentPayloadStreamsOneElement() {
      assertThat(Value.of("a").stream()).containsExactly("a");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.zems.lib.value.ValueTransformsContractTest#markers")
    @DisplayName("every state marker streams empty")
    void everyMarkerStreamsEmpty(Value<String> marker) {
      assertThat(marker.stream()).isEmpty();
    }

    @Test
    @DisplayName("a collection payload is one element, not its elements")
    void collectionPayloadStreamsAsOneElement() {
      assertThat(Value.listOf("a", "b").stream()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("null function arguments")
  class NullFunctionArguments {

    @ParameterizedTest(name = "{0}")
    @MethodSource("guardedCalls")
    @DisplayName("a null function-shaped argument is a caller bug and is refused with one shared message")
    void nullFunctionArgumentIsRefused(GuardedCall guardedCall) {
      assertThatThrownBy(guardedCall.invocation())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Combinator function must not be null");
    }

    static Stream<GuardedCall> guardedCalls() {
      return Stream.of(
        new GuardedCall("map", () -> PRESENT.map(null)),
        new GuardedCall("flatMap", () -> PRESENT.flatMap(null)),
        new GuardedCall("filter", () -> PRESENT.filter(null)),
        new GuardedCall("orElseGet", () -> PRESENT.orElseGet(null)),
        new GuardedCall("orElseThrow(Supplier)", () -> PRESENT.orElseThrow(null)),
        new GuardedCall("or", () -> PRESENT.or(null)),
        new GuardedCall("ifPresent", () -> PRESENT.ifPresent(null)),
        new GuardedCall("ifPresentOrElse(action)", () -> PRESENT.ifPresentOrElse(null, () -> {})),
        new GuardedCall("ifPresentOrElse(absentAction)", () -> PRESENT.ifPresentOrElse(text -> {}, null)),
        new GuardedCall("onError", () -> PRESENT.onError(null)),
        new GuardedCall("ifAbsent", () -> PRESENT.ifAbsent(null)),
        new GuardedCall("recoverWith", () -> PRESENT.recoverWith(null)),
        new GuardedCall("mapError", () -> PRESENT.mapError(null)),
        new GuardedCall("fold(onPresent)", () -> PRESENT.fold(null, marker -> "no value")),
        new GuardedCall("fold(onAbsent)", () -> PRESENT.fold(payload -> "ok", null))
      );
    }
  }

  @Nested
  @DisplayName("no combinator yields a literal null Value")
  class NoNullValues {

    @ParameterizedTest(name = "{0}")
    @MethodSource("nullReturningCombinators")
    @DisplayName("a combinator fed a null-returning function still answers a concrete Value on every state")
    void neverAnswersLiteralNull(NullReturningCombinator combinator) {
      Stream.concat(Stream.of(PRESENT), markers()).forEach(input ->
        assertThat(combinator.apply().apply(input)).as("%s on %s", combinator, input).isNotNull()
      );
    }

    static Stream<NullReturningCombinator> nullReturningCombinators() {
      return Stream.of(
        new NullReturningCombinator("map(x -> null)", input -> input.map(text -> null)),
        new NullReturningCombinator("flatMap(x -> null)", input -> input.flatMap(text -> null)),
        new NullReturningCombinator("filter(x -> false)", input -> input.filter(text -> false)),
        new NullReturningCombinator("or(() -> null)", input -> input.or(() -> null)),
        new NullReturningCombinator("recover(null)", input -> input.recover(null)),
        new NullReturningCombinator("recoverWith(t -> null)", input -> input.recoverWith(throwable -> null)),
        new NullReturningCombinator("onError(t -> {})", input -> input.onError(throwable -> {})),
        new NullReturningCombinator("ifAbsent(() -> {})", input -> input.ifAbsent(() -> {}))
      );
    }
  }

  record GuardedCall(String label, ThrowingCallable invocation) {
    @Override
    public String toString() {
      return label;
    }
  }

  record NullReturningCombinator(String label, Function<Value<String>, Value<?>> apply) {
    @Override
    public String toString() {
      return label;
    }
  }
}
