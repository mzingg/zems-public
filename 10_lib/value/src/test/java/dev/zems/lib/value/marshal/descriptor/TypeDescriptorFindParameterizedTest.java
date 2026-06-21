package dev.zems.lib.value.marshal.descriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TypeDescriptor.find(Class, fieldName, typeArgs) — generic parameterisation")
@ContractTest
class TypeDescriptorFindParameterizedTest {

  @Test
  @DisplayName("returns distinct parameterised descriptors for different type args")
  void distinctDescriptorsPerParameterisation() {
    var asString = TypeDescriptor.find(Box.class, "DESCRIPTOR", String.class).orElseThrow();
    var asInteger = TypeDescriptor.find(Box.class, "DESCRIPTOR", Integer.class).orElseThrow();

    // Different parameterisations must produce different descriptor instances (cache bypass).
    assertThat(asString).isNotSameAs(asInteger);
  }

  @Test
  @DisplayName("bypasses the (Class, fieldName) cache so each call returns a fresh descriptor")
  void bypassesCache() {
    var first = TypeDescriptor.find(Box.class, "DESCRIPTOR", String.class).orElseThrow();
    var second = TypeDescriptor.find(Box.class, "DESCRIPTOR", String.class).orElseThrow();

    // Cache bypass: two synthesise calls produce structurally identical but not
    // reference-equal descriptors.
    assertThat(first).isNotSameAs(second);
    assertThat(first.descriptorName()).isEqualTo(second.descriptorName());
  }

  @Test
  @DisplayName("throws IllegalStateException when class has explicit DESCRIPTOR field and type args supplied")
  void throwsOnAmbiguityWithExplicitField() {
    assertThatThrownBy(() -> TypeDescriptor.find(BoxWithDescriptor.class, "DESCRIPTOR", String.class))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("BoxWithDescriptor")
      .hasMessageContaining("explicit DESCRIPTOR field")
      .hasMessageContaining("type args only apply");
  }

  @Test
  @DisplayName("throws IllegalStateException when class is not a record")
  void throwsForNonRecordClass() {
    assertThatThrownBy(() -> TypeDescriptor.find(String.class, "DESCRIPTOR", Object.class))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("not a record class");
  }

  @Test
  @DisplayName("delegates to the 2-arg overload when no type args are supplied — non-generic record")
  void delegatesWithoutTypeArgs() {
    var direct = TypeDescriptor.find(PlainRecord.class, "DESCRIPTOR").orElseThrow();
    var viaOverload = TypeDescriptor.find(PlainRecord.class, "DESCRIPTOR", new Object[0]).orElseThrow();

    // The empty-typeArgs path uses the cached lookup, so the overload returns the same shared
    // descriptor as the 2-arg call.
    assertThat(direct).isSameAs(viaOverload);
  }

  @Test
  @DisplayName("rejects null type-args array")
  void rejectsNullTypeArgs() {
    assertThatThrownBy(() -> TypeDescriptor.find(Box.class, "DESCRIPTOR", (Object[]) null))
      .isInstanceOf(NullPointerException.class)
      .hasMessageContaining("typeArgs");
  }

  public record Box<T>(T payload) {}

  public record PlainRecord(String s) {}

  // Non-generic record with explicit DESCRIPTOR — used only to verify that supplying type-args
  // to a class with an explicit DESCRIPTOR field is rejected.
  public record BoxWithDescriptor(String payload) {
    public static final TypeDescriptor<BoxWithDescriptor> DESCRIPTOR = TypeDescriptor.of(
      "explicit.BoxWithDescriptor",
      BoxWithDescriptor.class,
      r -> new BoxWithDescriptor(r.readString(0, "payload")),
      (w, v) -> w.writeString(0, "payload", v.payload())
    );
  }
}
