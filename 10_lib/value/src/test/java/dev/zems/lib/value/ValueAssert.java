package dev.zems.lib.value;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.function.Predicate;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ObjectAssert;

/**
 * AssertJ assertions for {@link Value}. Lets tests read a value's state, content, and error detail in one fluent chain
 * instead of pattern-matching, unwrapping, and casting by hand.
 *
 * <p>
 * Reach it through {@link ValueAssertions#assertThat(Value)} (a single static import gives both these and AssertJ's own
 * {@code assertThat} overloads). Ships from value's {@code tests}-classifier JAR, so any module testing
 * {@code Value}-returning code can use it.
 *
 * <p>
 * <b>Naming note:</b> the NullValue-state check is {@link #isNullValue()}, not {@code isNull()} — AssertJ's inherited
 * {@code isNull()} asserts the <i>reference</i> is {@code null}, which a {@link NullValue} is not.
 */
public final class ValueAssert extends AbstractAssert<ValueAssert, Value<?>> {

  ValueAssert(Value<?> actual) {
    super(actual, ValueAssert.class);
  }

  // --- state ---------------------------------------------------------------

  /** Asserts the value is a {@link NullValue} (an explicit null state, not a {@code null} reference). */
  public ValueAssert isNullValue() {
    return hasState(Value::isNull, "a NullValue (explicit null state)");
  }

  /** Asserts the value is an {@link UndefinedValue}. */
  public ValueAssert isUndefined() {
    return hasState(Value::isUndefined, "undefined (UndefinedValue)");
  }

  /** Asserts the value is an {@link UnresolvedValue}. */
  public ValueAssert isUnresolved() {
    return hasState(Value::isUnresolved, "unresolved (UnresolvedValue)");
  }

  /** Asserts the value is an {@link ErrorValue}. */
  public ValueAssert isError() {
    return hasState(Value::isError, "an error (ErrorValue)");
  }

  /** Asserts the value is a {@link TombstoneValue}. */
  public ValueAssert isTombstone() {
    return hasState(Value::isTombstone, "a tombstone (TombstoneValue)");
  }

  /** Asserts the value is present — defined, not null, resolved, not an error, not a tombstone. */
  public ValueAssert isPresent() {
    return hasState(Value::isPresent, "present (a real value, not a state marker)");
  }

  /** Asserts the value is absent — any state marker. */
  public ValueAssert isAbsent() {
    return hasState(Value::isAbsent, "absent (a state marker)");
  }

  /** Asserts the value is not an {@link UndefinedValue}. */
  public ValueAssert isDefined() {
    return hasState(Value::isDefined, "defined (not an UndefinedValue)");
  }

  /** Asserts the value is not an {@link UnresolvedValue}. */
  public ValueAssert isResolved() {
    return hasState(Value::isResolved, "resolved (not an UnresolvedValue)");
  }

  // --- scalar content ------------------------------------------------------

  /** Asserts the value is a string wrapper holding {@code expected}. */
  public ValueAssert hasStringValue(String expected) {
    requireType(Value::isString, "a string");
    Assertions.assertThat(actual.asString().orElseThrow()).as("string value").isEqualTo(expected);
    return this;
  }

  /** Asserts the value is a numeric wrapper whose int value equals {@code expected}. */
  public ValueAssert hasIntValue(int expected) {
    requireType(Value::isNumber, "a number");
    Assertions.assertThat(actual.asInteger().orElseThrow().intValue()).as("int value").isEqualTo(expected);
    return this;
  }

  /** Asserts the value is a numeric wrapper whose long value equals {@code expected}. */
  public ValueAssert hasLongValue(long expected) {
    requireType(Value::isNumber, "a number");
    Assertions.assertThat(actual.asNumber().orElseThrow().longValue()).as("long value").isEqualTo(expected);
    return this;
  }

  /** Asserts the value is a numeric wrapper whose double value equals {@code expected}. */
  public ValueAssert hasDoubleValue(double expected) {
    requireType(Value::isNumber, "a number");
    Assertions.assertThat(actual.asDouble().orElseThrow().doubleValue()).as("double value").isEqualTo(expected);
    return this;
  }

  /** Asserts the value is a boolean wrapper holding {@code expected}. */
  public ValueAssert hasBooleanValue(boolean expected) {
    requireType(Value::isBoolean, "a boolean");
    Assertions.assertThat(actual.asBoolean().orElseThrow().booleanValue()).as("boolean value").isEqualTo(expected);
    return this;
  }

  /** Asserts the value is a UUID wrapper holding {@code expected}. */
  public ValueAssert hasUuidValue(UUID expected) {
    requireType(Value::isUuid, "a uuid");
    Assertions.assertThat(actual.asUuid().orElseThrow()).as("uuid value").isEqualTo(expected);
    return this;
  }

  /** Asserts the value is a URL wrapper holding {@code expected}. */
  public ValueAssert hasUrlValue(URI expected) {
    requireType(Value::isUrl, "a url");
    Assertions.assertThat(actual.asUrl().orElseThrow()).as("url value").isEqualTo(expected);
    return this;
  }

  // --- collection content --------------------------------------------------

  /** Asserts the value is a {@code ListValue}. */
  public ValueAssert isList() {
    return hasState(Value::isList, "a ListValue");
  }

  /** Asserts the value is a {@code SetValue}. */
  public ValueAssert isSet() {
    return hasState(Value::isSet, "a SetValue");
  }

  /** Asserts the value is a {@code MapValue}. */
  public ValueAssert isMap() {
    return hasState(Value::isMap, "a MapValue");
  }

  /** Asserts the value is a list, set, or map with {@code expected} elements / entries. */
  public ValueAssert hasSize(int expected) {
    isNotNull();
    int size;
    if (actual.isList()) {
      size = actual.asList().orElseThrow().size();
    } else if (actual.isSet()) {
      size = actual.asSet().orElseThrow().size();
    } else if (actual.isMap()) {
      size = actual.asMap().orElseThrow().size();
    } else {
      failWithMessage("%nExpecting:%n  <%s>%nto be a list, set, or map so its size can be checked", actual);
      return this;
    }
    Assertions.assertThat(size).as("collection size").isEqualTo(expected);
    return this;
  }

  /** Asserts the value is a list containing (at least) the given element values. */
  @SafeVarargs
  public final ValueAssert isListContaining(Value<?>... expected) {
    isList();
    var elements = new ArrayList<Value<?>>(actual.asList().orElseThrow());
    Assertions.assertThat(elements).as("list elements").contains(expected);
    return this;
  }

  /** Asserts the value is a set containing (at least) the given element values. */
  @SafeVarargs
  public final ValueAssert isSetContaining(Value<?>... expected) {
    isSet();
    var elements = new HashSet<Value<?>>(actual.asSet().orElseThrow());
    Assertions.assertThat(elements).as("set elements").contains(expected);
    return this;
  }

  /** Asserts the value is a map containing the entry {@code key -> value}. */
  public ValueAssert containsEntry(Object key, Value<?> value) {
    isMap();
    var entries = new LinkedHashMap<Object, Value<?>>(actual.asMap().orElseThrow());
    Assertions.assertThat(entries).as("map entries").containsEntry(key, value);
    return this;
  }

  // --- error inspection ----------------------------------------------------

  /** Asserts the value is an error whose message equals {@code expected}. */
  public ValueAssert hasErrorMessage(String expected) {
    isError();
    Assertions.assertThat(actual.errorMessage()).as("error message").hasValue(expected);
    return this;
  }

  /** Asserts the value is an error whose message contains {@code fragment}. */
  public ValueAssert hasErrorMessageContaining(String fragment) {
    isError();
    Assertions.assertThat(actual.errorMessage().orElse(null)).as("error message").contains(fragment);
    return this;
  }

  /** Asserts the value is an error carrying a throwable of (or extending) {@code type}. */
  public ValueAssert hasThrowableInstanceOf(Class<? extends Throwable> type) {
    isError();
    Assertions.assertThat(actual.error().orElseThrow()).as("error throwable").isInstanceOf(type);
    return this;
  }

  /**
   * Asserts the value is an error whose expected-type descriptor has the given qualified name — e.g.
   * {@code "java.lang.String"} or {@code "List<java.lang.String>"}.
   */
  public ValueAssert hasExpectedTypeName(String expectedQualifiedName) {
    isError();
    var descriptor = ((ErrorValue<?>) actual).expectedType();
    Assertions.assertThat(descriptor.qualifiedName()).as("error expected type").isEqualTo(expectedQualifiedName);
    return this;
  }

  // --- navigation ----------------------------------------------------------

  /**
   * Hands the wrapped present value to a standard AssertJ {@link ObjectAssert}, so a caller chains deeper checks
   * without unwrapping by hand. Fails if the value is a state marker (nothing to extract).
   */
  public ObjectAssert<Object> extractingValue() {
    isNotNull();
    if (actual.isAbsent()) {
      failWithMessage(
        "%nExpecting:%n  <%s>%nto be present so its value can be extracted, but it was a state marker",
        actual
      );
    }
    Object payload = Value.unbox(actual);
    return new ObjectAssert<>(payload);
  }

  /** Hands the error's throwable to a standard AssertJ throwable assertion. Fails if the value is not an error. */
  public AbstractThrowableAssert<?, ? extends Throwable> extractingThrowable() {
    isError();
    return Assertions.assertThat(actual.error().orElseThrow());
  }

  // --- helpers -------------------------------------------------------------

  private ValueAssert hasState(Predicate<Value<?>> predicate, String expectedState) {
    isNotNull();
    if (!predicate.test(actual)) {
      failWithMessage("%nExpecting:%n  <%s>%nto be %s", actual, expectedState);
    }
    return this;
  }

  private void requireType(Predicate<Value<?>> predicate, String expectedType) {
    isNotNull();
    if (!predicate.test(actual)) {
      failWithMessage("%nExpecting:%n  <%s>%nto wrap %s value", actual, expectedType);
    }
  }
}
