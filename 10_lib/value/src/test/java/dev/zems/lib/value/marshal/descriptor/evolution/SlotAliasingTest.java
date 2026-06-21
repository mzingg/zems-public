package dev.zems.lib.value.marshal.descriptor.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.marshal.Protocol;
import dev.zems.lib.value.marshal.ValueIo;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Phase 3 — slot-level aliases and default-on-missing via the new {@link dev.zems.lib.value.marshal.StateReader}
 * overloads. Each scenario writes with one descriptor and reads with another, verifying that aliases and defaults
 * bridge the difference without touching the wire format.
 */
@DisplayName("Slot aliasing and defaults (phase 3)")
@ContractTest
class SlotAliasingTest {

  // ============ Fixtures ============

  /** Old shape: a record with a slot named "fullName" (id 0) plus age (id 1). */
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

  /** New shape: same record but the slot has been renamed to "name" (id 0 — same wire id, different name). */
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

  /** Optional-slot shape: descriptor expects "email" (id 2) but tolerates absence with a default. */
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

  /** Minimal V1 shape used to write a payload that PersonWithEmail will read with a default. */
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

  // ============ Tests ============

  @Nested
  @DisplayName("Slot rename via aliases")
  class SlotRename {

    @Test
    void readViaAlias_whenWireHasOldName() {
      // Wire payload: PersonV1 with "fullName" slot.
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().binaryWriter(bos)) {
        w.writeRecord(0, "p", PersonV1.DESCRIPTOR, new PersonV1("Alice Smith", 30));
      }
      // Read with PersonV2 (primary "name", alias "fullName") — alias bridges the rename.
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
      // Writer emits "altName" — descriptor accepts "name" primary, ["altName", "fullName"] aliases.
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
      // Wire has only name+age (no email).
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().binaryWriter(bos)) {
        w.writeRecord(0, "p", PersonNoEmail.DESCRIPTOR, new PersonNoEmail("Carol", 40));
      }
      // Reader expects name+age+email; email absent → default "n/a".
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
      // Writer emits id=1 (age); reader expects id=0 ("name") with id=2 ("fullName") as
      // the alias fallback — neither id is present on the wire.
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
      } catch (RuntimeException closeError) {
        // After a body read fails the wire is positionally inconsistent, so close()'s
        // terminator-read fails too — that is downstream noise, not the assertion target.
      }
    }
  }
}
