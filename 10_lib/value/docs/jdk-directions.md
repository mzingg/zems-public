# Where the JDK Is Heading, and Where This Library Stands

The value library follows three OpenJDK directions, so that their features can be adopted as they land: Project
Valhalla, the "Towards Better Serialization" design note and the Simple JSON API. None of them is part of JDK 25, the
library's floor. This page says what each one gives you on JDK 25 today, and what changes for your code when the JDK
feature arrives.

JDK status as checked on 2026-10-24.

## Project Valhalla: values without identity

[Project Valhalla](https://openjdk.org/projects/valhalla/) adds value objects to Java: objects that lack identity and
are told apart only by their field values. [JEP 401](https://openjdk.org/jeps/401) brings them to JDK 28 as a preview
feature, so they need `--enable-preview` there and do not exist on JDK 25.

**On JDK 25.** The library's own value types are immutable and carry no identity semantics: they are records or final
classes with only final fields. Records are the on-ramp: data that flows through the library should be a record wherever
possible, and a record is the shape JEP 401 names as a good candidate for a value class.

**When value classes land.** JEP 401 makes adding the `value` modifier to a final class with only final fields a
binary-compatible change, so the library intends to adopt it without changing its API once the feature is final and
reaches the library's JDK floor. That is a design intent, not a guarantee: it holds as long as the feature keeps the
rules JEP 401 describes today.

**What your code avoids to stay ready.** For a value object, JEP 401 changes three things that work today:

- `==` compares field values, not references. Compare values with `equals`, never with `==`.
- Synchronising on a value object fails: at compile time where the static type is a value class, with an
  `IdentityException` at run time otherwise. Never use a value as a lock.
- `System.identityHashCode` is computed from the field values. Do not rely on identity hash codes or an
  `IdentityHashMap` to tell values apart, and do not use a value as a `WeakHashMap` key or as the referent of a weak
  reference: JEP 401 rules both out for value objects.

## Towards Better Serialization: declared, not reflected

["Towards Better Serialization"](https://openjdk.org/projects/amber/design-notes/towards-better-serialization) is an
exploratory design note by Brian Goetz (2019), not a JEP and not a plan for any release. It proposes that a class
declares its own serial form, with `@Serializer` and `@Deserializer` members and an `open` modifier, and that the serial
form stays separate from the byte encoding.

**On JDK 25.** The library already works that way. A type declares its wire shape in a `TypeDescriptor`: read and write
functions held in a `public static final TypeDescriptor<T> DESCRIPTOR` field, or derived for free for records and enums.
The descriptor never reflects over fields, and the same descriptor feeds both the binary and the JSON format.

**If the proposal becomes a feature.** The `DESCRIPTOR` convention maps onto the proposed `@Serializer`/`@Deserializer`
annotations and `open` modifier. The library intends to follow, but no JEP exists yet, so nothing about the shape or the
timing is promised.

## Simple JSON API: a mirror of its surface

[JEP 540](https://openjdk.org/jeps/540) adds a small JSON API to the JDK: `JsonValue` and its kinds `JsonObject`,
`JsonArray`, `JsonString`, `JsonNumber`, `JsonBoolean` and `JsonNull`, parsed with `Json.parse`. It ships in JDK 28 as
the incubator module `jdk.incubator.json`.

**On JDK 25.** The library's JSON engine mirrors that surface under the same type names, so JSON documents work as
values today, with the API you will meet in the JDK.

**When the JDK's API is final.** The library intends to replace its engine with the JDK's own API once that API is final
and reaches the library's JDK floor. The intent is that the swap changes your imports of engine types, from the
library's package to the JDK's, and not the code that uses them; whether it can depends on the final shape of the JDK's
API.
