# Converting Values

One generic verb converts any value to a target class. For the everyday scalar cases it sits right on the value,
`convertTo(Class)`. A conversion that cannot succeed returns an `UnsupportedValue<T>` when no conversion exists for the
type pair, or an `ErrorValue<T>` when this value is refused, both naming the source type, the target type and the
reason. Never an exception:

```java
Value<LocalDate> date = Value.of("2031-04-01").convertTo(LocalDate.class); // every canonical string form parses
Value<Long> wider = Value.of(42).convertTo(Long.class);                    // lossless numeric moves are automatic
Value<String> text = Value.of(2.5d).convertTo(String.class);               // ... and back out as the canonical form

// Losslessness is decided per value, not per type pair.
Value.of(4.0d).convertTo(Integer.class);              // Value.of(4)
Value.of(3.7d).convertTo(Integer.class);              // ErrorValue "Cannot convert Double to Integer: 3.7 is not a whole number"
Value.of(5_000_000_000L).convertTo(Integer.class);    // ErrorValue "Cannot convert Long to Integer: 5000000000 overflows Integer"

// A refusal about the type pair is its own state — retrying with other data is pointless, so take another path.
Value.of(UUID.randomUUID()).convertTo(Integer.class); // UnsupportedValue "Cannot convert UUID to Integer: no conversion exists"
Value.of(UUID.randomUUID()).convertTo(Integer.class).recover(0);  // Value.of(0) — recover applies to it as to an error
```

## Lossy conversions state their policy

A lossy or unit-ambiguous conversion runs only through a conversion that states its policy. A `Converter` carries the
full surface: `with(...)` derives a new converter with the policy registered (the original is never touched) and the
same verb runs everything. Build it once, keep it, reuse it:

```java
Converter rounding = Converter.standard().with(Conversions.doubleToInt(Rounding.DOWN));
rounding.convert(Value.of(3.7d), Integer.class);          // Value.of(3)

Converter epochs = Converter.standard().with(Conversions.epochToInstant(EpochUnit.SECONDS));
epochs.convert(Value.of(1_700_000_000L), Instant.class);  // Value.of(Instant.parse("2023-11-14T22:13:20Z"))
```

`Rounding` has no `UNNECESSARY`: refusing whenever rounding would drop content is what the standard converter does
without any policy, so a constant for it would only be a second way to spell the default.

## Your own conversion

A custom conversion rides the same contract; at the call site it is indistinguishable from a shipped one:

```java
record Celsius(double degrees) {}

Converter thermometer = Converter.standard()
    .with(Conversion.of(Double.class, Celsius.class, kelvin -> Value.of(new Celsius(kelvin - 273.15))));
thermometer.convert(Value.of(273.15d), Celsius.class);    // Value.of(new Celsius(0.0))
```

## Choosing

**`convertTo` or a `Converter`.** `value.convertTo(Target.class)` runs the standard converter: every lossless move,
every canonical string form, records from structures and back. Reach for `Converter.standard().with(...)` only when a
conversion needs a policy: a rounding, an epoch unit, or a conversion of your own.

**Which lossy policy.** `Conversions.doubleToInt(Rounding.DOWN)` and its siblings (`floatToLong`, `bigDecimalToInt`,
...) name the rounding mode the caller accepts, one of `Rounding`'s constants.
`Conversions.epochToInstant(EpochUnit.SECONDS)` or `MILLIS` says what a `Long` means as a point in time; without it a
`Long` to `Instant` conversion does not exist, because the unit is ambiguous.

**Keep the converter.** `with(...)` answers a new `Converter` and leaves the original untouched, so build one per policy
set, store it in a field, and reuse it; building it per call is correct but wasteful.

**Custom conversions.** `Conversion.of(Source.class, Target.class, fn)` where `fn` answers a `Value<Target>`: a present
value, or an `ErrorValue` for a source it refuses. The converter re-states a refusal in its own frame,
`Cannot convert <Source> to <Target>: <reason>`, and keeps your reason.

A type name in a type slot binds to a class only through `BoundTypes` at the call site; see
[Type names as data](type-names.md).
