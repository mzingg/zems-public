# Transform and Recover

`Value` chains like `Optional`, plus recovery, because an error is a state you can handle, not an exception in flight.
One pipeline reads a port from text and handles every state on the way:

```java
static int port(String raw) {
  return Value.ofNullable(raw)                 // null -> NullValue, never an ErrorValue by accident
    .map(Integer::parseInt)                    // a parse failure becomes an ErrorValue
    .filter(p -> p > 0 && p < 65_536)          // out of range -> undefined()
    .recover(8080)                             // error -> 8080
    .orElse(80);                               // null or undefined -> 80
}

port("8443");                                  // 8443
port(null);                                    // 80
port("abc");                                   // 8080
port("70000");                                 // 80
```

## The cause is preserved

`onError` sees the throwable and passes the value on; `recoverWith` derives the replacement from it:

```java
Value<Integer> limit = Value.of("many").convertTo(Integer.class);   // ErrorValue
int effective = limit
  .onError(t -> System.err.println("bad limit: " + t.getMessage()))
  .recoverWith(t -> Value.of(100))
  .orElse(0);                                                       // 100
```

## Fold out of the Value world

`fold` is total by construction and the way out to any result type. The absent handler receives the `Value` itself, so
it can branch further on `isError()` or `errorMessage()`:

```java
String label = Value.uuidOf("not-a-uuid").fold(
  payload -> "ok: " + payload,
  marker -> marker.isError() ? "error: " + marker.errorMessage().orElse("?") : "absent");
// "error: Invalid UUID string: not-a-uuid"
```

## Collections out of a mapper

`map` wraps its result through `Value.of`, which refuses a raw `List`, `Set` or `Map` with an
`IllegalArgumentException`. A mapper that answers a collection goes through `flatMap` over a collection factory, or
through `fold`:

```java
Value<String> csv = Value.of("a,b");
Value<List<Value<String>>> parts = csv.flatMap(s -> Value.listOf(s.split(",")));   // a list of two values
csv.map(s -> List.of(s.split(",")));     // throws IllegalArgumentException: use listOf, not Value.of
```

## Choosing

**`map`, `flatMap` or `fold`.** `map` when the function answers a plain result: a `RuntimeException` it throws becomes
an `ErrorValue`, and a `null` it answers becomes `Value.nullValue()`. `flatMap` when the function answers a `Value` of
its own, a collection factory included; `flatMap` guards nothing, its mapper owns the result, so it answers an error
value rather than throwing. `fold` when you leave the `Value` world for any result type, collections included.

**`recover` or `orElse`.** `recover(fallback)` replaces an `ErrorValue` or an `UnsupportedValue` and stays a `Value`, so
the chain continues; every other state passes through. `recoverWith(fn)` does the same with a function of the cause.
`orElse(other)` unwraps: it answers the payload when present and `other` on any marker, so it ends the chain. In the
pipeline above, `recover` catches the parse error and `orElse` catches the null and the filtered value.

**`filter`.** A present payload that fails the predicate becomes `undefined()`, not an error: "no acceptable value" is
not "refused input".

The marker sets these act on are drawn in [Explicit state](explicit-state.md).
