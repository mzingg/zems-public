# Quantities and Money

A number with its unit is one value type, a physical quantity and a monetary amount alike. Units are typed constants
from a closed catalogue (the SI, the accepted non-SI units, bits and bytes, every ISO 4217 currency), combined through
typed operations, never parsed from caller strings; the magnitude is exact and unbounded:

```java
Value<Quantity> distance = Value.of(5, Units.METRE.withPrefix(Prefix.KILO));                 // 5 km
Value<Quantity> price = Value.of(new BigDecimal("9.99"), Currency.getInstance("EUR"));        // 9.99 EUR — same type
Value<Quantity> perKilo = Value.of(new BigDecimal("4.50"),
    Units.currency(Currency.getInstance("EUR")).divide(Units.KILOGRAM));                      // 4.50 EUR/kg

perKilo.unbox().toString();                          // "4.50 EUR/kg" — the canonical form, also the wire form
Value.measurableOf("4.50 EUR/kg").equals(perKilo);   // true — the text alone parses back, scale included
Value.of(new BigDecimal("5.0"), Units.METRE).equals(Value.of(5, Units.METRE)); // true — equality is numeric

Value.measurableOf("5 furlong");  // ErrorValue "Invalid quantity '5 furlong': unknown unit symbol 'furlong'"
```

## Choosing

**`Units` constants or `measurableOf(text)`.** Build from the constants when your code knows the unit: `Units.METRE`,
`Units.GRAM.withPrefix(Prefix.MILLI)`, `Units.currency(eur)`, combined with `multiply`, `divide` and `pow`. The compiler
then rejects a unit the catalogue does not have; a prefix goes on a single unprefixed unit only, so
`Units.KILOGRAM.withPrefix(...)` throws `IllegalArgumentException`. Parse with `Value.measurableOf(text)` when the text
comes from outside, the wire or a configuration file; an unknown unit symbol is an `ErrorValue`, never an exception, and
the text parses back to the same value, scale included.

**Money.** An amount of money is a quantity whose unit is a currency: `Value.of(amount, Currency)` is the short form of
`Value.of(amount, Units.currency(currency))`. Compound units such as a price per kilogram follow from the same
operations. Give the magnitude as a `BigDecimal` to keep the scale as written; equality is numeric either way (`5.0 m`
equals `5 m`), and each renders as written.

**Catalogue limits.** Tonne, bit and byte take only the multiple prefixes from kilo up (bit and byte also the binary
ones), so `ft` and `dB` never read as units. The catalogue is closed: a unit it does not hold is a library change, not a
registration.
