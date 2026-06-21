package dev.zems.lib.value.marshal.descriptor.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.marshal.Protocol;
import dev.zems.lib.value.marshal.ValueIo;
import dev.zems.lib.value.marshal.descriptor.EvolutionPolicy;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Phase 6 — verifies FAIL/SKIP unknown-slot policy enforcement after a descriptor's read lambda returns. Binary and
 * JSON paths are covered.
 */
@DisplayName("Unknown-slot policy (phase 6)")
@ContractTest
class UnknownSlotPolicyTest {

  /** Old shape: 3 fields (slot ids 0 / 1 / 2). */
  public record PersonV3(String name, int age, String email) {
    public static final TypeDescriptor<PersonV3> DESCRIPTOR = TypeDescriptor.of(
      "Person",
      PersonV3.class,
      r -> new PersonV3(r.readString(0, "name"), r.readInt(1, "age"), r.readString(2, "email")),
      (w, p) -> {
        w.writeString(0, "name", p.name());
        w.writeInt(1, "age", p.age());
        w.writeString(2, "email", p.email());
      }
    );
  }

  /** New shape: drops 'email'. */
  public record PersonV2(String name, int age) {
    public static final TypeDescriptor<PersonV2> DESCRIPTOR = TypeDescriptor.of(
      "Person",
      PersonV2.class,
      r -> new PersonV2(r.readString(0, "name"), r.readInt(1, "age")),
      (w, p) -> {
        w.writeString(0, "name", p.name());
        w.writeInt(1, "age", p.age());
      }
    );
  }

  @Nested
  @DisplayName("Binary FAIL — extra slot on wire throws")
  class BinaryFail {

    @Test
    void throwsWithSlotId_includedInMessage() {
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().binaryWriter(bos)) {
        w.writeRecord(0, "p", PersonV3.DESCRIPTOR, new PersonV3("Alice", 30, "alice@x.com"));
      }
      // PersonV2 (default FAIL) reads name (id=0) + age (id=1) and leaves email (id=2) unread.
      // The binary wire is id-anchored so the drain reports the slot id, not the name.
      try (var r = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThatThrownBy(() -> r.readRecord(0, "p", PersonV2.DESCRIPTOR))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Unknown slots")
          .hasMessageContaining("id=2");
      }
    }
  }

  @Nested
  @DisplayName("Binary SKIP — extra slot ignored")
  class BinarySkip {

    @Test
    void readsCleanly() {
      // Same wire, but use a SKIP-policy descriptor.
      TypeDescriptor<PersonV2> lenient = TypeDescriptor.of(
        "Person",
        PersonV2.class,
        r -> new PersonV2(r.readString(0, "name"), r.readInt(1, "age")),
        (w, p) -> {
          w.writeString(0, "name", p.name());
          w.writeInt(1, "age", p.age());
        }
      ).withEvolutionPolicy(EvolutionPolicy.LENIENT);

      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().binaryWriter(bos)) {
        w.writeRecord(0, "p", PersonV3.DESCRIPTOR, new PersonV3("Bob", 25, "bob@x.com"));
      }
      try (var r = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThat(r.readRecord(0, "p", lenient)).isEqualTo(new PersonV2("Bob", 25));
      }
    }
  }

  @Nested
  @DisplayName("JSON FAIL — extra field on wire throws")
  class JsonFail {

    @Test
    void throwsWithSlotId_includedInMessage() {
      var sw = new StringWriter();
      try (var w = ValueIo.framed().jsonWriter(sw)) {
        w.writeRecord(0, "p", PersonV3.DESCRIPTOR, new PersonV3("Carol", 40, "carol@x.com"));
      }
      // Under id-only wires, user-supplied slot names ("email") never reach the wire — the drain
      // surfaces the __slot<id> wire key instead. PersonV3 puts email at id=2 → __slot2.
      try (var r = ValueIo.framed().jsonReader(new StringReader(sw.toString()))) {
        assertThatThrownBy(() -> r.readRecord(0, "p", PersonV2.DESCRIPTOR))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Unknown slots")
          .hasMessageContaining("__slot2");
      }
    }
  }

  @Nested
  @DisplayName("JSON SKIP — extra field ignored")
  class JsonSkip {

    @Test
    void readsCleanly() {
      TypeDescriptor<PersonV2> lenient = TypeDescriptor.of(
        "Person",
        PersonV2.class,
        r -> new PersonV2(r.readString(0, "name"), r.readInt(1, "age")),
        (w, p) -> {
          w.writeString(0, "name", p.name());
          w.writeInt(1, "age", p.age());
        }
      ).withEvolutionPolicy(EvolutionPolicy.LENIENT);

      var sw = new StringWriter();
      try (var w = ValueIo.framed().jsonWriter(sw)) {
        w.writeRecord(0, "p", PersonV3.DESCRIPTOR, new PersonV3("Dan", 50, "dan@x.com"));
      }
      try (var r = ValueIo.framed().jsonReader(new StringReader(sw.toString()))) {
        assertThat(r.readRecord(0, "p", lenient)).isEqualTo(new PersonV2("Dan", 50));
      }
    }
  }

  @Nested
  @DisplayName("Strict happy path — no unknowns, no error")
  class StrictHappyPath {

    @Test
    void binaryReadsCleanly_whenWireMatchesDescriptor() {
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().binaryWriter(bos)) {
        w.writeRecord(0, "p", PersonV2.DESCRIPTOR, new PersonV2("Eve", 22));
      }
      try (var r = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThat(r.readRecord(0, "p", PersonV2.DESCRIPTOR)).isEqualTo(new PersonV2("Eve", 22));
      }
    }

    @Test
    void jsonReadsCleanly_whenWireMatchesDescriptor() {
      var sw = new StringWriter();
      try (var w = ValueIo.framed().jsonWriter(sw)) {
        w.writeRecord(0, "p", PersonV2.DESCRIPTOR, new PersonV2("Frank", 33));
      }
      try (var r = ValueIo.framed().jsonReader(new StringReader(sw.toString()))) {
        assertThat(r.readRecord(0, "p", PersonV2.DESCRIPTOR)).isEqualTo(new PersonV2("Frank", 33));
      }
    }
  }
}
