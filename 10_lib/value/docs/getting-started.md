# Getting Started

A `Value<T>` carries its own state. Bad input does not throw where it is parsed; it becomes an `ErrorValue` that travels
with the data and is handled where the value is used:

```java
Value<LocalDate> when = Value.localDateOf("2026-13-99");            // no exception: an ErrorValue<LocalDate>
// ... passed along, stored, handed to another layer ...
LocalDate date = when.asLocalDate().orElse(LocalDate.of(2026, 1, 1)); // handled here, where the date is used
when.error().ifPresent(t -> System.err.println("bad date input: " + t.getMessage()));   // the cause travels along
```

## Construct

The Java type picks the wrapper. Factories never throw:

```java
Value<String> name = Value.of("Ada");                 // StringValue
Value<Integer> age = Value.of(42);                    // IntegerValue
Value<UUID> id = Value.uuidOf("not-a-uuid");          // ErrorValue<UUID> — a parser never throws
String rawHome = null;
Value<String> home = Value.ofNullable(rawHome);       // NullValue — null spelt as a state, no ternary
```

## Read

Pattern-match on state and type, or use the `as...()` accessors, which answer an `Optional`:

```java
String shown = switch (name) {
  case StringValue s -> s.string();
  case NullValue<?> _ -> "(null)";
  case ErrorValue<?> e -> "(error: " + e.errorMessage().orElse("?") + ")";
  case UnsupportedValue<?> u -> "(unsupported: " + u.reason() + ")";
  default -> name.convertTo(String.class).orElse("(other)");
};                                                    // "Ada"
```

## Compare

Compare values with `equals`, never with `==`:

```java
Value.of(42).equals(Value.of(42));                    // true
```

The library's own value types are immutable and carry no identity semantics, so that they can become value classes when
Project Valhalla lands; `==` on a value object will compare field values, not references. The direction page says what
else your code avoids to stay ready: [Project Valhalla](jdk-directions.md#project-valhalla-values-without-identity).

## Choosing

Use `Value.of(x)` when you hold a typed Java object; the overload for its type picks the wrapper. Use
`Value.xxxOf(text)` (`uuidOf`, `localDateOf`, `urlOf`, ...) when you parse text: invalid text becomes an `ErrorValue`,
never an exception. Use `Value.ofNullable(x)` when `null` is possible and means "null": it answers `Value.nullValue()`
for a null reference, where the typed `Value.of(String)` would answer an `ErrorValue`.
[Errors as values](errors-as-values.md) has the full rule.

## Where next

- [Explicit state](explicit-state.md) — the six state markers and when to produce each.
- [Errors as values](errors-as-values.md) — errors, unsupported requests and the null rule.
- [Transform and recover](transform-and-recover.md) — chaining and recovery.
- [Marshalling with ValueIo](marshalling.md) — records to JSON or binary and back.
