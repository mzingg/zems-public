package dev.zems.lib.value.valueio.format.json;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.JourneyTest;
import dev.zems.lib.value.valueio.ValueIo;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.io.StringReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JSON integral precision — a wide integer is rejected, not truncated")
class JsonIntegralPrecisionJourneyTest {

  public record LongHolder(long n) {
    public static final TypeDescriptor<LongHolder> DESCRIPTOR = TypeDescriptor.of(
      "LongHolder",
      LongHolder.class,
      r -> new LongHolder(r.readLong(0, "n")),
      (w, h) -> w.writeLong(0, "n", h.n())
    );
  }

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
    String json = "{\"$payload\":{\"__slot0\":5000000000}}";
    try (var r = ValueIo.streaming().jsonReader(new StringReader(json))) {
      r.hasMoreRecords();
      assertThatThrownBy(() -> r.readRecord(0, "$payload", IntHolder.DESCRIPTOR))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not fit int");
    }
  }
}
