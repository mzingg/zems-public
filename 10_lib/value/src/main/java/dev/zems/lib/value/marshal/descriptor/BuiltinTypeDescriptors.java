package dev.zems.lib.value.marshal.descriptor;

import dev.zems.lib.value.marshal.StateReader;
import dev.zems.lib.value.marshal.StateWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Pre-built {@link TypeDescriptor} constants for JDK wrapper types.
 *
 * <p>
 * Each descriptor reads/writes a single field named {@value #SLOT} using the corresponding primitive read/write methods
 * on {@link StateReader}/{@link StateWriter}, or — for non-primitive JDK types that have a canonical string form — via
 * {@code readString} / {@code writeString} with the type's documented parser ({@code Instant.parse},
 * {@code UUID.fromString}, {@code new BigDecimal(s)}, etc.).
 *
 * <p>
 * Each constant's {@link TypeDescriptor#descriptorName()} is the JDK type's qualified name ({@code "java.lang.String"},
 * {@code "java.time.Instant"}, etc.).
 *
 * <p>
 * Use {@link #find(Class)} to look up a descriptor by class, or {@link #all()} to get all built-in descriptors.
 *
 * <p>
 * <b>Note:</b> {@code byte[]} is not included as a built-in descriptor despite
 * {@link StateReader#readBytes(int, String)} / {@link StateWriter#writeBytes(int, String, byte[])} being first-class
 * wire operations. Arrays are rejected by {@link ScalarTypeDescriptor}; use the primitive byte[] read/write methods
 * directly instead.
 */
public final class BuiltinTypeDescriptors {

  /** Single-slot field name used by every built-in scalar descriptor. */
  private static final String SLOT = "value";

  // --- Primitive wrapper types ---

  public static final TypeDescriptor<String> STRING = TypeDescriptor.of(
    String.class.getName(),
    String.class,
    r -> r.readString(0, SLOT),
    (w, v) -> w.writeString(0, SLOT, v)
  );

  public static final TypeDescriptor<Integer> INTEGER = TypeDescriptor.of(
    Integer.class.getName(),
    Integer.class,
    r -> r.readInt(0, SLOT),
    (w, v) -> w.writeInt(0, SLOT, v)
  );

  public static final TypeDescriptor<Long> LONG = TypeDescriptor.of(
    Long.class.getName(),
    Long.class,
    r -> r.readLong(0, SLOT),
    (w, v) -> w.writeLong(0, SLOT, v)
  );

  public static final TypeDescriptor<Double> DOUBLE = TypeDescriptor.of(
    Double.class.getName(),
    Double.class,
    r -> r.readDouble(0, SLOT),
    (w, v) -> w.writeDouble(0, SLOT, v)
  );

  public static final TypeDescriptor<Float> FLOAT = TypeDescriptor.of(
    Float.class.getName(),
    Float.class,
    r -> r.readFloat(0, SLOT),
    (w, v) -> w.writeFloat(0, SLOT, v)
  );

  public static final TypeDescriptor<Boolean> BOOLEAN = TypeDescriptor.of(
    Boolean.class.getName(),
    Boolean.class,
    r -> r.readBoolean(0, SLOT),
    (w, v) -> w.writeBoolean(0, SLOT, v)
  );

  public static final TypeDescriptor<Short> SHORT = TypeDescriptor.of(
    Short.class.getName(),
    Short.class,
    r -> r.readShort(0, SLOT),
    (w, v) -> w.writeShort(0, SLOT, v)
  );

  public static final TypeDescriptor<Character> CHARACTER = TypeDescriptor.of(
    Character.class.getName(),
    Character.class,
    r -> r.readChar(0, SLOT),
    (w, v) -> w.writeChar(0, SLOT, v)
  );

  public static final TypeDescriptor<Byte> BYTE = TypeDescriptor.of(
    Byte.class.getName(),
    Byte.class,
    r -> {
      byte[] bytes = r.readBytes(0, SLOT);
      if (bytes.length == 0) {
        throw new IllegalStateException("Cannot unmarshal Byte: expected 1 byte but got empty array");
      }
      return bytes[0];
    },
    (w, v) -> w.writeBytes(0, SLOT, new byte[] { v })
  );

  // --- Stringly types: parser produces T from a single SLOT string slot ---

  public static final TypeDescriptor<BigInteger> BIG_INTEGER = stringly(BigInteger.class, BigInteger::new);
  public static final TypeDescriptor<BigDecimal> BIG_DECIMAL = stringly(BigDecimal.class, BigDecimal::new);
  public static final TypeDescriptor<Instant> INSTANT = stringly(Instant.class, Instant::parse);
  public static final TypeDescriptor<LocalDate> LOCAL_DATE = stringly(LocalDate.class, LocalDate::parse);
  public static final TypeDescriptor<LocalDateTime> LOCAL_DATE_TIME = stringly(
    LocalDateTime.class,
    LocalDateTime::parse
  );
  public static final TypeDescriptor<ZonedDateTime> ZONED_DATE_TIME = stringly(
    ZonedDateTime.class,
    ZonedDateTime::parse
  );
  public static final TypeDescriptor<OffsetDateTime> OFFSET_DATE_TIME = stringly(
    OffsetDateTime.class,
    OffsetDateTime::parse
  );
  public static final TypeDescriptor<LocalTime> LOCAL_TIME = stringly(LocalTime.class, LocalTime::parse);
  public static final TypeDescriptor<Year> YEAR = stringly(Year.class, Year::parse);
  public static final TypeDescriptor<YearMonth> YEAR_MONTH = stringly(YearMonth.class, YearMonth::parse);
  public static final TypeDescriptor<ZoneId> ZONE_ID = stringly(ZoneId.class, ZoneId::of);
  public static final TypeDescriptor<Duration> DURATION = stringly(Duration.class, Duration::parse);
  public static final TypeDescriptor<Period> PERIOD = stringly(Period.class, Period::parse);
  public static final TypeDescriptor<UUID> UUID_DESCRIPTOR = stringly(UUID.class, UUID::fromString);
  public static final TypeDescriptor<URI> URI_DESCRIPTOR = stringly(URI.class, URI::create);

  // Non-toString formatters: locale uses BCP 47 tag, currency uses ISO 4217 code,
  // InetAddress uses host-address (toString() prepends a `/`).
  public static final TypeDescriptor<Locale> LOCALE = stringly(
    Locale.class,
    Locale::forLanguageTag,
    Locale::toLanguageTag
  );
  public static final TypeDescriptor<Currency> CURRENCY = stringly(
    Currency.class,
    Currency::getInstance,
    Currency::getCurrencyCode
  );
  public static final TypeDescriptor<InetAddress> INET_ADDRESS = stringly(
    InetAddress.class,
    InetAddress::ofLiteral,
    InetAddress::getHostAddress
  );

  private static final List<TypeDescriptor<?>> ALL = List.of(
    STRING,
    INTEGER,
    LONG,
    DOUBLE,
    FLOAT,
    BOOLEAN,
    SHORT,
    CHARACTER,
    BYTE,
    BIG_INTEGER,
    BIG_DECIMAL,
    INSTANT,
    LOCAL_DATE,
    LOCAL_DATE_TIME,
    ZONED_DATE_TIME,
    OFFSET_DATE_TIME,
    LOCAL_TIME,
    YEAR,
    YEAR_MONTH,
    ZONE_ID,
    DURATION,
    PERIOD,
    UUID_DESCRIPTOR,
    LOCALE,
    CURRENCY,
    URI_DESCRIPTOR,
    INET_ADDRESS
  );

  private static final Map<Class<?>, TypeDescriptor<?>> BY_CLASS = Map.ofEntries(
    Map.entry(String.class, STRING),
    Map.entry(Integer.class, INTEGER),
    Map.entry(Long.class, LONG),
    Map.entry(Double.class, DOUBLE),
    Map.entry(Float.class, FLOAT),
    Map.entry(Boolean.class, BOOLEAN),
    Map.entry(Short.class, SHORT),
    Map.entry(Character.class, CHARACTER),
    Map.entry(Byte.class, BYTE),
    Map.entry(BigInteger.class, BIG_INTEGER),
    Map.entry(BigDecimal.class, BIG_DECIMAL),
    Map.entry(Instant.class, INSTANT),
    Map.entry(LocalDate.class, LOCAL_DATE),
    Map.entry(LocalDateTime.class, LOCAL_DATE_TIME),
    Map.entry(ZonedDateTime.class, ZONED_DATE_TIME),
    Map.entry(OffsetDateTime.class, OFFSET_DATE_TIME),
    Map.entry(LocalTime.class, LOCAL_TIME),
    Map.entry(Year.class, YEAR),
    Map.entry(YearMonth.class, YEAR_MONTH),
    Map.entry(ZoneId.class, ZONE_ID),
    Map.entry(Duration.class, DURATION),
    Map.entry(Period.class, PERIOD),
    Map.entry(UUID.class, UUID_DESCRIPTOR),
    Map.entry(Locale.class, LOCALE),
    Map.entry(Currency.class, CURRENCY),
    Map.entry(URI.class, URI_DESCRIPTOR),
    Map.entry(InetAddress.class, INET_ADDRESS)
  );

  // Short stable symbols for the scalar built-in types. Text formats (e.g. the tree spec's `!type` hints) use these to
  // name a concrete type and pick the matching String parser via Value.fromSymbol; the reverse (symbolFor) lets a
  // serialiser write the symbol back. Collection container types have no symbol — they are spelled structurally.
  private static final Map<String, TypeDescriptor<?>> BY_SYMBOL = Map.ofEntries(
    Map.entry("string", STRING),
    Map.entry("bool", BOOLEAN),
    Map.entry("char", CHARACTER),
    Map.entry("int", INTEGER),
    Map.entry("long", LONG),
    Map.entry("short", SHORT),
    Map.entry("byte", BYTE),
    Map.entry("bigint", BIG_INTEGER),
    Map.entry("double", DOUBLE),
    Map.entry("float", FLOAT),
    Map.entry("bigdec", BIG_DECIMAL),
    Map.entry("uuid", UUID_DESCRIPTOR),
    Map.entry("url", URI_DESCRIPTOR),
    Map.entry("instant", INSTANT),
    Map.entry("date", LOCAL_DATE),
    Map.entry("datetime", LOCAL_DATE_TIME),
    Map.entry("zdatetime", ZONED_DATE_TIME),
    Map.entry("odatetime", OFFSET_DATE_TIME),
    Map.entry("time", LOCAL_TIME),
    Map.entry("year", YEAR),
    Map.entry("yearmonth", YEAR_MONTH),
    Map.entry("zone", ZONE_ID),
    Map.entry("duration", DURATION),
    Map.entry("period", PERIOD),
    Map.entry("locale", LOCALE),
    Map.entry("currency", CURRENCY),
    Map.entry("ip", INET_ADDRESS)
  );

  private static final Map<String, String> SYMBOL_BY_NAME = buildSymbolByName();

  private BuiltinTypeDescriptors() {}

  /** Returns all built-in type descriptors. */
  public static List<TypeDescriptor<?>> all() {
    return ALL;
  }

  /**
   * Finds the built-in descriptor for the given class, if any. An exact registration wins; failing that, the nearest
   * registered supertype is used. Some built-in scalar factories return a subtype of the registered class —
   * {@code InetAddress.ofLiteral} yields {@code Inet4Address}/{@code Inet6Address}, {@code ZoneId.of} yields
   * {@code ZoneRegion}/{@code ZoneOffset} — so a concrete instance's class still resolves to its canonical descriptor.
   */
  @SuppressWarnings("unchecked")
  public static <T> Optional<TypeDescriptor<? extends T>> find(Class<T> clazz) {
    Objects.requireNonNull(clazz, "clazz must not be null");
    TypeDescriptor<?> exact = BY_CLASS.get(clazz);
    if (exact != null) {
      return Optional.of((TypeDescriptor<? extends T>) exact);
    }
    for (Class<?> ancestor = clazz.getSuperclass(); ancestor != null; ancestor = ancestor.getSuperclass()) {
      TypeDescriptor<?> inherited = BY_CLASS.get(ancestor);
      if (inherited != null) {
        return Optional.of((TypeDescriptor<? extends T>) inherited);
      }
    }
    return Optional.empty();
  }

  /** Finds the built-in descriptor for the given short type symbol (e.g. {@code "bigint"}), if any. */
  public static Optional<TypeDescriptor<?>> findBySymbol(String symbol) {
    Objects.requireNonNull(symbol, "symbol must not be null");
    return Optional.ofNullable(BY_SYMBOL.get(symbol));
  }

  /** Returns the short type symbol for the given descriptor (e.g. {@code "bigint"}), if it has one. */
  public static Optional<String> symbolFor(TypeDescriptor<?> descriptor) {
    Objects.requireNonNull(descriptor, "descriptor must not be null");
    return Optional.ofNullable(SYMBOL_BY_NAME.get(descriptor.descriptorName()));
  }

  private static Map<String, String> buildSymbolByName() {
    Map<String, String> reverse = new HashMap<>();
    BY_SYMBOL.forEach((symbol, descriptor) -> reverse.put(descriptor.descriptorName(), symbol));
    return Map.copyOf(reverse);
  }

  /**
   * Stringly type whose canonical string form is {@code value.toString()} and whose parser accepts that string back.
   * Reads/writes a single {@code SLOT} slot at id 0.
   */
  private static <T> TypeDescriptor<T> stringly(Class<T> cls, Function<String, T> parser) {
    return stringly(cls, parser, Object::toString);
  }

  /**
   * Stringly type with explicit {@code formatter} for the canonical string form (e.g. {@code Locale} →
   * {@code toLanguageTag()}, {@code Currency} → {@code getCurrencyCode()}).
   */
  private static <T> TypeDescriptor<T> stringly(
    Class<T> cls,
    Function<String, T> parser,
    Function<T, String> formatter
  ) {
    return TypeDescriptor.of(
      cls.getName(),
      cls,
      r -> parser.apply(r.readString(0, SLOT)),
      (w, v) -> w.writeString(0, SLOT, formatter.apply(v))
    );
  }
}
