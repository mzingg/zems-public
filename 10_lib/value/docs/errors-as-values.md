# Errors as Values

Every parser and factory returns a `Value<T>`. Invalid input becomes an `ErrorValue<T>` that carries its cause along, so
error handling happens where you _use_ the value, not where you parsed it:

```java
Value<LocalDate> when = Value.localDateOf("2026-13-99");   // ErrorValue<LocalDate>
when.isError();                                            // true
when.error().isPresent();                                  // true — the Throwable travels with the value
when.asLocalDate();                                        // Optional.empty
```

## Null is a state you spell

A typed factory asserts a type, so a null reference there is a caller bug and becomes an `ErrorValue`. The generic entry
boxes heterogeneous data, where a null reference is legitimate data. `ofNullable` always reads null as the null state:

```java
Value<String> text = Value.of((String) null);        // ErrorValue — the typed overload asserts a String
Value<Object> data = Value.of((Object) null);        // NullValue — the generic entry boxes anything
Value<String> maybe = Value.ofNullable((String) null); // NullValue — null is possible and means "null"
```

## Building an error yourself

`Value.errorOf` and `Value.errorMessage` are the error constructors. Both name the expected type, so a consumer can
still tell what the slot was meant to hold:

```java
Value<Integer> failed = Value.errorOf(new IllegalStateException("quota exceeded"), Integer.class);
Value<Integer> refused = Value.errorMessage("quota exceeded", Integer.class);
refused.errorMessage();                              // Optional["quota exceeded"]
failed.equals(refused);                              // true — equality is by throwable class, message and expected type
```

## Unsupported: the request, not the value

An `UnsupportedValue` says the request cannot be served: a type pair with no conversion, an operation the value does not
have. Retrying with other data is pointless. It is not an error: `error()` is empty and `onError` never fires, while
`recover` applies to it:

```java
Value<Integer> cannot = Value.of(UUID.randomUUID()).convertTo(Integer.class);
cannot.isUnsupported();                              // true
cannot.isError();                                    // false
cannot.error();                                      // Optional.empty
cannot.recover(0);                                   // Value.of(0) — recover applies to it as to an error
```

## Choosing

**`ErrorValue` or `UnsupportedValue`.** An `ErrorValue` says _this value_ was refused: bad text, a lossy conversion, a
failed lookup; another value may pass. An `UnsupportedValue` says _the request_ cannot be served; take another path.
Build the first with `Value.errorOf(throwable, Type.class)` when you hold the cause, or
`Value.errorMessage(text, Type.class)` when a message is all there is; build the second with
`Value.unsupported(reason, Type.class)`. All three refuse null or blank input with an `IllegalArgumentException`: a
missing reason is a programming bug, not data.

**Which null.** `Value.ofNullable(x)` whenever null is possible and means "null". `Value.of((Object) null)` is null too,
but only through the generic entry; the typed overloads make a null reference an `ErrorValue`, because the static type
promised a value.

Recovering from either state is the subject of [Transform and recover](transform-and-recover.md).
