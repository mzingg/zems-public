package dev.zems.lib.value.valueio.format.json;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.JourneyTest;
import dev.zems.lib.value.valueio.ValueIo;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JSON unknown-slot detection is read-order independent")
class JsonUnknownSlotOrderJourneyTest {

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
    } catch (RuntimeException ignored) {}

    try (var r = ValueIo.framed().jsonReader(new StringReader(wire))) {
      assertThatThrownBy(() -> r.readRecord(0, "outer", READ_OUT_OF_ORDER))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unknown slots")
        .hasMessageContaining("__slot2");
    } catch (RuntimeException ignored) {}
  }
}
