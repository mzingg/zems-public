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
 * Phase 4 — descriptor signature derivation and the {@code descriptorName@signature} wire encoding, plus
 * descriptor-name aliasing.
 */
@DisplayName("Signature wire encoding (phase 4)")
@ContractTest
class SignatureWireEncodingTest {

  @Nested
  @DisplayName("Signature derivation")
  class SignatureDerivation {

    @Test
    void scalarSignature_isStableAcrossInstances() {
      TypeDescriptor<Integer> a = TypeDescriptor.of(
        "MyInt",
        Integer.class,
        r -> r.readInt(0, "v"),
        (w, x) -> w.writeInt(0, "v", x)
      );
      TypeDescriptor<Integer> b = TypeDescriptor.of(
        "MyInt",
        Integer.class,
        r -> r.readInt(0, "v"),
        (w, x) -> w.writeInt(0, "v", x)
      );
      assertThat(a.signature()).isEqualTo(b.signature());
      assertThat(a.signature()).hasSize(16);
    }

    @Test
    void differentNames_differentSignatures() {
      TypeDescriptor<Integer> a = TypeDescriptor.of(
        "Foo",
        Integer.class,
        r -> r.readInt(0, "v"),
        (w, x) -> w.writeInt(0, "v", x)
      );
      TypeDescriptor<Integer> b = TypeDescriptor.of(
        "Bar",
        Integer.class,
        r -> r.readInt(0, "v"),
        (w, x) -> w.writeInt(0, "v", x)
      );
      assertThat(a.signature()).isNotEqualTo(b.signature());
    }

    @Test
    void listSignature_propagatesElementSignature() {
      TypeDescriptor<Integer> intDesc = TypeDescriptor.of(
        "MyInt",
        Integer.class,
        r -> r.readInt(0, "v"),
        (w, x) -> w.writeInt(0, "v", x)
      );
      TypeDescriptor<String> strDesc = TypeDescriptor.of(
        "MyStr",
        String.class,
        r -> r.readString(0, "v"),
        (w, x) -> w.writeString(0, "v", x)
      );

      var listOfInt = TypeDescriptor.ofList("L", intDesc);
      var listOfStr = TypeDescriptor.ofList("L", strDesc);
      // Same list-descriptor name, different element types → different signatures.
      assertThat(listOfInt.signature()).isNotEqualTo(listOfStr.signature());
    }

    @Test
    void mapSignature_propagatesKeyAndValue() {
      TypeDescriptor<String> sk = TypeDescriptor.of(
        "k",
        String.class,
        r -> r.readString(0, "v"),
        (w, x) -> w.writeString(0, "v", x)
      );
      TypeDescriptor<Integer> v1 = TypeDescriptor.of(
        "v1",
        Integer.class,
        r -> r.readInt(0, "v"),
        (w, x) -> w.writeInt(0, "v", x)
      );
      TypeDescriptor<Integer> v2 = TypeDescriptor.of(
        "v2",
        Integer.class,
        r -> r.readInt(0, "v"),
        (w, x) -> w.writeInt(0, "v", x)
      );

      var m1 = TypeDescriptor.ofMap("M", sk, v1);
      var m2 = TypeDescriptor.ofMap("M", sk, v2);
      assertThat(m1.signature()).isNotEqualTo(m2.signature());
    }
  }

  @Nested
  @DisplayName("Wire encoding — typeVerification on")
  class WireEncoding {

    @Test
    void roundTrip_binary_withSignatureSuffix() {
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().usingTypeVerification().binaryWriter(bos)) {
        w.writeRecord(0, "p", Point.DESCRIPTOR, new Point(1, 2));
      }
      try (var r = ValueIo.framed().usingTypeVerification().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThat(r.readRecord(0, "p", Point.DESCRIPTOR)).isEqualTo(new Point(1, 2));
      }
    }

    @Test
    void roundTrip_json_withSignatureSuffix() {
      var sw = new StringWriter();
      try (var w = ValueIo.framed().usingTypeVerification().jsonWriter(sw)) {
        w.writeRecord(0, "p", Point.DESCRIPTOR, new Point(3, 4));
      }
      // Wire shape carries Point@<hex> in __type
      assertThat(sw.toString()).contains("\"__type\":\"Point@");
      try (var r = ValueIo.framed().usingTypeVerification().jsonReader(new StringReader(sw.toString()))) {
        assertThat(r.readRecord(0, "p", Point.DESCRIPTOR)).isEqualTo(new Point(3, 4));
      }
    }

    public record Point(int x, int y) {
      public static final TypeDescriptor<Point> DESCRIPTOR = TypeDescriptor.of(
        "Point",
        Point.class,
        r -> new Point(r.readInt(0, "x"), r.readInt(1, "y")),
        (w, p) -> {
          w.writeInt(0, "x", p.x());
          w.writeInt(1, "y", p.y());
        }
      );
    }
  }

  @Nested
  @DisplayName("Descriptor name aliases")
  class DescriptorNameAliases {

    @Test
    void readsLegacyName_whenDescriptorDeclaresAlias() {
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().usingTypeVerification().binaryWriter(bos)) {
        w.writeRecord(0, "p", LegacyPerson.DESCRIPTOR, new LegacyPerson("Alice"));
      }
      try (var r = ValueIo.framed().usingTypeVerification().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThat(r.readRecord(0, "p", Person.DESCRIPTOR)).isEqualTo(new Person("Alice"));
      }
    }

    @Test
    void rejectsLegacyName_whenAliasNotDeclared() {
      // Person without aliases — same primary name, no LegacyPerson alias.
      TypeDescriptor<Person> strictPerson = TypeDescriptor.of(
        "Person",
        Person.class,
        r -> new Person(r.readString(0, "name")),
        (w, p) -> w.writeString(0, "name", p.name())
      );

      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().usingTypeVerification().binaryWriter(bos)) {
        w.writeRecord(0, "p", LegacyPerson.DESCRIPTOR, new LegacyPerson("Bob"));
      }
      try (var r = ValueIo.framed().usingTypeVerification().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThatThrownBy(() -> r.readRecord(0, "p", strictPerson))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Type mismatch")
          .hasMessageContaining("LegacyPerson");
      } catch (RuntimeException closeError) {
        // Close-time terminator failure is downstream of the body throw — expected.
      }
    }

    /** "old" descriptor name on the wire. */
    public record LegacyPerson(String name) {
      public static final TypeDescriptor<LegacyPerson> DESCRIPTOR = TypeDescriptor.of(
        "LegacyPerson",
        LegacyPerson.class,
        r -> new LegacyPerson(r.readString(0, "name")),
        (w, p) -> w.writeString(0, "name", p.name())
      );
    }

    /** "new" descriptor name with LegacyPerson as a name alias. */
    public record Person(String name) {
      public static final TypeDescriptor<Person> DESCRIPTOR = TypeDescriptor.of(
        "Person",
        Person.class,
        r -> new Person(r.readString(0, "name")),
        (w, p) -> w.writeString(0, "name", p.name())
      ).withAliases("LegacyPerson");
    }
  }
}
