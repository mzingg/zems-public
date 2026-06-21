package dev.zems.lib.value.marshal.descriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SlotSpec id and uniqueness")
@ContractTest
class SlotSpecIdTest {

  @Test
  @DisplayName("RecordSynthesis assigns ids by component declaration order")
  void implicitIdsByComponentOrder() {
    var descriptor = RecordSynthesis.synthesize(SamplePerson.class);

    assertThat(descriptor.slots()).hasSize(2);
    assertThat(descriptor.slots().get(0).id()).isEqualTo(0);
    assertThat(descriptor.slots().get(0).name()).isEqualTo("name");
    assertThat(descriptor.slots().get(1).id()).isEqualTo(1);
    assertThat(descriptor.slots().get(1).name()).isEqualTo("age");
  }

  @Test
  @DisplayName("SlotSpec rejects negative ids")
  void rejectsNegativeId() throws Exception {
    var accessor = MethodHandles.publicLookup().findVirtual(
      SamplePerson.class,
      "name",
      MethodType.methodType(String.class)
    );
    var stringDescriptor = TypeDescriptor.of("java.lang.String", String.class);

    assertThatThrownBy(() -> new SlotSpec<>(-1, "name", List.of(), stringDescriptor, null, accessor, SlotKind.STRING))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("slot id must be non-negative");
  }

  @Test
  @DisplayName("StructuredTypeDescriptor rejects duplicate ids")
  void rejectsDuplicateIds() throws Exception {
    var accessor = MethodHandles.publicLookup().findVirtual(
      SamplePerson.class,
      "name",
      MethodType.methodType(String.class)
    );
    var stringDescriptor = TypeDescriptor.of("java.lang.String", String.class);
    var ageAccessor = MethodHandles.publicLookup().findVirtual(
      SamplePerson.class,
      "age",
      MethodType.methodType(int.class)
    );
    var intDescriptor = TypeDescriptor.of("java.lang.Integer", Integer.class);

    var s1 = new SlotSpec<>(0, "name", List.of(), stringDescriptor, null, accessor, SlotKind.STRING);
    var s2 = new SlotSpec<>(0, "age", List.of(), intDescriptor, null, ageAccessor, SlotKind.INT);

    var ctor = MethodHandles.publicLookup().findConstructor(
      SamplePerson.class,
      MethodType.methodType(void.class, String.class, int.class)
    );

    assertThatThrownBy(() ->
      new StructuredTypeDescriptor<>("X", List.of(), EvolutionPolicy.STRICT, SamplePerson.class, List.of(s1, s2), ctor)
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Duplicate slot id 0")
      .hasMessageContaining("'name'")
      .hasMessageContaining("'age'");
  }

  @Test
  @DisplayName("SlotSpec validates name via Names.validate")
  void rejectsInvalidName() throws Exception {
    var accessor = MethodHandles.publicLookup().findVirtual(
      SamplePerson.class,
      "name",
      MethodType.methodType(String.class)
    );
    var stringDescriptor = TypeDescriptor.of("java.lang.String", String.class);

    String badName = "a" + (char) 0x00 + "b";
    assertThatThrownBy(() -> new SlotSpec<>(0, badName, List.of(), stringDescriptor, null, accessor, SlotKind.STRING))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("disallowed control character");
  }

  @Test
  @DisplayName("SlotSpec validates aliases via Names.validate")
  void rejectsInvalidAlias() throws Exception {
    var accessor = MethodHandles.publicLookup().findVirtual(
      SamplePerson.class,
      "name",
      MethodType.methodType(String.class)
    );
    var stringDescriptor = TypeDescriptor.of("java.lang.String", String.class);

    String badAlias = "x" + (char) 0x07 + "y";
    assertThatThrownBy(() ->
      new SlotSpec<>(0, "name", List.of(badAlias), stringDescriptor, null, accessor, SlotKind.STRING)
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("alias name");
  }

  public record SamplePerson(String name, int age) {}
}
