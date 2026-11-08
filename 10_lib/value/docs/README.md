# ZEMS Value Documentation

ZEMS Value is a library of immutable value types for Java, built to become value classes when Project Valhalla lands.
Each value carries its own state, such as null or an error, so bad input does not throw where it is parsed: it travels
with the data and is handled where it is used.

- [Setup](setup.md) — the JDK floor and the dependency.
- [Getting started](getting-started.md) — your first values, and what happens to bad input.

## Guides

One guide per feature. Each one shows the feature as a use case and ends with the decisions it asks of you.

- [Explicit state](explicit-state.md) — the six state markers, which one to produce, and your own types as values.
- [Errors as values](errors-as-values.md) — a refused input travels as an `ErrorValue`, a refused request as an
  `UnsupportedValue`.
- [Transform and recover](transform-and-recover.md) — chain like `Optional`, recover from errors, fold out.
- [Collections](collections.md) — lists, sets and maps of values in one call.
- [Type names as data](type-names.md) — a type name that never loads a class.
- [Versions](versions.md) — version strings that know their grammar.
- [Quantities and money](quantities-and-money.md) — a number with its unit, physical or monetary.
- [Enum constants](enum-constants.md) — an enum constant as a value with a wire shape for free.
- [Converting values](converting-values.md) — one verb from any value to a target class, lossless by default.
- [JSON documents](json-documents.md) — JSON as data, parsed and navigated without exceptions.
- [Marshalling with ValueIo](marshalling.md) — records to JSON or binary CBOR and back.
- [Testing with ValueAssertions](testing.md) — AssertJ assertions for values and a round-trip harness.

## Explanation

- [Where the JDK is heading, and where this library stands](jdk-directions.md)
