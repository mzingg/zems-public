package dev.zems.lib.value.marshal.descriptor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.JourneyTest;
import dev.zems.lib.value.marshal.Protocol;
import dev.zems.lib.value.marshal.ValueIo;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BuiltinTypeDescriptors — JDK-type round-trip")
class BuiltinTypeDescriptorsRoundTripJourneyTest {

  /** Pairs of declared type + sample value (some JDK types like ZoneId have subclass instances). */
  private static final List<Sample<?>> SAMPLES = List.of(
    new Sample<>(String.class, "hello"),
    new Sample<>(Integer.class, 42),
    new Sample<>(Long.class, 42L),
    new Sample<>(Double.class, 3.14),
    new Sample<>(Float.class, 2.5f),
    new Sample<>(Boolean.class, true),
    new Sample<>(Short.class, (short) 7),
    new Sample<>(Character.class, 'x'),
    new Sample<>(Byte.class, (byte) 9),
    new Sample<>(BigInteger.class, new BigInteger("123")),
    new Sample<>(BigDecimal.class, new BigDecimal("3.14")),
    new Sample<>(Instant.class, Instant.parse("2026-01-01T00:00:00Z")),
    new Sample<>(LocalDate.class, LocalDate.parse("2026-01-15")),
    new Sample<>(YearMonth.class, YearMonth.parse("2026-01")),
    new Sample<>(ZoneId.class, ZoneId.of("Europe/Zurich")),
    new Sample<>(Duration.class, Duration.ofSeconds(60)),
    new Sample<>(UUID.class, UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
    new Sample<>(Locale.class, Locale.forLanguageTag("en-US")),
    new Sample<>(Currency.class, Currency.getInstance("USD")),
    new Sample<>(URI.class, URI.create("https://example.com"))
  );

  @Test
  @JourneyTest(speakingId = "builtin-descriptors-jdk-round-trip", acceptance = "a1")
  void roundTripsAllJdkTypes() {
    for (Sample<?> sample : SAMPLES) {
      sample.roundTrip();
    }
  }

  private record Sample<T>(Class<T> clazz, T value) {
    void roundTrip() {
      var bos = new ByteArrayOutputStream();
      @SuppressWarnings("unchecked")
      TypeDescriptor<T> descriptor = (TypeDescriptor<T>) TypeDescriptor.find(clazz).orElseThrow(() ->
        new IllegalStateException("no descriptor for " + clazz)
      );
      try (var sw = ValueIo.framed().binaryWriter(bos)) {
        descriptor.write(sw, value);
      }
      try (var sr = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        Object roundTripped = descriptor.read(sr);
        assertThat(roundTripped).as("round-trip of %s", clazz.getName()).isEqualTo(value);
      }
    }
  }
}
