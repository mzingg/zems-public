# Marshalling with ValueIo

`ValueIo` is the single I/O entry point. Records get a wire shape for free, synthesised from the canonical constructor,
and the same code shape works for JSON and binary CBOR (RFC 8949):

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
List<Person> people;
try (Stream<Value<Person>> in = ValueIo.streaming().jsonRecords(new StringReader(out.toString()), Person.class)) {
  people = in.map(Value::unbox).toList();      // [Person[name=Ada, age=36], Person[name=Grace, age=45]]
}

// Same records as compact binary CBOR on disk — swap the terminal call, keep the code.
Path path = Files.createTempFile("people", ".cbor");
ValueIo.streaming().binaryWriteAllToFile(path, Person.class, people.stream().map(Value::of));
try (Stream<Value<Person>> s = ValueIo.streaming().binaryRecordsFromFile(path, Person.class)) {
  s.map(Value::unbox).toList();                // the same two people; the file is mmap'd, multi-GB inputs open in O(1)
}
```

## Framed documents

`framed()` writes a single document: header, exactly one top-level record, terminator. It supports a checksum;
`streaming()` has no terminator slot, so a checksum throws at configuration time there. Type verification works in both
modes.

```java
var framed = new StringWriter();
try (var w = ValueIo.framed().withChecksum(ChecksumAlgorithm.SHA_256).jsonWriter(framed)) {
  w.write(Value.of(new Person("Ada", 36)), TypeDescriptor.of(Person.class));
}
try (var r = ValueIo.framed().withChecksum(ChecksumAlgorithm.SHA_256).jsonReader(new StringReader(framed.toString()))) {
  r.read(Person.class);                        // Value.of(new Person("Ada", 36)) — the digest verified on read
}
```

## What the JSON looks like

Two modes, the same JSON inside, a different outer framing. `FRAMED` wraps the whole document in one object with a
header and a terminator; `STREAMING` writes each record as a self-contained object on its own line (JSON Lines). For
`Value.of("x")` and `Value.nullValue()`, written under the `String` descriptor (the header and terminator objects are
elided here; the header carries the protocol version, the mode, the type-verification flag and the checksum algorithm,
the terminator the byte count and the checksum):

```
// Value.of("x"):
FRAMED:    {"$header":{...},"$payload":{"__slot0":"x"},"$terminator":{...}}
STREAMING: {"$payload":{"__slot0":"x"}}

// Value.nullValue():
FRAMED:    {"$header":{...},"$payload":{"__state":"NULL"},"$terminator":{...}}
STREAMING: {"$payload":{"__state":"NULL"}}
```

State markers are objects with a `"__state"` field: errors add `"__errorClass"` and `"__errorMessage"`, unsupported
markers `"__reason"`. With type verification on, each record gets a `"__type"` field holding `descriptorName@signature`.
User slots are keyed `__slot<id>`; slot names never reach the wire.

## Evolving a record

Record synthesis assigns slot id = component index: appending a component is wire-safe, reordering or removing one
renumbers the rest and is a wire break. An unknown slot on read throws under the default `EvolutionPolicy.STRICT` and is
skipped under `LENIENT`:

```java
record PersonV2(String name, int age, String email) {}

var v2 = new StringWriter();
ValueIo.streaming().jsonWriteAll(v2, PersonV2.class, Stream.of(Value.of(new PersonV2("Ada", 36, "ada@example.org"))));

TypeDescriptor<Person> lenient = TypeDescriptor.of(Person.class).withEvolutionPolicy(EvolutionPolicy.LENIENT);
try (Stream<Value<Person>> in = ValueIo.streaming().jsonRecords(new StringReader(v2.toString()), lenient)) {
  in.map(Value::unbox).toList();               // [Person[name=Ada, age=36]] — slot 2 skipped
}
try (Stream<Value<Person>> in = ValueIo.streaming().jsonRecords(new StringReader(v2.toString()), Person.class)) {
  in.toList();                                 // throws IllegalStateException: unknown slot under STRICT
}
```

## Declaring a descriptor

A record needs no declaration. A type that is not a record, or a record whose wire shape you want to author, declares a
`public static final TypeDescriptor<T> DESCRIPTOR` field, found by `TypeDescriptor.find(Class)` after the built-ins and
before synthesis. The field is found by name first, so a declaration typed as a concrete subtype
(`ScalarTypeDescriptor<Foo>`) is honoured; a same-named static field whose type is not a `TypeDescriptor` fails loudly
rather than being ignored, because ignoring it would let a record fall back to synthesis with a different wire shape.

```java
public record Celsius(double degrees) {
  public static final TypeDescriptor<Celsius> DESCRIPTOR = TypeDescriptor.of(
      "example.Celsius",
      Celsius.class,
      r -> new Celsius(r.readDouble(0, "degrees")),
      (w, c) -> w.writeDouble(0, "degrees", c.degrees()));
}

var authored = new StringWriter();
ValueIo.streaming().jsonWriteAll(authored, Celsius.class, Stream.of(Value.of(new Celsius(21.5))));
// {"$payload":{"__slot0":21.5}} — the authored shape, found through the DESCRIPTOR field
```

## Records in a named module

Record synthesis reaches the canonical constructor and the accessors as method handles through `privateLookupIn` where
the package is opened to this module, else `publicLookup`, never `setAccessible` reflection. A record in a named module
therefore needs its package exported, or opened to `dev.zems.lib.value`:

```java
module com.example.app {
  requires dev.zems.lib.value;
  opens com.example.app.model to dev.zems.lib.value;   // or: exports com.example.app.model;
}
```

## Wire-level safety bounds

Readers and writers are bounded against hostile input by default: nesting depth, string, number and collection sizes and
duplicate keys are all capped (`WireConstraints.SECURE_DEFAULTS`). A violation throws `WireConstraintViolationException`
with the constraint, the limit and the actual size. Loosen one bound through the builder, or all of them with
`WireConstraints.UNCHECKED`:

```java
ValueIo wide = ValueIo.streaming()
    .withWireConstraints(WireConstraints.builder().maxArrayLength(10_000_000).build());
```

`WireConstraints` changes no bytes and is not recorded in the header: it is a local safety policy of the reader or
writer that applies it.

## Choosing

**`framed()` or `streaming()`.** `framed()` for one document, a file or a message, where a header, a terminator and a
checksum are worth their bytes. `streaming()` for a record sequence of unknown length, read lazily; it has no header and
no terminator, and end-of-stream is EOF.

**JSON or binary.** The same descriptor feeds both, so the choice is about the consumer: JSON (JSON Lines in streaming
mode) where a person or another tool reads the bytes, binary CBOR where size and speed matter and a file may be read
through an mmap.

**A record or a `DESCRIPTOR`.** Let synthesis derive the shape from a record wherever you can; declare `DESCRIPTOR` for
a type that is not a record, or when the wire shape must differ from the components. A record is also the on-ramp to
Project Valhalla: a record is the shape JEP 401 names as a good candidate for a value class
([Project Valhalla](jdk-directions.md#project-valhalla-values-without-identity)).

**Evolving a record.** Append new components at the end; never reorder or remove. A reader older than the writer sets
`EvolutionPolicy.LENIENT` to skip the slots it does not know; the default `STRICT` fails, so a shape mismatch is
noticed. `LENIENT` covers unknown slots only; unknown enum constants are the separate `UnknownConstantPolicy`
([Enum constants](enum-constants.md)).

**Wire constraints.** Keep the secure defaults wherever the bytes come from outside your process. Raise one bound when a
legitimate payload hits it; use `UNCHECKED` only on bytes your own process wrote, because the JSON reader's nesting is
then bounded only by the thread stack.

**Checksum and type verification.** The checksum is framed-only: `withChecksum(ChecksumAlgorithm.SHA_256)` digests the
logical stream, slot ids and values, so the digest is identical across binary and JSON and is verified on read.
`usingTypeVerification()` works in both modes: it writes each record's descriptor name and signature to the wire as a
`"__type"` field and checks it on read; use it where a reader may be handed a document of the wrong type.

The descriptor model follows the JDK's "Towards Better Serialization" design note; the direction page says how:
[Towards Better Serialization](jdk-directions.md#towards-better-serialization-declared-not-reflected).
