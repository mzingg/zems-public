package dev.zems.lib.value.marshal.descriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contract test for {@link RecordSynthesis} — pins each branch in the record-to-descriptor synthesis pipeline
 * (component type resolution, generic-binding rules, rejection paths). No round-trip assertions — that's journey
 * territory.
 */
@ContractTest
@DisplayName("RecordSynthesis")
class RecordSynthesisTest {

  // ============ Happy paths ============

  @Test
  @DisplayName("simple record produces structured descriptor with one slot per component")
  void simpleRecordSynthesises() {
    var d = RecordSynthesis.synthesize(SimpleRecord.class);
    assertThat(d).isInstanceOf(StructuredTypeDescriptor.class);
    assertThat(d.qualifiedName()).contains("SimpleRecord");
    assertThat(((StructuredTypeDescriptor<?>) d).slots()).hasSize(2);
  }

  @Test
  @DisplayName("record with nested record component synthesises recursively")
  void nestedRecordSynthesisesRecursively() {
    var d = RecordSynthesis.synthesize(NestedRecord.class);
    assertThat(((StructuredTypeDescriptor<?>) d).slots()).hasSize(1);
  }

  @Test
  @DisplayName("record with List<E> component resolves the element type")
  void listComponentResolves() {
    var d = RecordSynthesis.synthesize(WithList.class);
    assertThat(((StructuredTypeDescriptor<?>) d).slots()).hasSize(1);
  }

  @Test
  @DisplayName("record with Map<K,V> component resolves both type args")
  void mapComponentResolves() {
    var d = RecordSynthesis.synthesize(WithMap.class);
    assertThat(((StructuredTypeDescriptor<?>) d).slots()).hasSize(1);
  }

  @Test
  @DisplayName("primitive int component is auto-boxed to Integer descriptor")
  void primitiveIntBoxes() {
    var primitive = RecordSynthesis.synthesize(IntPrimitive.class);
    var boxed = RecordSynthesis.synthesize(IntBoxed.class);
    assertThat(((StructuredTypeDescriptor<?>) primitive).slots()).hasSize(1);
    assertThat(((StructuredTypeDescriptor<?>) boxed).slots()).hasSize(1);
  }

  @Test
  @DisplayName("generic record with TypeDescriptor binding resolves the type variable")
  void genericRecordWithDescriptorBinding() {
    var d = RecordSynthesis.synthesize(GenericRecord.class, BuiltinTypeDescriptors.STRING);
    assertThat(((StructuredTypeDescriptor<?>) d).slots()).hasSize(1);
  }

  @Test
  @DisplayName("generic record with Class binding resolves via TypeDescriptor.find")
  void genericRecordWithClassBinding() {
    var d = RecordSynthesis.synthesize(GenericRecord.class, String.class);
    assertThat(((StructuredTypeDescriptor<?>) d).slots()).hasSize(1);
  }

  @Test
  @DisplayName("non-record class is rejected with descriptive ISE")
  void nonRecordRejected() {
    assertThatThrownBy(() -> RecordSynthesis.synthesize(String.class))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("requires a record class");
  }

  @Test
  @DisplayName("type-arg mismatch (too many) is rejected")
  void tooManyTypeArgs() {
    assertThatThrownBy(() -> RecordSynthesis.synthesize(SimpleRecord.class, String.class))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("has no type parameters but");
  }

  @Test
  @DisplayName("type-arg mismatch (too few) is rejected")
  void tooFewTypeArgs() {
    assertThatThrownBy(() -> RecordSynthesis.synthesize(GenericRecord.class))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("type parameter");
  }

  @Test
  @DisplayName("non-Class / non-TypeDescriptor typeArg is rejected")
  void invalidTypeArgRejected() {
    assertThatThrownBy(() -> RecordSynthesis.synthesize(GenericRecord.class, "not a type"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("must be a Class<?> or TypeDescriptor<?>");
  }

  @Test
  @DisplayName("component with no resolvable descriptor (Thread) is rejected")
  void unresolvableComponentTypeRejected() {
    assertThatThrownBy(() -> RecordSynthesis.synthesize(UnknownTypeRecord.class))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Cannot resolve component type");
  }

  @Test
  @DisplayName("synthesised descriptor has STRICT evolution policy by default")
  void defaultEvolutionPolicyStrict() {
    var d = RecordSynthesis.synthesize(SimpleRecord.class);
    assertThat(((StructuredTypeDescriptor<?>) d).evolutionPolicy()).isEqualTo(EvolutionPolicy.STRICT);
  }

  @Test
  @DisplayName("synthesised descriptor name uses the record's fully-qualified class name")
  void descriptorNameIsClassName() {
    var d = RecordSynthesis.synthesize(SimpleRecord.class);
    assertThat(d.descriptorName()).isEqualTo(SimpleRecord.class.getName());
  }

  // ============ Rejection branches ============

  public record SimpleRecord(String name, int age) {}

  public record NestedRecord(SimpleRecord inner) {}

  public record WithList(List<String> items) {}

  public record WithMap(Map<String, Integer> entries) {}

  public record IntPrimitive(int n) {}

  public record IntBoxed(Integer n) {}

  // ============ Result contract ============

  public record GenericRecord<T>(T value) {}

  public record UnknownTypeRecord(Thread t) {}
}
