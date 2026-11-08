# Collections

The collection factories take raw elements, or map them out of your domain objects, and wrap each one:

```java
record User(String name, int age) {}
var users = List.of(new User("Ada", 36), new User("Grace", 45));

Value<List<Value<String>>> names = Value.listOf(users, User::name);
Value<Map<String, Value<Integer>>> byName = Value.mapOf(users, User::name, User::age);
Value<List<Value<String>>> tags = Value.listOf("math", "engines");

names.unbox();                                 // List<Value<String>> — the elements stay boxed
byName.unbox().get("Ada");                     // Value.of(36)
```

A collection is one value: the whole list is present, or the whole list is a marker. Each element carries its own state
as well, so a list may hold an `ErrorValue` for one bad entry and present values for the rest.

## Unboxing

`unbox()` answers the backing collection one level deep, with the elements still boxed. `ListValue.unboxElements()` and
`SetValue.unboxElements()` unbox the elements, `MapValue.unboxEntries()` the entry values; a nested collection stays
boxed. All of them throw `IllegalStateException` on a state marker, at the top or among the elements:

```java
if (tags instanceof ListValue<String> list) {
  list.unboxElements();                        // ["math", "engines"]
}
```

`Value.of` refuses a raw collection, so the two routes cannot be confused:

```java
Value.of(List.of("a", "b"));                   // throws IllegalArgumentException: use listOf
```

## Mixed element types

A collection with mixed element types cannot be pinned to one `E`. The typed factories stamp an explicit element
descriptor, typically `TypeDescriptor.oneOf(...)`, onto the collection, so it self-describes when written without a
descriptor:

```java
Value<List<Value<Object>>> mixed = Value.listOfTyped(
  TypeDescriptor.oneOf("scalar", BuiltinTypeDescriptors.STRING, BuiltinTypeDescriptors.INTEGER),
  Value.of("Ada"),
  Value.of(42));

var out = new StringWriter();
try (var w = ValueIo.framed().jsonWriter(out)) {
  w.write(mixed);                              // the descriptor is inferred from the value
}
```

## Choosing

**`listOf(raw...)` or `listOf(items, mapper)`.** The varargs form when you hold the elements; the mapper form when they
come out of domain objects, so no intermediate list is built. `setOf`, `mapOf` and `sortedMapOf` follow the same pair;
`mapOf(items, keyFn, valueFn)` takes two mappers.

**The typed factories.** `listOfTyped`, `setOfTyped`, `mapOfTyped` and `sortedMapOfTyped` when the element types differ
and the collection is written to the wire without an explicit descriptor. The element type binds to the descriptor's
type, usually `Object`, and the elements arrive as `Value<? extends E>`.

**`unbox()` or `unboxElements()`.** `unbox()` when the consumer handles per-element state; `unboxElements()` when every
element must be present and a marker among them should fail loudly.

Writing a collection to the wire is the subject of [Marshalling with ValueIo](marshalling.md).
