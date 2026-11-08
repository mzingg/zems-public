# Testing with ValueAssertions

The main jar carries fluent AssertJ assertions for `Value` in `dev.zems.lib.value.junit`. JUnit and AssertJ are optional
dependencies of the library, so add them to your own test scope:

```xml
<dependency>
  <groupId>org.assertj</groupId>
  <artifactId>assertj-core</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.junit.jupiter</groupId>
  <artifactId>junit-jupiter</artifactId>
  <scope>test</scope>
</dependency>
```

`ValueAssertions` extends AssertJ's `Assertions`, so one static import gives you the `Value`-aware `assertThat` overload
next to all the standard ones:

```java
import static dev.zems.lib.value.junit.ValueAssertions.assertThat;

assertThat(Value.of("Ada")).isPresent().hasStringValue("Ada");

assertThat(Value.nullValue()).isNullValue();   // named to avoid clashing with AssertJ's isNull()

assertThat(Value.uuidOf("not-a-uuid"))
    .isError()
    .hasErrorMessageContaining("not-a-uuid")
    .hasThrowableInstanceOf(IllegalArgumentException.class);

assertThat(Value.listOf("a", "b"))
    .hasSize(2)
    .isListContaining(Value.of("a"));
```

State checks (`isPresent`, `isAbsent`, `isNullValue`, `isUndefined`, `isError`, `isTombstone`, `isUnsupported`), content
checks (`hasStringValue`, `hasIntValue`, `hasUuidValue`, ...), collection checks (`hasSize`, `isListContaining`,
`containsEntry`, ...), error inspection (`hasErrorMessage`, `extractingThrowable()` to drop into AssertJ's throwable
assertions) and `hasUnsupportedReason` for a type-pair refusal are all chainable.

## Wire parity in one test

`RoundTripFormatHarness` drives one parameterised test over the binary and the JSON format, so a record's wire shape is
proven on both:

```java
record Person(String name, int age) {}

static Stream<RoundTripFormatHarness.Format> formats() {
  return RoundTripFormatHarness.all();
}

@ParameterizedTest
@MethodSource("formats")
void personRoundTrips(RoundTripFormatHarness.Format format) {
  TypeDescriptor<Person> descriptor = TypeDescriptor.of(Person.class);
  Value<Person> ada = Value.of(new Person("Ada", 36));

  byte[] wire = format.write(ada, descriptor);

  assertThat(format.read(wire, descriptor)).isEqualTo(ada);
}
```

## Choosing

**Dependencies.** AssertJ for the assertions; JUnit Jupiter as well when you use the harness, which is built for
`@ParameterizedTest`. Both stay in your test scope; the library declares them optional, so a consumer that does not test
with them never sees them.

**`isNullValue()`, not `isNull()`.** AssertJ's `isNull()` asks whether the reference is `null`; a `Value` reference
never is. `isNullValue()` asks whether the value is the null marker. The same holds for the other state checks:
`isError()` asks about the state, not about an exception.

**The harness.** Use `RoundTripFormatHarness.all()` for every type you marshal, so a shape that round-trips in JSON but
not in binary is caught; `binary()` or `json()` alone when one format is all you ship. The harness writes and reads
through `ValueIo.framed()`; a streaming shape is covered by the same descriptor.

What the harness writes is explained in [Marshalling with ValueIo](marshalling.md).
