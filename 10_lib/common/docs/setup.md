# Setup

The library runs on JDK 25 or newer and has no runtime dependencies. Its helpers are for tests, so add it at test scope:

```xml
<dependency>
  <groupId>dev.zems.lib</groupId>
  <artifactId>common</artifactId>
  <version>0.0.14</version>
  <scope>test</scope>
</dependency>
```

The JUnit tags need JUnit Jupiter, which the library declares as optional: bring `junit-jupiter-api` into your own test
scope. In a named module, require it:

```java
requires dev.zems.lib.common;
```

`AllocationMeasurement` reads per-thread allocation through `com.sun.management.ThreadMXBean`, which OpenJDK-based JDKs
provide; on a JVM without it, the class fails to initialise.

Continue with the [Documentation](README.md).
