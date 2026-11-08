package dev.zems.lib.value.valueio.descriptor.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.valueio.ValueIo;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Slot aliasing and defaults (phase 3)")
@ContractTest
class SlotAliasingTest {

  public record PersonV1(String fullName, int age) {
    public static final TypeDescriptor<PersonV1> DESCRIPTOR = TypeDescriptor.of(
      "Person",
      PersonV1.class,
      r -> new PersonV1(r.readString(0, "fullName"), r.readInt(1, "age")),
      (w, p) -> {
        w.writeString(0, "fullName", p.fullName());
        w.writeInt(1, "age", p.age());
      }
    );
  }

  public record PersonV2(String name, int age) {
    public static final TypeDescriptor<PersonV2> DESCRIPTOR = TypeDescriptor.of(
      "Person",
      PersonV2.class,
      r ->
        new PersonV2(
          r.hasField(0, "name") ? r.readString(0, "name") : r.readString(0, "fullName"),
          r.readInt(1, "age")
        ),
      (w, p) -> {
        w.writeString(0, "name", p.name());
        w.writeInt(1, "age", p.age());
      }
    );
  }

  public record PersonWithEmail(String name, int age, String email) {
    public static final TypeDescriptor<PersonWithEmail> DESCRIPTOR = TypeDescriptor.of(
      "Person",
      PersonWithEmail.class,
      r -> new PersonWithEmail(r.readString(0, "name"), r.readInt(1, "age"), r.readStringOr(2, "email", "n/a")),
      (w, p) -> {
        w.writeString(0, "name", p.name());
        w.writeInt(1, "age", p.age());
        w.writeString(2, "email", p.email());
      }
    );
  }

  public record PersonNoEmail(String name, int age) {
    public static final TypeDescriptor<PersonNoEmail> DESCRIPTOR = TypeDescriptor.of(
      "Person",
      PersonNoEmail.class,
      r -> new PersonNoEmail(r.readString(0, "name"), r.readInt(1, "age")),
      (w, p) -> {
        w.writeString(0, "name", p.name());
        w.writeInt(1, "age", p.age());
      }
    );
  }

  @Nested
  @DisplayName("Slot rename via aliases")
  class SlotRename {

    @Test
    void readViaAlias_whenWireHasOldName() {
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().binaryWriter(bos)) {
        w.writeRecord(0, "p", PersonV1.DESCRIPTOR, new PersonV1("Alice Smith", 30));
      }
      try (var r = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThat(r.readRecord(0, "p", PersonV2.DESCRIPTOR)).isEqualTo(new PersonV2("Alice Smith", 30));
      }
    }

    @Test
    void readViaPrimary_whenWireHasNewName() {
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().binaryWriter(bos)) {
        w.writeRecord(0, "p", PersonV2.DESCRIPTOR, new PersonV2("Bob Jones", 25));
      }
      try (var r = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThat(r.readRecord(0, "p", PersonV2.DESCRIPTOR)).isEqualTo(new PersonV2("Bob Jones", 25));
      }
    }

    @Test
    void firstMatchWins_amongAliases() {
      record Multi(String name) {
        static final TypeDescriptor<Multi> WRITER = TypeDescriptor.of(
          "M",
          Multi.class,
          r -> new Multi(r.readString(0, "altName")),
          (w, m) -> w.writeString(0, "altName", m.name())
        );
        static final TypeDescriptor<Multi> READER = TypeDescriptor.of(
          "M",
          Multi.class,
          r ->
            new Multi(
              r.hasField(0, "name")
                ? r.readString(0, "name")
                : r.hasField(0, "altName")
                  ? r.readString(0, "altName")
                  : r.readString(0, "fullName")
            ),
          (w, m) -> w.writeString(0, "name", m.name())
        );
      }
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().binaryWriter(bos)) {
        w.writeRecord(0, "m", Multi.WRITER, new Multi("hello"));
      }
      try (var r = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThat(r.readRecord(0, "m", Multi.READER)).isEqualTo(new Multi("hello"));
      }
    }
  }

  @Nested
  @DisplayName("Default on missing")
  class DefaultOnMissing {

    @Test
    void usesDefault_whenSlotAbsent() {
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().binaryWriter(bos)) {
        w.writeRecord(0, "p", PersonNoEmail.DESCRIPTOR, new PersonNoEmail("Carol", 40));
      }
      try (var r = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThat(r.readRecord(0, "p", PersonWithEmail.DESCRIPTOR)).isEqualTo(new PersonWithEmail("Carol", 40, "n/a"));
      }
    }

    @Test
    void usesWireValue_whenSlotPresent() {
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().binaryWriter(bos)) {
        w.writeRecord(0, "p", PersonWithEmail.DESCRIPTOR, new PersonWithEmail("Dan", 50, "dan@x.com"));
      }
      try (var r = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThat(r.readRecord(0, "p", PersonWithEmail.DESCRIPTOR)).isEqualTo(
          new PersonWithEmail("Dan", 50, "dan@x.com")
        );
      }
    }
  }

  @Nested
  @DisplayName("Cross-format — aliases work for JSON too")
  class CrossFormat {

    @Test
    void slotRename_acrossJson() {
      var sw = new StringWriter();
      try (var w = ValueIo.framed().jsonWriter(sw)) {
        w.writeRecord(0, "p", PersonV1.DESCRIPTOR, new PersonV1("Eve", 21));
      }
      try (var r = ValueIo.framed().jsonReader(new StringReader(sw.toString()))) {
        assertThat(r.readRecord(0, "p", PersonV2.DESCRIPTOR)).isEqualTo(new PersonV2("Eve", 21));
      }
    }

    @Test
    void defaultOnMissing_acrossJson() {
      var sw = new StringWriter();
      try (var w = ValueIo.framed().jsonWriter(sw)) {
        w.writeRecord(0, "p", PersonNoEmail.DESCRIPTOR, new PersonNoEmail("Frank", 33));
      }
      try (var r = ValueIo.framed().jsonReader(new StringReader(sw.toString()))) {
        assertThat(r.readRecord(0, "p", PersonWithEmail.DESCRIPTOR)).isEqualTo(new PersonWithEmail("Frank", 33, "n/a"));
      }
    }
  }

  @Nested
  @DisplayName("Required slot missing — error path")
  class RequiredSlotMissing {

    @Test
    void throwsClearError_whenRequiredSlotAbsent() {
      record OnlyAge(int age) {
        static final TypeDescriptor<OnlyAge> WRITER = TypeDescriptor.of(
          "X",
          OnlyAge.class,
          r -> new OnlyAge(r.readInt(1, "age")),
          (w, o) -> w.writeInt(1, "age", o.age())
        );
      }
      record Wants(String name) {
        static final TypeDescriptor<Wants> READER = TypeDescriptor.of(
          "X",
          Wants.class,
          r -> {
            if (r.hasField(0, "name")) {
              return new Wants(r.readString(0, "name"));
            }
            if (r.hasField(2, "fullName")) {
              return new Wants(r.readString(2, "fullName"));
            }
            throw new IllegalStateException("required slot 'name' (aliases: fullName) not found");
          },
          (w, x) -> w.writeString(0, "name", x.name())
        );
      }
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().binaryWriter(bos)) {
        w.writeRecord(0, "x", OnlyAge.WRITER, new OnlyAge(7));
      }
      try (var r = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThatThrownBy(() -> r.readRecord(0, "x", Wants.READER))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("name")
          .hasMessageContaining("fullName");
      } catch (RuntimeException ignored) {}
    }
  }
}
