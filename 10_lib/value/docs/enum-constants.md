# Enum Constants

An enum constant is a value of its own, and every enum has a wire shape for free: the constant's name, never its
ordinal, so reordering the constants keeps old payloads readable:

```java
enum Status { ACTIVE, ARCHIVED, @EnumAlias("INACTIVE") DISABLED }

Value<Status> status = Value.of(Status.ACTIVE);     // EnumValue<Status>
status.isEnum();                                    // true
status.asEnum(Status.class);                        // Optional[ACTIVE]
status.convertTo(String.class);                     // Value.of("ACTIVE") — the name, never toString()

var out = new StringWriter();
ValueIo.streaming().jsonWriteAll(out, Status.class, Stream.of(status));
out.toString();                                     // {"$payload":{"__slot0":"ACTIVE"}} and a line break
```

## Renaming a constant

A renamed constant stays readable when the old name is declared as an alias. An old payload carrying `"INACTIVE"` reads
back as `Status.DISABLED`; the constant is always written under its current name:

```java
var old = new StringReader("{\"$payload\":{\"__slot0\":\"INACTIVE\"}}\n");
try (Stream<Value<Status>> in = ValueIo.streaming().jsonRecords(old, Status.class)) {
  in.toList();                                      // [Value.of(Status.DISABLED)]
}
```

## Unknown constants

A constant the reader does not know throws an `IllegalStateException`, or becomes an `ErrorValue<Status>` when you say
so:

```java
TypeDescriptor<Status> tolerant = TypeDescriptor.of(Status.class)
    .withEvolutionPolicy(new EvolutionPolicy(UnknownSlotPolicy.FAIL, UnknownConstantPolicy.ERROR_VALUE));

var unknown = new StringReader("{\"$payload\":{\"__slot0\":\"DELETED\"}}\n");
try (Stream<Value<Status>> in = ValueIo.streaming().jsonRecords(unknown, tolerant)) {
  in.toList();                                      // [ErrorValue<Status>]
}
var again = new StringReader("{\"$payload\":{\"__slot0\":\"DELETED\"}}\n");
try (Stream<Value<Status>> in = ValueIo.streaming().jsonRecords(again, Status.class)) {
  in.toList();                                      // throws IllegalStateException
}
```

A record with an enum component gets its descriptor synthesised like any other record.

## Choosing

**Renaming.** Keep the old name on the constant with `@EnumAlias("OLD_NAME")`; several names are allowed. Reordering
constants needs nothing: the wire carries the name.

**Unknown constants.** The default throws, because a constant the reader does not know usually means the reader is older
than the writer and should be upgraded. Choose `UnknownConstantPolicy.ERROR_VALUE` when the stream must keep flowing and
the consumer handles the error per value. That tolerance reaches only positions where the read yields a `Value`: a
top-level read, a list or set element, a map value. Raw positions (record components, map keys, union payloads) have no
slot for an error value and always throw. `EvolutionPolicy.LENIENT` does not cover constants: it skips unknown slots
only, so an unknown constant still fails under it.

The wire shape is declared, not reflected; the direction page explains the model behind it:
[Towards Better Serialization](jdk-directions.md#towards-better-serialization-declared-not-reflected).
