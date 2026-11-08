package dev.zems.lib.value.valueio.descriptor.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.valueio.ValueIo;
import dev.zems.lib.value.valueio.descriptor.StructuredTypeDescriptor;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TypeDescriptor.of(Class) — record auto-synthesis (phase 4b)")
@ContractTest
class FromClassTest {

  public record Person(String name, int age) {}

  public record AllPrimitives(boolean b, char c, byte by, short s, int i, long l, float f, double d, String str) {}

  public record Outer(String label, Person person) {}

  @Nested
  @DisplayName("Basic record auto-synthesis")
  class Basic {

    @Test
    void synthesizes_aStructuredTypeDescriptor() {
      TypeDescriptor<Person> d = TypeDescriptor.of(Person.class);
      assertThat(d).isInstanceOf(StructuredTypeDescriptor.class);
      assertThat(d.descriptorName()).isEqualTo(Person.class.getName());
    }

    @Test
    void roundTrips_simpleRecord_acrossBinary() {
      TypeDescriptor<Person> d = TypeDescriptor.of(Person.class);
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().binaryWriter(bos)) {
        w.writeRecord(0, "p", d, new Person("Alice", 30));
      }
      try (var r = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThat(r.readRecord(0, "p", d)).isEqualTo(new Person("Alice", 30));
      }
    }

    @Test
    void roundTrips_simpleRecord_acrossJson() {
      TypeDescriptor<Person> d = TypeDescriptor.of(Person.class);
      var sw = new StringWriter();
      try (var w = ValueIo.framed().jsonWriter(sw)) {
        w.writeRecord(0, "p", d, new Person("Bob", 25));
      }
      try (var r = ValueIo.framed().jsonReader(new StringReader(sw.toString()))) {
        assertThat(r.readRecord(0, "p", d)).isEqualTo(new Person("Bob", 25));
      }
    }

    @Test
    void roundTrips_allPrimitiveKinds() {
      TypeDescriptor<AllPrimitives> d = TypeDescriptor.of(AllPrimitives.class);
      var v = new AllPrimitives(true, 'A', (byte) 7, (short) 16384, 42, 9_000_000_000L, 3.14f, Math.PI, "héllo");
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().binaryWriter(bos)) {
        w.writeRecord(0, "p", d, v);
      }
      try (var r = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThat(r.readRecord(0, "p", d)).isEqualTo(v);
      }
    }

    @Test
    void roundTrips_nestedRecord() {
      TypeDescriptor<Outer> d = TypeDescriptor.of(Outer.class);
      var v = new Outer("seg", new Person("Dan", 50));
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().binaryWriter(bos)) {
        w.writeRecord(0, "o", d, v);
      }
      try (var r = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThat(r.readRecord(0, "o", d)).isEqualTo(v);
      }
    }
  }

  @Nested
  @DisplayName("Non-record rejection")
  class NonRecord {

    @Test
    void of_nonRecord_throwsWithBuilderHint() {
      assertThatThrownBy(() -> TypeDescriptor.of(PlainClass.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not a record")
        .hasMessageContaining("TypeDescriptor.builder");
    }

    public static final class PlainClass {}
  }

  @Nested
  @DisplayName("Per-slot overrides via withSlot")
  class WithSlot {

    @Test
    void aliasOverride_acceptsAlternateName() {
      var rawDesc = (StructuredTypeDescriptor<Person>) TypeDescriptor.of(Person.class);

      TypeDescriptor<Person> writer = TypeDescriptor.of(
        "Person",
        Person.class,
        r -> {
          throw new UnsupportedOperationException();
        },
        (w, p) -> {
          w.writeString(0, "fullName", p.name());
          w.writeInt(1, "age", p.age());
        }
      );

      TypeDescriptor<Person> reader = rawDesc.withSlot("name", s -> s.aliases("fullName"));

      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().binaryWriter(bos)) {
        w.writeRecord(0, "p", writer, new Person("Eve", 22));
      }
      try (var r = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThat(r.readRecord(0, "p", reader)).isEqualTo(new Person("Eve", 22));
      }
    }

    @Test
    void unknownSlotName_throws() {
      var d = (StructuredTypeDescriptor<Person>) TypeDescriptor.of(Person.class);
      assertThatThrownBy(() -> d.withSlot("notReal", s -> s))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("notReal")
        .hasMessageContaining("name")
        .hasMessageContaining("age");
    }
  }
}
