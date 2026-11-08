# Versions

A version carries its dialect — SemVer 2.0.0, JDK version strings (`Runtime.Version`) or JPMS module versions
(`ModuleDescriptor.Version`) — and parses, orders and compares by that dialect's rules. Precedence is its own question,
separate from equality, and two dialects never compare:

```java
Value<Version> need = Value.versionOf("1.0.0", VersionDialect.SEMVER);
Value<Version> have = Value.versionOf("1.4.0-rc.1+build.7", VersionDialect.SEMVER);
Value<Version> rc = Value.versionOf("1.0.0-rc.1", VersionDialect.SEMVER);

Versions.atLeast(have, need);   // Value.of(true) — SemVer precedence, build metadata ignored
Versions.atLeast(rc, need);     // Value.of(false) — a pre-release sorts before its release

Value.versionOf("1.4", VersionDialect.SEMVER); // ErrorValue "Invalid SemVer version '1.4': expected MAJOR.MINOR.PATCH"
Value.versionOf("1.4", VersionDialect.MODULE); // a version — JPMS accepts it, and 1.4 equals 1.4.0 there

Versions.atLeast(have, Value.versionOf("1.0", VersionDialect.MODULE));
// UnsupportedValue "Cannot compare a SemVer version with a JPMS module version: dialects differ"

have.unbox().toString();                                              // "semver:1.4.0-rc.1+build.7" — the qualified form
Value.qualifiedVersionOf("semver:1.4.0-rc.1+build.7").equals(have);   // true — the string alone parses back
```

## Choosing

**Which dialect.** `SEMVER` for anything you publish and compare by SemVer 2.0.0 rules: pre-releases sort before their
release, build metadata is ignored. `JDK` for `java.version`-shaped strings, where the JDK's own `Runtime.Version` rules
hold. `MODULE` for JPMS module versions, where `1.4` equals `1.4.0`. The dialect set is closed: a bare string names no
dialect, so there is no `versionOf(String)` without one.

**`equals` or `Versions.atLeast`.** `equals` asks whether two versions are the same version under their dialect.
`Versions.atLeast(have, need)` and `Versions.comparePrecedence(left, right)` ask how they order, and answer a `Value`:
`Value.of(true)` or `Value.of(false)`, or an `UnsupportedValue` when the dialects differ, because that question has no
answer, not a wrong one.

**Qualified or bare text.** The qualified form `semver:1.4.0` is `Version.toString()`, the wire form, and what
`Value.qualifiedVersionOf(String)` parses back; use it wherever the text must carry its own dialect. The bare text
`1.4.0` is what `convertTo(String.class)` renders and what `versionOf(text, dialect)` reads, for places where the
dialect is known from context.
