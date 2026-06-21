package dev.zems.lib.value.marshal.descriptor.evolution;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.marshal.Protocol;
import dev.zems.lib.value.marshal.ValueIo;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Phase 5 — verifies that {@code StateReader.recordSignature()} surfaces the wire signature to a descriptor's read
 * lambda, enabling migration branches against captured constants.
 */
@DisplayName("recordSignature() API (phase 5)")
@ContractTest
class RecordSignatureTest {

  public record Person(String name, int age) {
    public static final TypeDescriptor<Person> DESCRIPTOR = TypeDescriptor.of(
      "Person",
      Person.class,
      r -> new Person(r.readString(0, "name"), r.readInt(1, "age")),
      (w, p) -> {
        w.writeString(0, "name", p.name());
        w.writeInt(1, "age", p.age());
      }
    );
  }

  @Nested
  @DisplayName("Surfaces wire signature when type verification is on")
  class TypeVerifyOn {

    @Test
    void binary_exposesNonEmptySignature() {
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().usingTypeVerification().binaryWriter(bos)) {
        w.writeRecord(0, "p", Person.DESCRIPTOR, new Person("Alice", 30));
      }
      AtomicReference<String> seen = new AtomicReference<>();
      TypeDescriptor<Person> probing = TypeDescriptor.of(
        "Person",
        Person.class,
        r -> {
          seen.set(r.recordSignature());
          return new Person(r.readString(0, "name"), r.readInt(1, "age"));
        },
        (w, p) -> {
          w.writeString(0, "name", p.name());
          w.writeInt(1, "age", p.age());
        }
      );
      try (var r = ValueIo.framed().usingTypeVerification().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        r.readRecord(0, "p", probing);
      }
      assertThat(seen.get()).isNotEmpty().hasSize(16).isEqualTo(Person.DESCRIPTOR.signature());
    }

    @Test
    void json_exposesNonEmptySignature() {
      var sw = new StringWriter();
      try (var w = ValueIo.framed().usingTypeVerification().jsonWriter(sw)) {
        w.writeRecord(0, "p", Person.DESCRIPTOR, new Person("Bob", 25));
      }
      AtomicReference<String> seen = new AtomicReference<>();
      TypeDescriptor<Person> probing = TypeDescriptor.of(
        "Person",
        Person.class,
        r -> {
          seen.set(r.recordSignature());
          return new Person(r.readString(0, "name"), r.readInt(1, "age"));
        },
        (w, p) -> {
          w.writeString(0, "name", p.name());
          w.writeInt(1, "age", p.age());
        }
      );
      try (var r = ValueIo.framed().usingTypeVerification().jsonReader(new StringReader(sw.toString()))) {
        r.readRecord(0, "p", probing);
      }
      assertThat(seen.get()).isEqualTo(Person.DESCRIPTOR.signature());
    }
  }

  @Nested
  @DisplayName("Returns empty string when not in a record / no type verification")
  class TypeVerifyOff {

    @Test
    void atRootLevel_isEmpty() {
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().binaryWriter(bos)) {
        w.writeRecord(0, "p", Person.DESCRIPTOR, new Person("Dan", 50));
      }
      try (var r = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        // Before entering the record, signature is empty.
        assertThat(r.recordSignature()).isEmpty();
        r.readRecord(0, "p", Person.DESCRIPTOR);
        // After exit, empty again.
        assertThat(r.recordSignature()).isEmpty();
      }
    }

    @Test
    void typeVerificationOff_signatureEmptyInsideRecord() {
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().binaryWriter(bos)) {
        w.writeRecord(0, "p", Person.DESCRIPTOR, new Person("Eve", 22));
      }
      AtomicReference<String> seen = new AtomicReference<>();
      TypeDescriptor<Person> probing = TypeDescriptor.of(
        "Person",
        Person.class,
        r -> {
          seen.set(r.recordSignature());
          return new Person(r.readString(0, "name"), r.readInt(1, "age"));
        },
        (w, p) -> {
          w.writeString(0, "name", p.name());
          w.writeInt(1, "age", p.age());
        }
      );
      try (var r = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        r.readRecord(0, "p", probing);
      }
      // No type verification → wire didn't carry a signature → reader exposes "".
      assertThat(seen.get()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Migration branch via signature constant")
  class MigrationBranch {

    @Test
    void descriptorBranchesOnHistoricalSignature() {
      // Capture the v1 signature so the v2 descriptor can recognise old payloads.
      String SIG_V1 = PersonV1.DESCRIPTOR.signature();

      // Write with v1 descriptor.
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().usingTypeVerification().binaryWriter(bos)) {
        w.writeRecord(0, "p", PersonV1.DESCRIPTOR, new PersonV1("Frank Miller", 60));
      }

      // V2 descriptor: same wire-name "Person", branches on signature.
      TypeDescriptor<Person> v2 = TypeDescriptor.of(
        "Person",
        Person.class,
        r -> {
          if (r.recordSignature().equals(SIG_V1)) {
            return new Person(r.readString(0, "fullName"), r.readInt(0, "years"));
          }
          return new Person(r.readString(0, "name"), r.readInt(1, "age"));
        },
        (w, p) -> {
          w.writeString(0, "name", p.name());
          w.writeInt(1, "age", p.age());
        }
      );

      try (var r = ValueIo.framed().usingTypeVerification().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        // Will hit the V1 branch via signature equality.
        assertThat(r.readRecord(0, "p", v2)).isEqualTo(new Person("Frank Miller", 60));
      }
    }

    public record PersonV1(String fullName, int years) {
      public static final TypeDescriptor<PersonV1> DESCRIPTOR = TypeDescriptor.of(
        "Person",
        PersonV1.class,
        r -> new PersonV1(r.readString(0, "fullName"), r.readInt(0, "years")),
        (w, p) -> {
          w.writeString(0, "fullName", p.fullName());
          w.writeInt(0, "years", p.years());
        }
      );
    }
  }
}
