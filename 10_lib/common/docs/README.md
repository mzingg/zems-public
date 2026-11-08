# ZEMS Common Documentation

ZEMS Common holds the helpers the ZEMS libraries are built and tested with: JUnit tags that sort tests into contract and
journey tests, and a per-thread allocation measurement. The value library needs it only for its own tests, so you do not
need it to use value.

- [Setup](setup.md) — the JDK floor and the dependency.

## Tag contract and journey tests

`@ContractTest` marks a test class that pins the behaviour of exactly one production class. `@JourneyTest` marks a test
method that walks a workflow across several classes and names the requirement it covers:

```java
@ContractTest
class PriceTest {

  @Test
  void addsAmountsOfTheSameCurrency() {
    // ...
  }
}

class CheckoutJourneyTest {

  @Test
  @JourneyTest(speakingId = "checkout-flow", acceptance = "a1")
  void paysForACart() {
    // ...
  }
}
```

The tags are JUnit tags `contract` and `journey`, so Maven runs one kind alone with `mvn test -Dgroups=contract` or
`mvn test -Dgroups=journey`. `@JourneyTest` sits beside `@Test`; on its own it does not make a method a test. The ZEMS
build checks `speakingId` and `acceptance` against its own issue tracker; in your project they are free text that names
the requirement.

## Measure allocation

`AllocationMeasurement` measures the bytes the calling thread allocates and the time it takes to run a workload:

```java
var sample = AllocationMeasurement.measureMedian(5, 1_000, 7, () -> {
  for (int i = 0; i < 1_000; i++) {
    Integer.toString(i);
  }
});
System.out.println(sample.bytesPerIteration() + " bytes per iteration");
```

`iterations` (here `1_000`) is the loop count inside the workload; it only divides the totals into the per-iteration
figures.

## Decisions at first use

- **`measure` or `measureMedian`.** `measure` runs the workload once, cold; use it to assert an upper bound where a
  first run counts. `measureMedian` runs `warmup` rounds, then takes the median of `repeats`; use it for figures you
  compare across changes.
- **Which tag.** One production class under test → `@ContractTest` on the class; several classes in one workflow →
  `@JourneyTest` on the method.
