package dev.zems.lib.value.marshal.format.json;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.JourneyTest;
import dev.zems.lib.value.marshal.ValueIo;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.io.StringReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reading bare JSON integers (authored by a foreign producer) that are wider than the target slot. The parser keeps the
 * value exactly as a {@code BigInteger} (pinned by {@code JsonStreamParserTest}); a fixed-width read then rejects the
 * out-of-range value rather than silently truncating it.
 */
@DisplayName("JSON integral precision — narrowing a wide integer")
class JsonIntegralPrecisionJourneyTest {

  /** Reads slot 0 as a {@code long}. */
  public record LongHolder(long n) {
    public static final TypeDescriptor<LongHolder> DESCRIPTOR = TypeDescriptor.of(
      "LongHolder",
      LongHolder.class,
      r -> new LongHolder(r.readLong(0, "n")),
      (w, h) -> w.writeLong(0, "n", h.n())
    );
  }

  /** Reads slot 0 as an {@code int}. */
  public record IntHolder(int n) {
    public static final TypeDescriptor<IntHolder> DESCRIPTOR = TypeDescriptor.of(
      "IntHolder",
      IntHolder.class,
      r -> new IntHolder(r.readInt(0, "n")),
      (w, h) -> w.writeInt(0, "n", h.n())
    );
  }

  @Test
  @JourneyTest(speakingId = "json-integral-precision-preserved", acceptance = "a1")
  @DisplayName("a JSON integer wider than long is rejected, not truncated, when read into a long slot")
  void wideIntegerIntoLongOverflows() {
    // 23-digit integer, far beyond Long.MAX_VALUE.
    String json = "{\"$payload\":{\"__slot0\":12345678901234567890123}}";
    try (var r = ValueIo.streaming().jsonReader(new StringReader(json))) {
      r.hasMoreRecords();
      assertThatThrownBy(() -> r.readRecord(0, "$payload", LongHolder.DESCRIPTOR))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not fit long");
    }
  }

  @Test
  @JourneyTest(speakingId = "json-integral-precision-preserved", acceptance = "a1")
  @DisplayName("a long-range JSON integer is rejected, not truncated, when read into an int slot")
  void longRangeIntegerIntoIntOverflows() {
    // 5_000_000_000 fits long but not int; the old intValue() path wrapped it silently.
    String json = "{\"$payload\":{\"__slot0\":5000000000}}";
    try (var r = ValueIo.streaming().jsonReader(new StringReader(json))) {
      r.hasMoreRecords();
      assertThatThrownBy(() -> r.readRecord(0, "$payload", IntHolder.DESCRIPTOR))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not fit int");
    }
  }
}
