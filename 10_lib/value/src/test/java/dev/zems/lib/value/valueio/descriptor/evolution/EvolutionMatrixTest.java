package dev.zems.lib.value.valueio.descriptor.evolution;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common.junit.JourneyTest;
import dev.zems.lib.value.valueio.StateReader;
import dev.zems.lib.value.valueio.StateWriter;
import dev.zems.lib.value.valueio.ValueIo;
import dev.zems.lib.value.valueio.descriptor.EvolutionPolicy;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Function;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("Evolution matrix (cross-format)")
class EvolutionMatrixTest {

  private static byte[] serialize(Format format, Consumer<StateWriter> action, boolean typeVerify) {
    var bos = new ByteArrayOutputStream();
    ValueIo base = ValueIo.framed();
    ValueIo proto = typeVerify ? base.usingTypeVerification() : base;
    switch (format) {
      case BINARY -> {
        try (var w = proto.binaryWriter(bos)) {
          action.accept(w);
        }
      }
      case JSON -> {
        var sw = new StringWriter();
        try (var w = proto.jsonWriter(sw)) {
          action.accept(w);
        }
        return sw.toString().getBytes(StandardCharsets.UTF_8);
      }
    }
    return bos.toByteArray();
  }

  private static <T> T deserialize(Format format, byte[] wire, Function<StateReader, T> action, boolean typeVerify) {
    ValueIo base = ValueIo.framed();
    ValueIo proto = typeVerify ? base.usingTypeVerification() : base;
    switch (format) {
      case BINARY -> {
        try (var r = proto.binaryReader(new ByteArrayInputStream(wire))) {
          return action.apply(r);
        }
      }
      case JSON -> {
        try (var r = proto.jsonReader(new StringReader(new String(wire, StandardCharsets.UTF_8)))) {
          return action.apply(r);
        }
      }
      default -> throw new IllegalStateException("unreachable");
    }
  }

  @ParameterizedTest(name = "[{0}] rename + drop + descriptor-alias + SKIP")
  @EnumSource(Format.class)
  @DisplayName("Combined: V1 wire → V2 reader with aliases + SKIP")
  @JourneyTest(speakingId = "descriptor-evolution-machinery", acceptance = "a1")
  void combinedScenario_acrossFormats(Format format) {
    var v1 = new PersonV1("Alice Smith", 30, "alice@x.com");
    boolean withTypeVerification = true;
    byte[] wire = serialize(format, w -> w.writeRecord(0, "p", PersonV1.DESCRIPTOR, v1), withTypeVerification);
    PersonV2 result = deserialize(format, wire, r -> r.readRecord(0, "p", PersonV2.DESCRIPTOR), withTypeVerification);
    assertThat(result).isEqualTo(new PersonV2("Alice Smith", 30));
  }

  @ParameterizedTest(name = "[{0}] slot rename via alias")
  @EnumSource(Format.class)
  @JourneyTest(speakingId = "descriptor-evolution-machinery", acceptance = "a2")
  void slotRename_acrossFormats(Format format) {
    record OnlyName(String fullName) {
      static final TypeDescriptor<OnlyName> WRITER = TypeDescriptor.of(
        "X",
        OnlyName.class,
        r -> new OnlyName(r.readString(0, "fullName")),
        (w, o) -> w.writeString(0, "fullName", o.fullName())
      );
    }
    record V2(String name) {
      static final TypeDescriptor<V2> READER = TypeDescriptor.of(
        "X",
        V2.class,
        r -> new V2(r.hasField(0, "name") ? r.readString(0, "name") : r.readString(0, "fullName")),
        (w, v) -> w.writeString(0, "name", v.name())
      );
    }

    byte[] wire = serialize(format, w -> w.writeRecord(0, "x", OnlyName.WRITER, new OnlyName("Bob")), false);
    V2 result = deserialize(format, wire, r -> r.readRecord(0, "x", V2.READER), false);
    assertThat(result).isEqualTo(new V2("Bob"));
  }

  @ParameterizedTest(name = "[{0}] default on missing")
  @EnumSource(Format.class)
  @JourneyTest(speakingId = "descriptor-evolution-machinery", acceptance = "a3")
  void defaultOnMissing_acrossFormats(Format format) {
    record HasOnlyName(String name) {
      static final TypeDescriptor<HasOnlyName> WRITER = TypeDescriptor.of(
        "X",
        HasOnlyName.class,
        r -> new HasOnlyName(r.readString(0, "name")),
        (w, o) -> w.writeString(0, "name", o.name())
      );
    }
    record WithEmail(String name, String email) {
      static final TypeDescriptor<WithEmail> READER = TypeDescriptor.of(
        "X",
        WithEmail.class,
        r -> new WithEmail(r.readString(0, "name"), r.readStringOr(1, "email", "n/a")),
        (w, v) -> {
          w.writeString(0, "name", v.name());
          w.writeString(1, "email", v.email());
        }
      );
    }

    byte[] wire = serialize(format, w -> w.writeRecord(0, "x", HasOnlyName.WRITER, new HasOnlyName("Carol")), false);
    WithEmail result = deserialize(format, wire, r -> r.readRecord(0, "x", WithEmail.READER), false);
    assertThat(result).isEqualTo(new WithEmail("Carol", "n/a"));
  }

  @ParameterizedTest(name = "[{0}] signature branch on historical SIG_V1")
  @EnumSource(Format.class)
  @JourneyTest(speakingId = "descriptor-evolution-machinery", acceptance = "a4")
  void signatureBranch_acrossFormats(Format format) {
    String sigV1 = PersonV1.DESCRIPTOR.signature();

    TypeDescriptor<PersonV2> v2WithBranch = TypeDescriptor.of(
      "Person",
      PersonV2.class,
      r -> {
        if (r.recordSignature().equals(sigV1)) {
          String n = r.readString(0, "fullName");
          int a = r.readInt(1, "years");
          r.readString(2, "email"); // an unread slot would trip the FAIL policy
          return new PersonV2(n, a);
        }
        return new PersonV2(r.readString(0, "name"), r.readInt(1, "age"));
      },
      (w, p) -> {
        w.writeString(0, "name", p.name());
        w.writeInt(1, "age", p.age());
      }
    );

    var v2WithBranchAndAlias = v2WithBranch.withAliases("LegacyPerson");

    var v1 = new PersonV1("Dan", 50, "dan@x.com");
    byte[] wire = serialize(format, w -> w.writeRecord(0, "p", PersonV1.DESCRIPTOR, v1), true);
    PersonV2 result = deserialize(format, wire, r -> r.readRecord(0, "p", v2WithBranchAndAlias), true);
    assertThat(result).isEqualTo(new PersonV2("Dan", 50));
  }

  @ParameterizedTest(name = "[{0}] FAIL — extra slot rejected")
  @EnumSource(Format.class)
  @JourneyTest(speakingId = "descriptor-evolution-machinery", acceptance = "a5")
  void failPolicy_rejectsExtraSlot(Format format) {
    TypeDescriptor<PersonV2> strict = PersonV2.DESCRIPTOR.withEvolutionPolicy(EvolutionPolicy.STRICT);

    var v1 = new PersonV1("Eve", 25, "eve@x.com");
    byte[] wire = serialize(format, w -> w.writeRecord(0, "p", PersonV1.DESCRIPTOR, v1), true);

    String expected = format == Format.BINARY ? "id=2" : "__slot2";
    Assertions.assertThatThrownBy(() -> deserialize(format, wire, r -> r.readRecord(0, "p", strict), true))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Unknown slots")
      .hasMessageContaining(expected);
  }

  enum Format {
    BINARY,
    JSON,
  }

  public record PersonV1(String fullName, int years, String email) {
    public static final TypeDescriptor<PersonV1> DESCRIPTOR = TypeDescriptor.of(
      "LegacyPerson",
      PersonV1.class,
      r -> new PersonV1(r.readString(0, "fullName"), r.readInt(1, "years"), r.readString(2, "email")),
      (w, p) -> {
        w.writeString(0, "fullName", p.fullName());
        w.writeInt(1, "years", p.years());
        w.writeString(2, "email", p.email());
      }
    );
  }

  public record PersonV2(String name, int age) {
    public static final TypeDescriptor<PersonV2> DESCRIPTOR = TypeDescriptor.of(
      "Person",
      PersonV2.class,
      r -> {
        String n = r.hasField(0, "name") ? r.readString(0, "name") : r.readString(0, "fullName");
        int a = r.hasField(1, "age") ? r.readInt(1, "age") : r.readInt(1, "years");
        return new PersonV2(n, a);
      },
      (w, p) -> {
        w.writeString(0, "name", p.name());
        w.writeInt(1, "age", p.age());
      }
    )
      .withAliases("LegacyPerson")
      .withEvolutionPolicy(EvolutionPolicy.LENIENT);
  }
}
