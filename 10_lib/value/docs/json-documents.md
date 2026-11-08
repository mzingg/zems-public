# JSON Documents

For JSON that is data rather than a typed record graph — a thread dump, a GeoJSON file, a benchmark result —
`Value.parseJson` parses strict RFC 8259 text into a `Value<JsonValue>`: one JSON document value, read like any other
value. Nothing here throws on data. A parse error is an `ErrorValue` naming the line and column, a JSON `null` is
`Value.nullValue()`, and `navigate()` walks members and elements, carrying the first failure, with its path and
location, to the exit:

```java
Value<JsonValue> root = Value.parseJson("""
    {"service": "tree-browser", "ports": [8080, 8443], "tls": {"enabled": true}, "region": null}""");

root.navigate().get("service").convertTo(String.class);        // Value.of("tree-browser")
root.navigate().get("ports").get(1).convertTo(Integer.class);  // Value.of(8443)
root.navigate().get("tls").get("enabled").value();             // Value.jsonOf(true)
root.navigate().get("region").value();                         // Value.nullValue() — a JSON null is the null marker

root.navigate().get("ports").get(2).value();
// ErrorValue: $.ports: index 2 is out of range for an array of 2 elements (line 1, column 38)
root.navigate().get("tls").get("enabled").get("x").convertTo(Integer.class);
// ErrorValue<Integer>: $.tls.enabled: cannot step to member 'x' of a boolean (line 1, column 71)

Value.parseJson("{\"a\": 1,}");
// ErrorValue: cannot parse the JSON at line 1, column 9 ($): the object ends with a trailing comma, which JSON does
//   not allow; remove the comma
```

## Numbers keep their text

Numbers convert through `convertTo` like every other value: losslessly per value, decided on the digits, and at once
however large the exponent:

```java
Navigator numbers = Value.parseJson("[123.0, 345.6, 1e999999999999, 3.14159265358979323846]").navigate();

numbers.get(0).convertTo(Integer.class);     // Value.of(123) — whole, so it converts
numbers.get(1).convertTo(Integer.class);
// ErrorValue: Cannot convert JsonNumber to Integer: 345.6 is not a whole number
numbers.get(1).convertTo(BigDecimal.class);  // 345.6 — every digit
numbers.get(2).convertTo(Long.class);
// ErrorValue, at once: Cannot convert JsonNumber to Long: 1e999999999999 overflows Long
numbers.get(3).convertTo(Double.class);      // Value.of(3.141592653589793) — the nearest double
numbers.get(3).value().toString();           // 3.14159265358979323846 — the text as written
```

## Building a document

Documents build through one overloaded `Value.jsonOf`, which takes only `Value<JsonValue>` members — a member that is
not JSON does not compile — and never parses a `String`: `jsonOf("1815")` is the JSON string `"1815"`. Built and parsed
documents compare by value (numbers numerically, objects in any member order) and emit in member order; `JsonText` gives
the indented form:

```java
var members = new LinkedHashMap<String, Value<JsonValue>>();
members.put("name", Value.jsonOf("Ada"));
members.put("born", Value.jsonOf(1815));
members.put("tags", Value.jsonOf(List.of(Value.jsonOf("math"), Value.jsonOf("engines"))));
Value<JsonValue> doc = Value.jsonOf(members);
Value.jsonOf(List.of(Value.jsonOf("math"), Value.of(42)));   // does not compile: a Value<Integer> is not a JSON member

doc.toString();                // {"name":"Ada","born":1815,"tags":["math","engines"]}
JsonText.indented(doc, "  ");  // Value<String>: the same document, one member per line, indented two spaces
doc.equals(Value.parseJson("{\"tags\":[\"math\",\"engines\"],\"born\":1815.0,\"name\":\"Ada\"}"));  // true
Value.jsonOf("1815").equals(Value.jsonOf(1815));  // false — "1815" is the JSON string, never parsed as a number
```

## The kind at compile time

`navigate()` is the way in for structure. When a caller wants the kind at compile time, JSON is one record branch of the
sealed `Value` family whose component is the engine value, so a record pattern with a nested engine pattern reaches any
kind in one step, and `unbox()` answers the engine value itself; nothing is copied or wrapped per level:

```java
String kind = switch (doc) {
  case JsonDocumentValue(JsonObject _) -> "object";
  case JsonDocumentValue(JsonArray _) -> "array";
  case JsonDocumentValue _ -> "leaf";
  case NullValue<?> _ -> "null";
  default -> "broken";
};                                     // "object"

JsonValue engine = doc.unbox();        // the engine value, not a copy
engine.get("tags").get(0).asString();  // "math" — the engine's own accessors, which throw on a missing member or kind

Value.urlOf("https://zems.dev").navigate().get("host").value();  // UnsupportedValue: UrlValue is not navigable
```

## Choosing

**`Value.parseJson` or `ValueIo`.** `parseJson` when the JSON is data you read by path and convert leaf by leaf: a file
from another tool, a payload whose shape you do not own. `ValueIo` when the JSON is the wire form of your own records,
typed by a descriptor and read back as the record ([Marshalling with ValueIo](marshalling.md)). The two are different
parsers with different jobs.

**`navigate()`, a record pattern or `unbox()`.** `navigate()` for a path into the structure that may fail anywhere: it
carries the first failure to the exit, with path, line and column, and nothing throws. A record pattern
(`case JsonDocumentValue(JsonObject o)`) when the kind must be known at compile time. `unbox()` when you hand the engine
value to code written against the engine's own API; its accessors throw on a missing member or a kind mismatch, as the
JDK's will.

**Strings and numbers.** `jsonOf(String)` always builds a JSON string, so `jsonOf("1815")` can never be read as a
number; build a number from a `long`, `double`, `BigInteger` or `BigDecimal`. Read a number with `convertTo` and the
target you mean: `Integer.class` refuses a fraction or an overflow, `BigDecimal.class` keeps every digit, `Double.class`
gives the nearest double.

The engine mirrors the JDK's Simple JSON API; the direction page says what the swap will mean for your imports:
[Simple JSON API](jdk-directions.md#simple-json-api-a-mirror-of-its-surface).
