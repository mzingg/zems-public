package dev.zems.lib.value.marshal.descriptor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.marshal.StateReader;
import dev.zems.lib.value.marshal.StateWriter;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Contract test for {@link TypeDescriptorBuilder} validation branches. Happy-path "configured field propagates to built
 * descriptor" assertions are store-and-read pairs and live in journey tests if needed at all.
 */
@ContractTest
@DisplayName("TypeDescriptorBuilder")
class TypeDescriptorBuilderTest {

  static Stream<Function<TypeDescriptorBuilder<String>, TypeDescriptorBuilder<String>>> chainSteps() {
    Function<StateReader, String> reader = r -> "x";
    BiConsumer<StateWriter, String> writer = (sw, s) -> {};
    return Stream.of(
      b -> b.reader(reader).writer(writer),
      b -> b.writer(writer).reader(reader),
      b -> b.reader(reader).withName("X").writer(writer),
      b -> b.reader(reader).writer(writer).withAliases("a"),
      b -> b.reader(reader).writer(writer).withEvolutionPolicy(EvolutionPolicy.LENIENT)
    );
  }

  @Test
  @DisplayName("TypeDescriptor.builder(null) throws NPE")
  void builderRejectsNullClazz() {
    assertThatThrownBy(() -> TypeDescriptor.builder(null))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("clazz must not be null");
  }

  @ParameterizedTest(name = "withName({0}) is rejected")
  @ValueSource(strings = { "", " ", "   ", "\t\n" })
  @DisplayName("withName rejects blank input")
  void withNameRejectsBlank(String blank) {
    var b = TypeDescriptor.builder(String.class);
    assertThatThrownBy(() -> b.withName(blank))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("name must not be null or blank");
  }

  @Test
  @DisplayName("withName(null) is rejected")
  void withNameRejectsNull() {
    var b = TypeDescriptor.builder(String.class);
    assertThatThrownBy(() -> b.withName(null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("name must not be null or blank");
  }

  @Test
  @DisplayName("withAliases(null) throws NPE")
  void withAliasesRejectsNull() {
    var b = TypeDescriptor.builder(String.class);
    assertThatThrownBy(() -> b.withAliases((String[]) null))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("aliases must not be null");
  }

  @Test
  @DisplayName("withEvolutionPolicy(null) throws NPE")
  void withEvolutionPolicyRejectsNull() {
    var b = TypeDescriptor.builder(String.class);
    assertThatThrownBy(() -> b.withEvolutionPolicy(null))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("policy must not be null");
  }

  @Test
  @DisplayName("reader(null) throws NPE")
  void readerRejectsNull() {
    var b = TypeDescriptor.builder(String.class);
    assertThatThrownBy(() -> b.reader(null))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("reader must not be null");
  }

  @Test
  @DisplayName("writer(null) throws NPE")
  void writerRejectsNull() {
    var b = TypeDescriptor.builder(String.class);
    assertThatThrownBy(() -> b.writer(null))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("writer must not be null");
  }

  @Test
  @DisplayName("build() without reader throws ISE")
  void buildWithoutReaderThrows() {
    var b = TypeDescriptor.builder(String.class).writer((sw, s) -> {});
    assertThatThrownBy(b::build)
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("reader is required — call .reader(...) before build()");
  }

  @Test
  @DisplayName("build() without writer throws ISE")
  void buildWithoutWriterThrows() {
    var b = TypeDescriptor.builder(String.class).reader(r -> "x");
    assertThatThrownBy(b::build)
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("writer is required — call .writer(...) before build()");
  }

  @ParameterizedTest
  @MethodSource("chainSteps")
  @DisplayName("build() succeeds when reader and writer are both supplied")
  void buildSucceedsWithReaderAndWriter(Function<TypeDescriptorBuilder<String>, TypeDescriptorBuilder<String>> chain) {
    chain.apply(TypeDescriptor.builder(String.class)).build();
  }
}
