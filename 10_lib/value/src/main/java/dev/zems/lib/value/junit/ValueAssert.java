package dev.zems.lib.value.junit;

import dev.zems.lib.value.ErrorValue;
import dev.zems.lib.value.NullValue;
import dev.zems.lib.value.UnsupportedValue;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ObjectAssert;

/**
 * AssertJ assertions for {@link Value}: state, content and error detail in one fluent chain. Reach it through
 * {@link ValueAssertions#assertThat(Value)}. The explicit-null state check is {@link #isNullValue()}, because the
 * inherited {@code isNull()} asserts that the reference itself is {@code null}.
 */
public final class ValueAssert extends AbstractAssert<ValueAssert, Value<?>> {

  ValueAssert(Value<?> actual) {
    super(actual, ValueAssert.class);
  }

  /** Asserts the value is a {@link NullValue}, the explicit null state, not a {@code null} reference. */
  public ValueAssert isNullValue() {
    return hasState(Value::isNull, "a NullValue (explicit null state)");
  }

  public ValueAssert isUndefined() {
    return hasState(Value::isUndefined, "undefined (UndefinedValue)");
  }

  public ValueAssert isUnresolved() {
    return hasState(Value::isUnresolved, "unresolved (UnresolvedValue)");
  }

  public ValueAssert isError() {
    return hasState(Value::isError, "an error (ErrorValue)");
  }

  public ValueAssert isTombstone() {
    return hasState(Value::isTombstone, "a tombstone (TombstoneValue)");
  }

  /** Asserts the value is an {@link UnsupportedValue}: a refusal about the type pair or the operation, not the data. */
  public ValueAssert isUnsupported() {
    return hasState(Value::isUnsupported, "unsupported (UnsupportedValue)");
  }

  /** Asserts the value is anything but an {@link UnsupportedValue}; every other marker counts as supported. */
  public ValueAssert isSupported() {
    return hasState(Value::isSupported, "supported (not an UnsupportedValue)");
  }

  /**
   * Asserts the value is present: defined, not null, resolved, not an error, not a tombstone and not unsupported.
   */
  public ValueAssert isPresent() {
    return hasState(Value::isPresent, "present (a real value, not a state marker)");
  }

  /** Asserts the value is any state marker. */
  public ValueAssert isAbsent() {
    return hasState(Value::isAbsent, "absent (a state marker)");
  }

  public ValueAssert isDefined() {
    return hasState(Value::isDefined, "defined (not an UndefinedValue)");
  }

  public ValueAssert isResolved() {
    return hasState(Value::isResolved, "resolved (not an UnresolvedValue)");
  }

  public ValueAssert hasStringValue(String expected) {
    requireType(Value::isString, "a string");
    Assertions.assertThat(actual.unbox()).as("string value").isEqualTo(expected);
    return this;
  }

  /** Asserts the value is a number that converts to {@code int} without loss and equals {@code expected}. */
  public ValueAssert hasIntValue(int expected) {
    requireType(Value::isNumber, "a number");
    Assertions.assertThat(exactly(Integer.class, "an int")).as("int value").isEqualTo(expected);
    return this;
  }

  /** Asserts the value is a number that converts to {@code long} without loss and equals {@code expected}. */
  public ValueAssert hasLongValue(long expected) {
    requireType(Value::isNumber, "a number");
    Assertions.assertThat(exactly(Long.class, "a long")).as("long value").isEqualTo(expected);
    return this;
  }

  /** Asserts the value is a number that converts to {@code double} without loss and equals {@code expected}. */
  public ValueAssert hasDoubleValue(double expected) {
    requireType(Value::isNumber, "a number");
    Assertions.assertThat(exactly(Double.class, "a double")).as("double value").isEqualTo(expected);
    return this;
  }

  public ValueAssert hasBooleanValue(boolean expected) {
    requireType(Value::isBoolean, "a boolean");
    Assertions.assertThat(actual.unbox()).as("boolean value").isEqualTo(expected);
    return this;
  }

  public ValueAssert hasUuidValue(UUID expected) {
    requireType(Value::isUuid, "a uuid");
    Assertions.assertThat(actual.asUuid().orElseThrow()).as("uuid value").isEqualTo(expected);
    return this;
  }

  public ValueAssert hasUrlValue(URI expected) {
    requireType(Value::isUrl, "a url");
    Assertions.assertThat(actual.asUrl().orElseThrow()).as("url value").isEqualTo(expected);
    return this;
  }

  /** Asserts the value is a type name whose qualified name is {@code expected}. */
  public ValueAssert hasTypeNameValue(String expected) {
    requireType(Value::isTypeName, "a type name");
    Assertions.assertThat(actual.asTypeName().orElseThrow()).as("type name value").isEqualTo(expected);
    return this;
  }

  /** Asserts the value is an enum constant equal to {@code expected}. */
  public ValueAssert hasEnumValue(Enum<?> expected) {
    requireType(Value::isEnum, "an enum constant");
    Assertions.assertThat(actual.asEnum().orElseThrow()).as("enum value").isEqualTo(expected);
    return this;
  }

  public ValueAssert isList() {
    return hasState(Value::isList, "a ListValue");
  }

  public ValueAssert isSet() {
    return hasState(Value::isSet, "a SetValue");
  }

  public ValueAssert isMap() {
    return hasState(Value::isMap, "a MapValue");
  }

  /** Asserts the value is a list, set or map with {@code expected} elements or entries. */
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

  /** Asserts the value is a list containing at least the given elements. */
  @SafeVarargs
  public final ValueAssert isListContaining(Value<?>... expected) {
    isList();
    var elements = new ArrayList<Value<?>>(actual.asList().orElseThrow());
    Assertions.assertThat(elements).as("list elements").contains(expected);
    return this;
  }

  /** Asserts the value is a set containing at least the given elements. */
  @SafeVarargs
  public final ValueAssert isSetContaining(Value<?>... expected) {
    isSet();
    var elements = new HashSet<Value<?>>(actual.asSet().orElseThrow());
    Assertions.assertThat(elements).as("set elements").contains(expected);
    return this;
  }

  public ValueAssert containsEntry(Object key, Value<?> value) {
    isMap();
    var entries = new LinkedHashMap<Object, Value<?>>(actual.asMap().orElseThrow());
    Assertions.assertThat(entries).as("map entries").containsEntry(key, value);
    return this;
  }

  public ValueAssert hasErrorMessage(String expected) {
    isError();
    Assertions.assertThat(actual.errorMessage()).as("error message").hasValue(expected);
    return this;
  }

  public ValueAssert hasErrorMessageContaining(String fragment) {
    isError();
    Assertions.assertThat(actual.errorMessage().orElse(null)).as("error message").contains(fragment);
    return this;
  }

  public ValueAssert hasThrowableInstanceOf(Class<? extends Throwable> type) {
    isError();
    Assertions.assertThat(actual.error().orElseThrow()).as("error throwable").isInstanceOf(type);
    return this;
  }

  /** Asserts the value is an unsupported marker whose reason equals {@code expected}. */
  public ValueAssert hasUnsupportedReason(String expected) {
    isUnsupported();
    Assertions.assertThat(((UnsupportedValue<?>) actual).reason())
      .as("unsupported reason")
      .isEqualTo(expected);
    return this;
  }

  /**
   * Asserts the value is an error or an unsupported value whose expected-type descriptor has the given qualified
   * name, such as {@code "java.lang.String"} or {@code "List<java.lang.String>"}.
   */
  public ValueAssert hasExpectedTypeName(String expectedQualifiedName) {
    isNotNull();
    TypeDescriptor<?> descriptor = switch (actual) {
      case ErrorValue<?> error -> error.expectedType();
      case UnsupportedValue<?> unsupported -> unsupported.expectedType();
      default -> null;
    };
    if (descriptor == null) {
      failWithMessage(
        "%nExpecting:%n  <%s>%nto be an error (ErrorValue) or unsupported (UnsupportedValue) so its expected type can be read",
        actual
      );
      return this;
    }
    Assertions.assertThat(descriptor.qualifiedName()).as("expected type").isEqualTo(expectedQualifiedName);
    return this;
  }

  /** Hands the payload to a standard AssertJ {@link ObjectAssert}; fails when the value is a state marker. */
  public ObjectAssert<Object> extractingValue() {
    isNotNull();
    if (actual.isAbsent()) {
      failWithMessage(
        "%nExpecting:%n  <%s>%nto be present so its value can be extracted, but it was a state marker",
        actual
      );
    }
    Object payload = actual.unbox();
    return new ObjectAssert<>(payload);
  }

  /** Hands the carried throwable to a standard AssertJ throwable assertion; fails when the value is not an error. */
  public AbstractThrowableAssert<?, ? extends Throwable> extractingThrowable() {
    isError();
    return Assertions.assertThat(actual.error().orElseThrow());
  }

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

  private <N> N exactly(Class<N> targetType, String label) {
    Value<N> converted = actual.convertTo(targetType);
    if (!converted.isPresent()) {
      failWithMessage(
        "%nExpecting:%n  <%s>%nto convert exactly to %s, but: %s",
        actual,
        label,
        converted
          .errorMessage()
          .or(() -> reasonOf(converted))
          .orElse("no value")
      );
    }
    return converted.unbox();
  }

  private Optional<String> reasonOf(Value<?> value) {
    return value instanceof UnsupportedValue<?> unsupported ? Optional.of(unsupported.reason()) : Optional.empty();
  }
}
