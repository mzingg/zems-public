package dev.zems.lib.value.marshal.format.json;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.JourneyTest;
import dev.zems.lib.value.marshal.ValueIo;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unknown-slot detection (UnknownSlotPolicy.FAIL) must be independent of JSON read order. The wire carries an inner
 * record with an extra slot the reading descriptor does not know (email at {@code __slot2}). Reading the inner record
 * in field order materialises it into a live frame; reading a later sibling first forces it into a buffered frame. Both
 * must report the same unknown slot.
 */
@DisplayName("JSON unknown-slot detection is read-order independent")
class JsonUnknownSlotOrderJourneyTest {

  /** Inner record as written — three slots (name/age/email at ids 0/1/2). */
  public record InnerWide(String name, int age, String email) {
    public static final TypeDescriptor<InnerWide> DESCRIPTOR = TypeDescriptor.of(
      "Inner",
      InnerWide.class,
      r -> new InnerWide(r.readString(0, "name"), r.readInt(1, "age"), r.readString(2, "email")),
      (w, p) -> {
        w.writeString(0, "name", p.name());
        w.writeInt(1, "age", p.age());
        w.writeString(2, "email", p.email());
      }
    );
  }

  /** Inner record as read — drops email, so email (id=2 → {@code __slot2}) is an unknown slot. */
  public record InnerNarrow(String name, int age) {
    public static final TypeDescriptor<InnerNarrow> DESCRIPTOR = TypeDescriptor.of(
      "Inner",
      InnerNarrow.class,
      r -> new InnerNarrow(r.readString(0, "name"), r.readInt(1, "age")),
      (w, p) -> {
        w.writeString(0, "name", p.name());
        w.writeInt(1, "age", p.age());
      }
    );
  }

  /** Outer record: inner at slot 0, a sentinel scalar at slot 1. */
  public record Outer(InnerNarrow inner, int sentinel) {}

  private static final TypeDescriptor<Outer> WRITE_WIDE = TypeDescriptor.of(
    "Outer",
    Outer.class,
    r -> new Outer(r.readRecord(0, "inner", InnerNarrow.DESCRIPTOR), r.readInt(1, "sentinel")),
    (w, o) -> {
      w.writeRecord(0, "inner", InnerWide.DESCRIPTOR, new InnerWide(o.inner().name(), o.inner().age(), "ada@x.com"));
      w.writeInt(1, "sentinel", o.sentinel());
    }
  );

  /** Reads the inner record first → inner is a live frame. */
  private static final TypeDescriptor<Outer> READ_IN_ORDER = TypeDescriptor.of(
    "Outer",
    Outer.class,
    r -> {
      InnerNarrow inner = r.readRecord(0, "inner", InnerNarrow.DESCRIPTOR);
      int sentinel = r.readInt(1, "sentinel");
      return new Outer(inner, sentinel);
    },
    (w, o) -> {
      w.writeRecord(0, "inner", InnerNarrow.DESCRIPTOR, o.inner());
      w.writeInt(1, "sentinel", o.sentinel());
    }
  );

  /** Reads the sentinel first, forcing the inner record to be buffered before it is read → inner is a buffered frame. */
  private static final TypeDescriptor<Outer> READ_OUT_OF_ORDER = TypeDescriptor.of(
    "Outer",
    Outer.class,
    r -> {
      int sentinel = r.readInt(1, "sentinel");
      InnerNarrow inner = r.readRecord(0, "inner", InnerNarrow.DESCRIPTOR);
      return new Outer(inner, sentinel);
    },
    (w, o) -> {
      w.writeRecord(0, "inner", InnerNarrow.DESCRIPTOR, o.inner());
      w.writeInt(1, "sentinel", o.sentinel());
    }
  );

  private static String writeWire() {
    var sw = new StringWriter();
    try (var w = ValueIo.framed().jsonWriter(sw)) {
      w.writeRecord(0, "outer", WRITE_WIDE, new Outer(new InnerNarrow("Ada", 36), 7));
    }
    return sw.toString();
  }

  @Test
  @JourneyTest(speakingId = "json-unknown-slots-out-of-order", acceptance = "agree")
  @DisplayName("the same wire fails on the unknown slot whether the inner record is read in order or out of order")
  void unknownSlotDetectedRegardlessOfReadOrder() {
    String wire = writeWire();

    try (var r = ValueIo.framed().jsonReader(new StringReader(wire))) {
      assertThatThrownBy(() -> r.readRecord(0, "outer", READ_IN_ORDER))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unknown slots")
        .hasMessageContaining("__slot2");
    } catch (RuntimeException closeError) {
      // After the body throws, close()'s terminator read is positionally inconsistent and also throws — not the
      // assertion target.
    }

    try (var r = ValueIo.framed().jsonReader(new StringReader(wire))) {
      assertThatThrownBy(() -> r.readRecord(0, "outer", READ_OUT_OF_ORDER))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unknown slots")
        .hasMessageContaining("__slot2");
    } catch (RuntimeException closeError) {
      // Same close-time noise as above.
    }
  }
}
