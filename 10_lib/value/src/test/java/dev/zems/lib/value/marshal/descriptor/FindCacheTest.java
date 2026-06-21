package dev.zems.lib.value.marshal.descriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Phase 4b — verifies the {@code find()} cache properties: identity invariant on repeated lookups, record auto-synth
 * fallback when no DESCRIPTOR field present, negative caching for misses, and that a custom field name never
 * auto-synths.
 */
@DisplayName("TypeDescriptor.find() cache (phase 4b)")
@ContractTest
class FindCacheTest {

  public record PlainRecord(String s, int i) {}

  public record RecordWithDESCRIPTOR(String label) {
    public static final TypeDescriptor<RecordWithDESCRIPTOR> DESCRIPTOR = TypeDescriptor.of(
      "explicit.RecordWithDescriptor",
      RecordWithDESCRIPTOR.class,
      r -> new RecordWithDESCRIPTOR(r.readString(0, "label")),
      (w, v) -> w.writeString(0, "label", v.label())
    );
  }

  public record RecordWithMultipleDescriptors(String label) {
    public static final TypeDescriptor<RecordWithMultipleDescriptors> DESCRIPTOR = TypeDescriptor.of(
      "explicit.A",
      RecordWithMultipleDescriptors.class,
      r -> new RecordWithMultipleDescriptors(r.readString(0, "label")),
      (w, v) -> w.writeString(0, "label", v.label())
    );

    public static final TypeDescriptor<RecordWithMultipleDescriptors> DESCRIPTOR_PUBLIC = TypeDescriptor.of(
      "explicit.B",
      RecordWithMultipleDescriptors.class,
      r -> new RecordWithMultipleDescriptors(r.readString(0, "title")),
      (w, v) -> w.writeString(0, "title", v.label())
    );
  }

  public static final class NonRecordWithoutField {}

  /** DESCRIPTOR declared with a concrete subtype ({@link ScalarTypeDescriptor}) — the natural builder return type. */
  public record RecordWithSubtypedDescriptor(String label) {
    public static final ScalarTypeDescriptor<RecordWithSubtypedDescriptor> DESCRIPTOR = TypeDescriptor.builder(
      RecordWithSubtypedDescriptor.class
    )
      .withName("explicit.Subtyped")
      .reader(r -> new RecordWithSubtypedDescriptor(r.readString(0, "label")))
      .writer((w, v) -> w.writeString(0, "label", v.label()))
      .build();
  }

  /** A same-named field whose type is not a TypeDescriptor — must fail loudly, not fall back to auto-synthesis. */
  public record RecordWithNonDescriptorField(String label) {
    public static final String DESCRIPTOR = "not a descriptor";
  }

  @Nested
  @DisplayName("Identity invariant")
  class Identity {

    @Test
    void repeatedFindReturnsSameInstance_forBuiltIn() {
      var a = TypeDescriptor.find(String.class).orElseThrow();
      var b = TypeDescriptor.find(String.class).orElseThrow();
      assertThat(a).isSameAs(b);
    }

    @Test
    void repeatedFindReturnsSameInstance_forExplicitDESCRIPTOR() {
      var a = TypeDescriptor.find(RecordWithDESCRIPTOR.class).orElseThrow();
      var b = TypeDescriptor.find(RecordWithDESCRIPTOR.class).orElseThrow();
      assertThat(a).isSameAs(b);
      assertThat(a).isSameAs(RecordWithDESCRIPTOR.DESCRIPTOR);
    }

    @Test
    void repeatedFindReturnsSameInstance_forAutoSynthesizedRecord() {
      var a = TypeDescriptor.find(PlainRecord.class).orElseThrow();
      var b = TypeDescriptor.find(PlainRecord.class).orElseThrow();
      assertThat(a).isSameAs(b);
      assertThat(a).isInstanceOf(StructuredTypeDescriptor.class);
    }
  }

  @Nested
  @DisplayName("Record auto-synth fallback")
  class AutoSynth {

    @Test
    void recordWithoutDescriptor_autoSynthesizes() {
      var found = TypeDescriptor.find(PlainRecord.class);
      assertThat(found).isPresent();
      assertThat(found.get()).isInstanceOf(StructuredTypeDescriptor.class);
    }

    @Test
    void recordWithExplicitDescriptor_returnsExplicitNotSynth() {
      var found = TypeDescriptor.find(RecordWithDESCRIPTOR.class);
      assertThat(found).isPresent();
      assertThat(found.get()).isSameAs(RecordWithDESCRIPTOR.DESCRIPTOR);
    }

    @Test
    void customFieldName_neverAutoSynthesizes() {
      // PlainRecord has no DESCRIPTOR_OTHER field — find returns empty, doesn't synth.
      var found = TypeDescriptor.find(PlainRecord.class, "DESCRIPTOR_OTHER");
      assertThat(found).isEmpty();
    }
  }

  @Nested
  @DisplayName("DESCRIPTOR field type tolerance")
  class FieldTypeTolerance {

    @Test
    void subtypedDescriptorField_isDiscoveredAndWinsOverAutoSynth() {
      var found = TypeDescriptor.find(RecordWithSubtypedDescriptor.class).orElseThrow();
      // Discovered despite being declared as ScalarTypeDescriptor, and the explicit one beats record synthesis.
      assertThat(found).isSameAs(RecordWithSubtypedDescriptor.DESCRIPTOR);
      assertThat(found).isInstanceOf(ScalarTypeDescriptor.class);
    }

    @Test
    void nonAssignableDescriptorField_failsClearly() {
      assertThatThrownBy(() -> TypeDescriptor.find(RecordWithNonDescriptorField.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("is not a TypeDescriptor");
    }
  }

  @Nested
  @DisplayName("Negative caching")
  class NegativeCaching {

    @Test
    void missForNonRecordClass_returnsEmpty() {
      var a = TypeDescriptor.find(NonRecordWithoutField.class);
      var b = TypeDescriptor.find(NonRecordWithoutField.class);
      // Both empty; the second call should hit the cached negative.
      assertThat(a).isEmpty();
      assertThat(b).isEmpty();
    }
  }

  @Nested
  @DisplayName("Multiple descriptors per type")
  class MultipleDescriptors {

    @Test
    void findByName_returnsCorrectDescriptor() {
      var primary = TypeDescriptor.find(RecordWithMultipleDescriptors.class).orElseThrow();
      var publicApi = TypeDescriptor.find(RecordWithMultipleDescriptors.class, "DESCRIPTOR_PUBLIC").orElseThrow();
      assertThat(primary).isSameAs(RecordWithMultipleDescriptors.DESCRIPTOR);
      assertThat(publicApi).isSameAs(RecordWithMultipleDescriptors.DESCRIPTOR_PUBLIC);
      assertThat(primary).isNotSameAs(publicApi);
    }
  }
}
