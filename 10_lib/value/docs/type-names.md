# Type Names as Data

A type name is a value of its own: the qualified name alone, never a `Class`, and nothing ever resolves it. A name read
off the wire is exactly as safe as any other string:

```java
Value<TypeName> type = Value.typeOf(UUID.class);        // extracts "java.util.UUID" and keeps only that
Value<TypeName> same = Value.typeOf("java.util.UUID");  // parsed — what a wire read yields
type.equals(same);                                      // true — the value is the name, nothing more

type.asTypeName();                                      // Optional["java.util.UUID"]
type.isTypeName();                                      // true
Value.of("java.util.UUID").isTypeName();                // false — the same characters as ordinary text

Value.typeOf("not a name!");                            // ErrorValue — the factories never throw
```

## Binding a name to a class

A name binds to a class only through a set of classes the caller holds, handed to
`Converter.convert(source, BoundTypes.of(base, members...), typeSlot)` at the conversion call site: the structure
converts to the bound class its type slot names, a name outside the set is refused, and no class is ever loaded because
data named it. The set is a call-site argument only: no registry, no `ServiceLoader`, no static state.

## Choosing

**`typeOf(Class)` or `typeOf(String)`.** `typeOf(Class)` when you hold the class and want its name as data, for example
to write it into a type slot. `typeOf(String)` when the name arrives as text, from the wire or from configuration; a
malformed name is an `ErrorValue`.

**Binding.** Never look a name up yourself. Hand `BoundTypes.of(base, members...)` to the converter where the structure
is converted, naming exactly the classes that may be chosen; the match is by `Class.getName()`, never by a simple name.
See [Converting values](converting-values.md) for the converter.
