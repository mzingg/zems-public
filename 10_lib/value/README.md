# ZEMS Value

Immutable value types for Java with **explicit state** and a **wire-format-agnostic marshalling API** — no runtime
dependencies.

Instead of leaning on `null`, every value carries its own state: a real value, or one of `NullValue`, `UndefinedValue`,
`UnresolvedValue`, `ErrorValue`, `TombstoneValue`. The same `Value<T>` round-trips through binary (CBOR) and JSON behind
one I/O surface.

See [CLAUDE.md](CLAUDE.md) for the full design and rationale.

## Install

```xml
<dependency>
  <groupId>dev.zems.lib</groupId>
  <artifactId>value</artifactId>
  <version>0.0.1</version>
</dependency>
```

Requires JDK 25 or newer.

## The basics

```java
// Construct — the Java type picks the wrapper. Factories never throw; bad input becomes an ErrorValue.
Value<String> name = Value.of("Ada");
Value<Integer> age = Value.of(42);
Value<UUID> id     = Value.uuidOf("not-a-uuid");   // -> ErrorValue<UUID>

// Read by pattern matching on state + type.
String shown = switch (name) {
  case StringValue s   -> s.string();
  case NullValue<?> _  -> "(null)";
  case ErrorValue<?> e -> "(error: " + e.errorMessage().orElse("?") + ")";
  default              -> name.asString().orElse("(other)");
};
```

## Errors are values, not exceptions

Every parser and factory returns a `Value<T>` — invalid input becomes an `ErrorValue<T>` that carries its cause along,
so error handling happens where you _use_ the value, not where you parsed it:

```java
Value<LocalDate> when = Value.localDateOf(userInput);   // "2026-13-99" -> ErrorValue<LocalDate>
when.error().ifPresent(t -> log.warn("bad date input", t));
LocalDate date = when.asLocalDate().orElse(LocalDate.now());
```

Null is a state you spell, not an accident you catch:

```java
Value<String> home = Value.ofNullable(System.getenv("HOME"), Value::of);  // null -> NullValue, no ternary
```

## Collections in one call

The collection factories take raw elements — or map them out of your domain objects — and wrap each one:

```java
record User(String name, int age) {}
var users = List.of(new User("Ada", 36), new User("Grace", 45));

Value<List<Value<String>>> names         = Value.listOf(users, User::name);
Value<Map<String, Value<Integer>>> byAge = Value.mapOf(users, User::name, User::age);
```

## Marshalling with ValueIo

`ValueIo` is the single I/O entry point. Records get a wire shape for free (synthesised from the canonical constructor);
the same code shape works for JSON and binary CBOR:

```java
record Person(String name, int age) {}

// JSON Lines out — one record per line.
var out = new StringWriter();
ValueIo.streaming().jsonWriteAll(out, Person.class, Stream.of(
    Value.of(new Person("Ada", 36)),
    Value.of(new Person("Grace", 45))));
// {"$payload":{"__slot0":"Ada","__slot1":36}}
// {"$payload":{"__slot0":"Grace","__slot1":45}}
//  ^ slots are keyed by id, never by field name — renaming a record component is wire-compatible

// ...and back, lazily (Stream.close() releases everything the reader opened).
try (Stream<Value<Person>> in = ValueIo.streaming().jsonRecords(new StringReader(out.toString()), Person.class)) {
  List<Person> people = in.map(Value::unbox).toList();
}

// Same records as compact binary CBOR (RFC 8949) on disk — swap the terminal call, keep the code.
ValueIo.streaming().binaryWriteAllToFile(path, Person.class, people.stream().map(Value::of));
try (Stream<Value<Person>> s = ValueIo.streaming().binaryRecordsFromFile(path, Person.class)) {
  s.forEach(...);   // file is mmap'd; multi-GB inputs open in O(1)
}
```

For single documents, `ValueIo.framed()` adds an envelope with optional checksum verification and type verification
(`usingTypeVerification()` writes each record's descriptor name to the wire and checks it on read). Readers and writers
are bounded against hostile input by default — nesting depth, string/collection sizes, and duplicate keys are all capped
unless you opt out (`withWireConstraints`).

## Testing with ValueAssertions

The module ships a `tests`-classifier JAR with fluent AssertJ assertions for `Value`:

```xml
<dependency>
  <groupId>dev.zems.lib</groupId>
  <artifactId>value</artifactId>
  <type>test-jar</type>
  <scope>test</scope>
</dependency>
```

`ValueAssertions` extends AssertJ's `Assertions`, so one static import gives you the `Value`-aware `assertThat` overload
next to all the standard ones:

```java
import static dev.zems.lib.value.ValueAssertions.assertThat;

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

State checks (`isPresent`, `isAbsent`, `isNullValue`, `isUndefined`, `isError`, `isTombstone`), content checks
(`hasStringValue`, `hasIntValue`, `hasUuidValue`, ...), collection checks (`hasSize`, `isListContaining`,
`containsEntry`, ...), and error inspection (`hasErrorMessage`, `extractingThrowable()` to drop into AssertJ's throwable
assertions) are all chainable. The same JAR carries `RoundTripFormatHarness` for asserting binary/JSON wire parity with
one parameterised test.
