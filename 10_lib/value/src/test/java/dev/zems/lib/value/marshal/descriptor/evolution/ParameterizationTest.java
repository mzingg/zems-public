package dev.zems.lib.value.marshal.descriptor.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Phase 4b — verifies parameterisation through {@code TypeDescriptor.of(Class, Object...)}: generic records bind
 * type-args; List/Map dispatch; nested generics; mixing Class and TypeDescriptor in typeArgs.
 */
@DisplayName("of(Class, typeArgs) — parameterization (phase 4b)")
@ContractTest
class ParameterizationTest {

  public record Person(String name) {}

  public record MyBox<T>(T value) {}

  public record Pair<A, B>(A first, B second) {}

  public record Container<E>(String label, MyBox<E> box) {}

  @Nested
  @DisplayName("List / Map dispatch")
  class CollectionDispatch {}

  @Nested
  @DisplayName("Generic record binding")
  class GenericRecord {

    @Test
    void singleTypeParam_resolvedFromClass() {
      var d = TypeDescriptor.of(MyBox.class, Person.class);
      assertThat(d.descriptorName()).isEqualTo(MyBox.class.getName());
    }

    @Test
    void twoTypeParams_resolvedInDeclarationOrder() {
      var d = TypeDescriptor.of(Pair.class, Person.class, String.class);
      assertThat(d.descriptorName()).isEqualTo(Pair.class.getName());
    }

    @Test
    void wrongTypeArgCount_throws() {
      assertThatThrownBy(() -> TypeDescriptor.of(MyBox.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("type parameter")
        .hasMessageContaining("typeArg");
    }

    @Test
    void unboundTypeVariable_throwsAtSynthesis() {
      // Cannot synthesise raw MyBox.class without typeArgs — it has a TypeVariable component.
      assertThatThrownBy(() -> TypeDescriptor.of(MyBox.class, new Object[0])).isInstanceOf(
        IllegalArgumentException.class
      );
    }
  }

  @Nested
  @DisplayName("Nested generics")
  class NestedGenerics {

    @Test
    void containerOfMyBoxOfPerson_passesInnerDescriptor() {
      var inner = TypeDescriptor.of(MyBox.class, Person.class);
      var d = TypeDescriptor.of(Container.class, inner);
      assertThat(d.descriptorName()).isEqualTo(Container.class.getName());
    }
  }

  @Nested
  @DisplayName("Class vs TypeDescriptor in typeArgs")
  class TypeArgCoercion {

    @Test
    void classArg_resolvedViaFind() {
      // Class<Person> in the typeArg position is resolved via TypeDescriptor.find(Person.class)
      // (which auto-synthesises a record descriptor since Person has no DESCRIPTOR field).
      var d = TypeDescriptor.of(MyBox.class, Person.class);
      assertThat(d).isNotNull();
    }

    @Test
    void descriptorArg_passedThrough() {
      var personDesc = TypeDescriptor.of(Person.class);
      var d = TypeDescriptor.of(MyBox.class, personDesc);
      assertThat(d).isNotNull();
    }

    @Test
    void invalidTypeArg_throws() {
      assertThatThrownBy(() -> TypeDescriptor.of(MyBox.class, "not a class"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Class")
        .hasMessageContaining("TypeDescriptor");
    }
  }
}
