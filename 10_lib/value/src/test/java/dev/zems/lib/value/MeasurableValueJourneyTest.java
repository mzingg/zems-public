package dev.zems.lib.value;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;

import dev.zems.lib.common.junit.JourneyTest;
import dev.zems.lib.value.builtin.MeasurableValue;
import dev.zems.lib.value.junit.RoundTripFormatHarness;
import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("MeasurableValue — one value type for physical quantities and money, exact and unbounded")
class MeasurableValueJourneyTest {

  private static final String QUANTITY_NAME = "dev.zems.lib.value.Quantity";
  private static final Currency EUR = Currency.getInstance("EUR");

  static Stream<RoundTripFormatHarness.Format> formats() {
    return RoundTripFormatHarness.all();
  }

  @Test
  @JourneyTest(speakingId = "measurable-value-with-unit", acceptance = "unified")
  @DisplayName("a length and a price are both MeasurableValues read through the same API")
  void aLengthAndAPriceAreTheSameValueType() {
    Value<Quantity> distance = Value.of(5, Units.METRE);
    Value<Quantity> price = Value.of(new BigDecimal("9.99"), EUR);

    assertThat(distance).isInstanceOf(MeasurableValue.class);
    assertThat(price).isInstanceOf(MeasurableValue.class);
    assertThat(distance.isQuantity()).isTrue();
    assertThat(price.isQuantity()).isTrue();
    assertThat(distance.asQuantity()).hasValue(distance.unbox());
    assertThat(price.asQuantity()).hasValue(price.unbox());
    assertThat(distance.unbox().magnitude()).isEqualTo(new BigDecimal("5"));
    assertThat(distance.unbox().unit()).isEqualTo(Units.METRE);
    assertThat(price.unbox().magnitude()).isEqualTo(new BigDecimal("9.99"));
    assertThat(price.unbox().unit()).isEqualTo(Units.currency(EUR));
    assertThat(price).isEqualTo(Value.of(new BigDecimal("9.99"), Units.currency(EUR)));
    assertThat(Value.of("5 m").isQuantity()).isFalse();
    assertThat(Value.of("5 m").asQuantity()).isEmpty();
  }

  @Test
  @JourneyTest(speakingId = "measurable-value-with-unit", acceptance = "typed")
  @DisplayName("the six typed Value.of(magnitude, unit) overloads build equal values")
  void theSixTypedOverloadsBuildEqualValues() {
    Unit euro = Units.currency(EUR);

    Value<Quantity> metreFromLong = Value.of(7L, Units.METRE);
    Value<Quantity> metreFromBigInteger = Value.of(BigInteger.valueOf(7), Units.METRE);
    Value<Quantity> metreFromBigDecimal = Value.of(new BigDecimal("7"), Units.METRE);
    Value<Quantity> euroFromLong = Value.of(7L, EUR);
    Value<Quantity> euroFromBigInteger = Value.of(BigInteger.valueOf(7), EUR);
    Value<Quantity> euroFromBigDecimal = Value.of(new BigDecimal("7"), EUR);

    assertThat(metreFromBigInteger).isEqualTo(metreFromLong);
    assertThat(metreFromBigDecimal).isEqualTo(metreFromLong);
    assertThat(metreFromLong.unbox()).hasToString("7 m");
    assertThat(euroFromLong).isEqualTo(Value.of(7L, euro));
    assertThat(euroFromBigInteger).isEqualTo(Value.of(BigInteger.valueOf(7), euro));
    assertThat(euroFromBigDecimal).isEqualTo(Value.of(new BigDecimal("7"), euro));
    assertThat(euroFromBigInteger).isEqualTo(euroFromLong);
    assertThat(euroFromBigDecimal).isEqualTo(euroFromLong);
    assertThat(euroFromLong.unbox()).hasToString("7 EUR");
  }

  @Test
  @JourneyTest(speakingId = "measurable-value-with-unit", acceptance = "typed")
  @DisplayName("no public method on Unit or Units takes a String")
  void noPublicUnitMethodTakesAString() {
    List<String> stringTaking = Stream.of(Unit.class, Units.class)
      .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
      .filter(method -> Modifier.isPublic(method.getModifiers()))
      .filter(method -> Arrays.asList(method.getParameterTypes()).contains(String.class))
      .map(Method::toString)
      .toList();

    assertThat(stringTaking).isEmpty();
  }

  @Test
  @JourneyTest(speakingId = "measurable-value-with-unit", acceptance = "catalogue")
  @DisplayName("every catalogue unit constant has its SI, non-SI or IEC symbol and reads back from it")
  void everyUnitConstantHasItsSymbol() {
    Map<Unit, String> symbols = Map.ofEntries(
      Map.entry(Units.METRE, "m"),
      Map.entry(Units.KILOGRAM, "kg"),
      Map.entry(Units.SECOND, "s"),
      Map.entry(Units.AMPERE, "A"),
      Map.entry(Units.KELVIN, "K"),
      Map.entry(Units.MOLE, "mol"),
      Map.entry(Units.CANDELA, "cd"),
      Map.entry(Units.GRAM, "g"),
      Map.entry(Units.RADIAN, "rad"),
      Map.entry(Units.STERADIAN, "sr"),
      Map.entry(Units.HERTZ, "Hz"),
      Map.entry(Units.NEWTON, "N"),
      Map.entry(Units.PASCAL, "Pa"),
      Map.entry(Units.JOULE, "J"),
      Map.entry(Units.WATT, "W"),
      Map.entry(Units.COULOMB, "C"),
      Map.entry(Units.VOLT, "V"),
      Map.entry(Units.FARAD, "F"),
      Map.entry(Units.OHM, "\u03A9"),
      Map.entry(Units.SIEMENS, "S"),
      Map.entry(Units.WEBER, "Wb"),
      Map.entry(Units.TESLA, "T"),
      Map.entry(Units.HENRY, "H"),
      Map.entry(Units.DEGREE_CELSIUS, "\u00B0C"),
      Map.entry(Units.LUMEN, "lm"),
      Map.entry(Units.LUX, "lx"),
      Map.entry(Units.BECQUEREL, "Bq"),
      Map.entry(Units.GRAY, "Gy"),
      Map.entry(Units.SIEVERT, "Sv"),
      Map.entry(Units.KATAL, "kat"),
      Map.entry(Units.MINUTE, "min"),
      Map.entry(Units.HOUR, "h"),
      Map.entry(Units.DAY, "d"),
      Map.entry(Units.LITRE, "L"),
      Map.entry(Units.TONNE, "t"),
      Map.entry(Units.BIT, "bit"),
      Map.entry(Units.BYTE, "B")
    );

    assertThat(symbols).hasSize(37);
    symbols.forEach((unit, symbol) -> {
      assertThat(unit).as(symbol).hasToString(symbol);
      Value<Quantity> read = Value.measurableOf("1 " + symbol);
      assertThat(read).as(symbol).isPresent();
      assertThat(read.unbox().unit()).as(symbol).isEqualTo(unit);
    });
  }

  @Test
  @JourneyTest(speakingId = "measurable-value-with-unit", acceptance = "catalogue")
  @DisplayName("the 24 SI prefixes attach to the metre and the 10 binary prefixes to the byte")
  void theSiAndBinaryPrefixesAttach() {
    Map<Prefix, String> siPrefixes = Map.ofEntries(
      Map.entry(Prefix.QUECTO, "q"),
      Map.entry(Prefix.RONTO, "r"),
      Map.entry(Prefix.YOCTO, "y"),
      Map.entry(Prefix.ZEPTO, "z"),
      Map.entry(Prefix.ATTO, "a"),
      Map.entry(Prefix.FEMTO, "f"),
      Map.entry(Prefix.PICO, "p"),
      Map.entry(Prefix.NANO, "n"),
      Map.entry(Prefix.MICRO, "\u03BC"),
      Map.entry(Prefix.MILLI, "m"),
      Map.entry(Prefix.CENTI, "c"),
      Map.entry(Prefix.DECI, "d"),
      Map.entry(Prefix.DECA, "da"),
      Map.entry(Prefix.HECTO, "h"),
      Map.entry(Prefix.KILO, "k"),
      Map.entry(Prefix.MEGA, "M"),
      Map.entry(Prefix.GIGA, "G"),
      Map.entry(Prefix.TERA, "T"),
      Map.entry(Prefix.PETA, "P"),
      Map.entry(Prefix.EXA, "E"),
      Map.entry(Prefix.ZETTA, "Z"),
      Map.entry(Prefix.YOTTA, "Y"),
      Map.entry(Prefix.RONNA, "R"),
      Map.entry(Prefix.QUETTA, "Q")
    );
    Map<Prefix, String> binaryPrefixes = Map.of(
      Prefix.KIBI,
      "Ki",
      Prefix.MEBI,
      "Mi",
      Prefix.GIBI,
      "Gi",
      Prefix.TEBI,
      "Ti",
      Prefix.PEBI,
      "Pi",
      Prefix.EXBI,
      "Ei",
      Prefix.ZEBI,
      "Zi",
      Prefix.YOBI,
      "Yi",
      Prefix.ROBI,
      "Ri",
      Prefix.QUEBI,
      "Qi"
    );

    assertThat(Prefix.values()).hasSize(34);
    siPrefixes.forEach((prefix, symbol) -> {
      Unit prefixed = Units.METRE.withPrefix(prefix);
      assertThat(prefix.symbol()).isEqualTo(symbol);
      assertThat(prefixed)
        .as(symbol)
        .hasToString(symbol + "m");
      assertThat(
        Value.measurableOf("1 " + symbol + "m")
          .unbox()
          .unit()
      )
        .as(symbol)
        .isEqualTo(prefixed);
    });
    binaryPrefixes.forEach((prefix, symbol) -> {
      Unit prefixed = Units.BYTE.withPrefix(prefix);
      assertThat(prefix.symbol()).isEqualTo(symbol);
      assertThat(prefixed)
        .as(symbol)
        .hasToString(symbol + "B");
      assertThat(
        Value.measurableOf("1 " + symbol + "B")
          .unbox()
          .unit()
      )
        .as(symbol)
        .isEqualTo(prefixed);
    });
  }

  @Test
  @JourneyTest(speakingId = "measurable-value-with-unit", acceptance = "catalogue")
  @DisplayName("a sample of every group reads back to the unit built from the catalogue")
  void aSampleOfEveryGroupReadsBack() {
    Map<String, Unit> samples = Map.ofEntries(
      Map.entry("kg", Units.GRAM.withPrefix(Prefix.KILO)),
      Map.entry("mg", Units.GRAM.withPrefix(Prefix.MILLI)),
      Map.entry("\u00B0C", Units.DEGREE_CELSIUS),
      Map.entry("mL", Units.LITRE.withPrefix(Prefix.MILLI)),
      Map.entry("kt", Units.TONNE.withPrefix(Prefix.KILO)),
      Map.entry("KiB", Units.BYTE.withPrefix(Prefix.KIBI)),
      Map.entry("QiB", Units.BYTE.withPrefix(Prefix.QUEBI)),
      Map.entry("Mbit", Units.BIT.withPrefix(Prefix.MEGA)),
      Map.entry("kbit", Units.BIT.withPrefix(Prefix.KILO)),
      Map.entry("min", Units.MINUTE),
      Map.entry("XAU", Units.currency(Currency.getInstance("XAU")))
    );

    samples.forEach((symbol, unit) -> {
      Value<Quantity> read = Value.measurableOf("3 " + symbol);
      assertThat(read).as(symbol).isInstanceOf(MeasurableValue.class);
      assertThat(read.unbox().unit()).as(symbol).isEqualTo(unit);
      assertThat(read.unbox())
        .as(symbol)
        .hasToString("3 " + symbol);
    });
  }

  @Test
  @JourneyTest(speakingId = "measurable-value-with-unit", acceptance = "catalogue")
  @DisplayName("the kilogram is the gram with the kilo prefix")
  void theKilogramIsThePrefixedGram() {
    Unit kilogram = Units.GRAM.withPrefix(Prefix.KILO);

    assertThat(kilogram).isEqualTo(Units.KILOGRAM);
    assertThat(kilogram).hasSameHashCodeAs(Units.KILOGRAM);
    assertThat(kilogram).hasToString("kg");
  }

  @Test
  @JourneyTest(speakingId = "measurable-value-with-unit", acceptance = "catalogue")
  @DisplayName("symbols outside the prefix rules are unknown: ft, dB, kmin, Kim, kkg")
  void symbolsOutsideThePrefixRulesAreUnknown() {
    for (String symbol : List.of("ft", "dB", "kmin", "Kim", "kkg")) {
      assertThat(Value.measurableOf("1 " + symbol))
        .as(symbol)
        .isError()
        .hasErrorMessage("Invalid quantity '1 " + symbol + "': unknown unit symbol '" + symbol + "'")
        .hasExpectedTypeName(QUANTITY_NAME);
    }
  }

  @Test
  @JourneyTest(speakingId = "measurable-value-with-unit", acceptance = "catalogue")
  @DisplayName("every ISO 4217 currency of the running JDK is a unit and round-trips through its code")
  void everyJdkCurrencyRoundTrips() {
    for (Currency currency : Currency.getAvailableCurrencies()) {
      String code = currency.getCurrencyCode();
      Value<Quantity> amount = Value.of(1, currency);

      assertThat(amount.unbox())
        .as(code)
        .hasToString("1 " + code);
      assertThat(Value.measurableOf("1 " + code))
        .as(code)
        .isEqualTo(amount);
      assertThat(Units.currency(currency)).as(code).hasToString(code);
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "measurable-value-with-unit", acceptance = "compound")
  @DisplayName("compound units built by typed operations are held and round-trip through both forms")
  void compoundUnitsRoundTripThroughBothForms(RoundTripFormatHarness.Format format) {
    Unit speed = Units.METRE.divide(Units.SECOND);
    Unit force = Units.KILOGRAM.multiply(Units.METRE).divide(Units.SECOND.pow(2));
    Unit pricePerKilo = Units.currency(EUR).divide(Units.KILOGRAM);
    Map<Unit, String> texts = Map.of(speed, "10 m/s", force, "10 kg*m/s^2", pricePerKilo, "10 EUR/kg");

    texts.forEach((unit, text) -> {
      Value<Quantity> original = Value.of(10, unit);

      Value<Quantity> fromWire = format.read(
        format.write(original, BuiltinTypeDescriptors.QUANTITY),
        BuiltinTypeDescriptors.QUANTITY
      );
      Value<Quantity> fromText = Value.measurableOf(original.unbox().toString());

      assertThat(original).as(text).isInstanceOf(MeasurableValue.class);
      assertThat(original.unbox()).as(text).hasToString(text);
      assertThat(fromWire).as(text).isEqualTo(original);
      assertThat(fromWire.unbox().unit()).as(text).isEqualTo(unit);
      assertThat(fromText).as(text).isEqualTo(original);
      assertThat(fromText.unbox().unit()).as(text).isEqualTo(unit);
    });
  }

  @Test
  @JourneyTest(speakingId = "measurable-value-with-unit", acceptance = "grammar")
  @DisplayName("a built unit has one canonical spelling, whatever the construction order")
  void aBuiltUnitHasOneCanonicalSpelling() {
    assertThat(Units.KILOGRAM.multiply(Units.METRE).divide(Units.SECOND.pow(2))).hasToString("kg*m/s^2");
    assertThat(Units.METRE.divide(Units.SECOND.pow(2)).multiply(Units.KILOGRAM)).hasToString("kg*m/s^2");
    assertThat(Units.METRE.divide(Units.KILOGRAM).divide(Units.SECOND)).hasToString("m/kg/s");
    assertThat(Units.SECOND.pow(-1)).hasToString("s^-1");
    assertThat(Units.KILOGRAM.multiply(Units.METRE.pow(2)).pow(-1)).hasToString("kg^-1*m^-2");
    assertThat(Units.METRE.pow(2)).hasToString("m^2");
    assertThat(Units.METRE.multiply(Units.METRE)).isEqualTo(Units.METRE.pow(2));
    assertThat(Units.SECOND.multiply(Units.METRE)).hasToString("m*s");
    assertThat(Units.SECOND.multiply(Units.METRE)).isEqualTo(Units.METRE.multiply(Units.SECOND));
    assertThat(Units.currency(EUR).divide(Units.KILOGRAM)).hasToString("EUR/kg");
    assertThat(Value.of(5, Units.METRE.divide(Units.SECOND)).unbox()).hasToString("5 m/s");
  }

  @Test
  @JourneyTest(speakingId = "measurable-value-with-unit", acceptance = "grammar")
  @DisplayName("a well-formed but non-canonical expression reads and normalises")
  void aNonCanonicalExpressionNormalises() {
    Map<String, Unit> normalised = Map.of(
      "s*m",
      Units.METRE.multiply(Units.SECOND),
      "m^1",
      Units.METRE,
      "m*m",
      Units.METRE.pow(2),
      "m*s/s",
      Units.METRE,
      "m*s^-1",
      Units.METRE.divide(Units.SECOND)
    );

    normalised.forEach((expression, unit) -> {
      Value<Quantity> read = Value.measurableOf("2 " + expression);
      assertThat(read.unbox().unit()).as(expression).isEqualTo(unit);
      assertThat(read.unbox())
        .as(expression)
        .hasToString("2 " + unit);
    });
    assertThat(Value.measurableOf("2 s*m").unbox()).hasToString("2 m*s");
    assertThat(Value.measurableOf("2 m*s^-1").unbox()).hasToString("2 m/s");
  }

  @Test
  @JourneyTest(speakingId = "measurable-value-with-unit", acceptance = "grammar")
  @DisplayName("the micro sign and the ohm sign read as their canonical Greek letters")
  void microAndOhmAliasesReadCanonically() {
    Value<Quantity> microSign = Value.measurableOf("3 \u00B5m");
    Value<Quantity> ohmSign = Value.measurableOf("3 \u2126");

    assertThat(microSign).isEqualTo(Value.measurableOf("3 \u03BCm"));
    assertThat(microSign).isEqualTo(Value.of(3, Units.METRE.withPrefix(Prefix.MICRO)));
    assertThat(microSign.unbox()).hasToString("3 \u03BCm");
    assertThat(ohmSign).isEqualTo(Value.of(3, Units.OHM));
    assertThat(ohmSign.unbox()).hasToString("3 \u03A9");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "measurable-value-with-unit", acceptance = "exact")
  @DisplayName("magnitude, scale and unit are identical after the wire and the string round trip")
  void magnitudeAndScaleSurviveBothRoundTrips(RoundTripFormatHarness.Format format) {
    List<Value<Quantity>> originals = List.of(
      Value.of(5, Units.METRE.withPrefix(Prefix.KILO)),
      Value.of(new BigDecimal("9.99"), EUR),
      Value.of(10, Units.METRE.divide(Units.SECOND)),
      Value.of(new BigDecimal("4.50"), Units.currency(EUR).divide(Units.KILOGRAM)),
      Value.of(new BigDecimal("1E+3"), Units.METRE)
    );
    List<String> texts = List.of("5 km", "9.99 EUR", "10 m/s", "4.50 EUR/kg", "1E+3 m");

    for (int i = 0; i < originals.size(); i++) {
      Value<Quantity> original = originals.get(i);
      String text = texts.get(i);

      Value<Quantity> fromWire = format.read(
        format.write(original, BuiltinTypeDescriptors.QUANTITY),
        BuiltinTypeDescriptors.QUANTITY
      );
      Value<Quantity> fromText = Value.measurableOf(original.unbox().toString());

      assertThat(original.unbox()).as(text).hasToString(text);
      for (Value<Quantity> reread : List.of(fromWire, fromText)) {
        assertThat(reread.unbox().magnitude()).as(text).isEqualTo(original.unbox().magnitude());
        assertThat(reread.unbox().magnitude().scale()).as(text).isEqualTo(original.unbox().magnitude().scale());
        assertThat(reread.unbox().unit()).as(text).isEqualTo(original.unbox().unit());
        assertThat(reread.unbox()).as(text).hasToString(text);
      }
    }
    assertThat(Value.of(new BigDecimal("4.50"), EUR).unbox().magnitude().scale()).isEqualTo(2);
    assertThat(Value.measurableOf("1E+3 m").unbox().magnitude().scale()).isEqualTo(-3);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "measurable-value-with-unit", acceptance = "unbounded")
  @DisplayName("magnitudes far outside long and double range stay exact through both forms")
  void hugeAndTinyMagnitudesStayExact(RoundTripFormatHarness.Format format) {
    BigInteger tenToTheForty = new BigInteger("1" + "0".repeat(40));
    BigDecimal tiny = new BigDecimal("1E-2147483647");
    Map<Value<Quantity>, String> originals = Map.of(
      Value.of(tenToTheForty, Units.METRE),
      "1" + "0".repeat(40) + " m",
      Value.of(tiny, Units.METRE),
      "1E-2147483647 m"
    );

    originals.forEach((original, text) -> {
      Value<Quantity> fromWire = format.read(
        format.write(original, BuiltinTypeDescriptors.QUANTITY),
        BuiltinTypeDescriptors.QUANTITY
      );
      Value<Quantity> fromText = Value.measurableOf(original.unbox().toString());

      assertThat(original.unbox()).as(text).hasToString(text);
      for (Value<Quantity> reread : List.of(fromWire, fromText)) {
        assertThat(reread.unbox().magnitude()).as(text).isEqualTo(original.unbox().magnitude());
        assertThat(reread.unbox()).as(text).hasToString(text);
      }
    });
    assertThat(Value.of(tenToTheForty, Units.METRE).unbox().magnitude()).isEqualTo(new BigDecimal(tenToTheForty));
    assertThat(Value.of(tiny, Units.METRE).unbox().magnitude().scale()).isEqualTo(Integer.MAX_VALUE);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "measurable-value-with-unit", acceptance = "representation")
  @DisplayName("long, BigInteger and BigDecimal construction give equal values, equal text and equal wire bytes")
  void theRepresentationDoesNotShowInEqualityOrWireForm(RoundTripFormatHarness.Format format) {
    Value<Quantity> fromLong = Value.of(5L, Units.METRE);
    Value<Quantity> fromBigInteger = Value.of(BigInteger.valueOf(5), Units.METRE);
    Value<Quantity> fromBigDecimal = Value.of(new BigDecimal("5"), Units.METRE);

    assertThat(fromBigInteger).isEqualTo(fromLong);
    assertThat(fromBigDecimal).isEqualTo(fromLong);
    assertThat(fromBigInteger).hasSameHashCodeAs(fromLong);
    assertThat(fromBigDecimal).hasSameHashCodeAs(fromLong);
    assertThat(fromLong.unbox()).hasToString("5 m");
    assertThat(fromBigInteger.unbox()).hasToString("5 m");
    assertThat(fromBigDecimal.unbox()).hasToString("5 m");
    byte[] longWire = format.write(fromLong, BuiltinTypeDescriptors.QUANTITY);
    assertThat(format.write(fromBigInteger, BuiltinTypeDescriptors.QUANTITY)).isEqualTo(longWire);
    assertThat(format.write(fromBigDecimal, BuiltinTypeDescriptors.QUANTITY)).isEqualTo(longWire);
  }

  @Test
  @JourneyTest(speakingId = "measurable-value-with-unit", acceptance = "representation")
  @DisplayName("5 m equals 5.0 m and hashes alike, while each keeps its text as written")
  void equalityIsNumericAndTheTextIsKept() {
    Value<Quantity> five = Value.of(5, Units.METRE);
    Value<Quantity> fivePointZero = Value.of(new BigDecimal("5.0"), Units.METRE);

    assertThat(fivePointZero).isEqualTo(five);
    assertThat(fivePointZero).hasSameHashCodeAs(five);
    assertThat(fivePointZero.unbox()).isEqualTo(five.unbox());
    assertThat(fivePointZero.unbox()).hasSameHashCodeAs(five.unbox());
    assertThat(five.unbox()).hasToString("5 m");
    assertThat(fivePointZero.unbox()).hasToString("5.0 m");
    assertThat(Value.measurableOf("5.0 m")).isEqualTo(Value.measurableOf("5 m"));
    assertThat(Value.of(5, Units.SECOND)).isNotEqualTo(five);
  }

  @Test
  @JourneyTest(speakingId = "measurable-value-with-unit", acceptance = "unknown")
  @DisplayName("an unknown unit, an unknown currency code or a malformed expression reads as an ErrorValue naming it")
  void unknownOrMalformedTextIsAnErrorValue() {
    assertThat(Value.measurableOf("5 furlong"))
      .isError()
      .hasErrorMessage("Invalid quantity '5 furlong': unknown unit symbol 'furlong'")
      .hasExpectedTypeName(QUANTITY_NAME);
    assertThat(Value.measurableOf("5 XYZ"))
      .isError()
      .hasErrorMessage("Invalid quantity '5 XYZ': unknown unit symbol 'XYZ'")
      .hasExpectedTypeName(QUANTITY_NAME);
    assertThat(Value.measurableOf("5 eur"))
      .isError()
      .hasErrorMessage("Invalid quantity '5 eur': unknown unit symbol 'eur'")
      .hasExpectedTypeName(QUANTITY_NAME);
    assertThat(Value.measurableOf("5 m/"))
      .isError()
      .hasErrorMessage("Invalid quantity '5 m/': malformed unit expression 'm/'")
      .hasExpectedTypeName(QUANTITY_NAME);
    assertThat(Value.measurableOf("5 m^0"))
      .isError()
      .hasErrorMessage("Invalid quantity '5 m^0': malformed unit expression 'm^0'")
      .hasExpectedTypeName(QUANTITY_NAME);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "measurable-value-with-unit", acceptance = "unknown")
  @DisplayName("an unknown unit read from the wire is an ErrorValue, not an exception")
  void anUnknownUnitFromTheWireIsAnErrorValue(RoundTripFormatHarness.Format format) {
    byte[] wire = format.write(Value.of("5 furlong"), quantityWireText());

    Value<Quantity> read = format.read(wire, BuiltinTypeDescriptors.QUANTITY);

    assertThat(read)
      .isError()
      .hasErrorMessage("Invalid quantity '5 furlong': unknown unit symbol 'furlong'")
      .hasExpectedTypeName(QUANTITY_NAME);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  @JourneyTest(speakingId = "measurable-value-with-unit", acceptance = "unknown")
  @DisplayName("in a list read from the wire, only the bad elements become ErrorValues")
  void badListElementsFromTheWireArePerElementErrorValues(RoundTripFormatHarness.Format format) {
    byte[] wire = format.write(
      Value.listOf("5 m", "5 furlong", "5 m^0"),
      TypeDescriptor.ofList("quantities", quantityWireText())
    );

    Value<List<Value<Quantity>>> read = format.read(
      wire,
      TypeDescriptor.ofList("quantities", BuiltinTypeDescriptors.QUANTITY)
    );

    List<Value<Quantity>> elements = read.unbox();
    assertThat(elements).hasSize(3);
    assertThat(elements.getFirst()).isEqualTo(Value.of(5, Units.METRE));
    assertThat(elements.get(1))
      .isError()
      .hasErrorMessage("Invalid quantity '5 furlong': unknown unit symbol 'furlong'")
      .hasExpectedTypeName(QUANTITY_NAME);
    assertThat(elements.get(2))
      .isError()
      .hasErrorMessage("Invalid quantity '5 m^0': malformed unit expression 'm^0'")
      .hasExpectedTypeName(QUANTITY_NAME);
  }

  @Test
  @JourneyTest(speakingId = "measurable-value-with-unit", acceptance = "systems")
  @DisplayName("a unit reports the systems of its parts: physical, digital information, monetary or a mix")
  void aUnitReportsItsSystems() {
    assertThat(UnitSystem.values()).containsExactly(
      UnitSystem.PHYSICAL,
      UnitSystem.DIGITAL_INFORMATION,
      UnitSystem.MONETARY
    );
    assertThat(Units.METRE.divide(Units.SECOND).systems()).containsExactly(UnitSystem.PHYSICAL);
    assertThat(Units.BYTE.withPrefix(Prefix.KIBI).systems()).containsExactly(UnitSystem.DIGITAL_INFORMATION);
    assertThat(Units.currency(EUR).systems()).containsExactly(UnitSystem.MONETARY);
    assertThat(Units.currency(EUR).divide(Units.KILOGRAM).systems()).containsExactlyInAnyOrder(
      UnitSystem.MONETARY,
      UnitSystem.PHYSICAL
    );
  }

  private static TypeDescriptor<String> quantityWireText() {
    return TypeDescriptor.of(
      QUANTITY_NAME,
      String.class,
      reader -> reader.readString(0, "value"),
      (writer, text) -> writer.writeString(0, "value", text)
    );
  }
}
