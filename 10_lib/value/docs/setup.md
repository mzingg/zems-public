# Setup

The library runs on JDK 25 or newer and has no runtime dependencies.

Add the dependency:

```xml
<dependency>
  <groupId>dev.zems.lib</groupId>
  <artifactId>value</artifactId>
  <version>0.0.13</version>
</dependency>
```

In a named module, require it:

```java
requires dev.zems.lib.value;
```

The test helpers (`ValueAssertions`, `RoundTripFormatHarness`) ship in the same jar and need AssertJ and JUnit, which
the library declares as optional; add them to your own test scope as [Testing with ValueAssertions](testing.md) shows.

Continue with [Getting started](getting-started.md).
