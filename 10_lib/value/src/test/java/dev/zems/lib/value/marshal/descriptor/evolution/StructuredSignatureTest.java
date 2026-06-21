package dev.zems.lib.value.marshal.descriptor.evolution;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.marshal.descriptor.StructuredTypeDescriptor;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Phase 4b — verifies that {@link StructuredTypeDescriptor#signature()} is derived from the slot table: structural
 * changes (rename a slot, change a slot's type) bump the signature; read-time changes (adding aliases) leave it
 * stable.
 */
@DisplayName("StructuredTypeDescriptor signature (phase 4b)")
@ContractTest
class StructuredSignatureTest {

  public record V1(String name, int age) {}

  public record V2_RenamedSlot(String fullName, int age) {}

  public record V2_ChangedType(String name, long age) {}

  public record SameShape(String name, int age) {}

  @Nested
  @DisplayName("Structural changes bump the signature")
  class StructuralChanges {

    @Test
    void renamingSlot_bumpsSignature() {
      String s1 = TypeDescriptor.of(V1.class).signature();
      String s2 = TypeDescriptor.of(V2_RenamedSlot.class).signature();
      assertThat(s1).isNotEqualTo(s2);
    }

    @Test
    void changingSlotType_bumpsSignature() {
      String s1 = TypeDescriptor.of(V1.class).signature();
      String s2 = TypeDescriptor.of(V2_ChangedType.class).signature();
      assertThat(s1).isNotEqualTo(s2);
    }
  }

  @Nested
  @DisplayName("Same-shape records have the same slot-derived signature mod descriptor name")
  class SameShapeDifferentName {

    @Test
    void sameSlotShape_butDifferentRecordClass_hasDifferentSignature() {
      // Different class names → different descriptorName → different signature.
      String s1 = TypeDescriptor.of(V1.class).signature();
      String s2 = TypeDescriptor.of(SameShape.class).signature();
      assertThat(s1).isNotEqualTo(s2);
    }
  }

  @Nested
  @DisplayName("Read-time customizations don't bump the signature")
  class ReadTimeStability {

    @Test
    void addingSlotAlias_doesNotBumpSignature() {
      var base = (StructuredTypeDescriptor<V1>) TypeDescriptor.of(V1.class);
      var withAlias = base.withSlot("name", s -> s.aliases("fullName"));
      assertThat(base.signature()).isEqualTo(withAlias.signature());
    }

    @Test
    void addingDescriptorAlias_doesNotBumpSignature() {
      var base = (StructuredTypeDescriptor<V1>) TypeDescriptor.of(V1.class);
      var withAlias = base.withAliases("LegacyV1");
      assertThat(base.signature()).isEqualTo(withAlias.signature());
    }
  }

  @Nested
  @DisplayName("Signature stability across calls")
  class Stability {

    @Test
    void signature_isDeterministic_acrossSynthesizedInstances() {
      String s1 = TypeDescriptor.of(V1.class).signature();
      String s2 = TypeDescriptor.of(V1.class).signature();
      assertThat(s1).isEqualTo(s2);
      assertThat(s1).hasSize(16);
    }
  }
}
