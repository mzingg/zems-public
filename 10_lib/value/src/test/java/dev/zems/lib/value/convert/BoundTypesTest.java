package dev.zems.lib.value.convert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.fixture.Twin;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("BoundTypes")
class BoundTypesTest {

  private static final String SHAPE_NAME = "dev.zems.lib.value.convert.BoundTypesTest$Shape";
  private static final String CIRCLE_NAME = "dev.zems.lib.value.convert.BoundTypesTest$Circle";
  private static final String NOT_A_QUALIFIED_NAME = ": its name is not a qualified type name";

  @Test
  @DisplayName("a null base class is a programming bug and throws")
  void nullBaseIsRefused() {
    assertThatThrownBy(() -> BoundTypes.of(null, Circle.class))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Base class must not be null");
  }

  @Test
  @DisplayName("a null member array is a programming bug and throws")
  void nullMemberArrayIsRefused() {
    assertThatThrownBy(() -> BoundTypes.of(Shape.class, (Class<? extends Shape>[]) null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Bound classes must not be null");
  }

  @Test
  @DisplayName("a null member is a programming bug and throws")
  void nullMemberIsRefused() {
    assertThatThrownBy(() -> BoundTypes.of(Shape.class, Circle.class, null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Bound class must not be null");
  }

  @Test
  @DisplayName("a member that is not a subtype of the base is refused, even when raw use slips it past the compiler")
  void memberOutsideTheBaseIsRefused() {
    assertThatThrownBy(() -> BoundTypes.of(Shape.class, Circle.class, disguisedAsShape(String.class)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Cannot bind java.lang.String: it is not a " + SHAPE_NAME);
  }

  @Test
  @DisplayName("an array class and a hidden class are refused: neither name is a qualified type name")
  void memberWithoutAQualifiedNameIsRefused() {
    Runnable task = () -> {};

    assertThat(task.getClass().isHidden()).as("precondition: a lambda's class is hidden").isTrue();
    assertThatThrownBy(() -> BoundTypes.of(Object.class, String[].class))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Cannot bind [Ljava.lang.String;" + NOT_A_QUALIFIED_NAME);
    assertThatThrownBy(() -> BoundTypes.of(Runnable.class, task.getClass()))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Cannot bind " + task.getClass().getName() + NOT_A_QUALIFIED_NAME);
  }

  @Test
  @DisplayName("two distinct classes that share one name are refused — the name could not tell them apart")
  void twoDistinctClassesSharingANameAreRefused() throws IOException {
    var twinCopy = new TwinLoader().defineCopyOf(Twin.class);

    assertThat(twinCopy.getName()).as("precondition: the copy has the same name").isEqualTo(Twin.class.getName());
    assertThat(twinCopy).as("precondition: the copy is a distinct class").isNotSameAs(Twin.class);
    assertThatThrownBy(() -> BoundTypes.of(Object.class, Twin.class, twinCopy))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Cannot bind dev.zems.lib.value.fixture.Twin twice: two distinct classes share that name");
  }

  @Test
  @DisplayName("members are checked one by one in bind order, so the first bad member decides the message")
  void firstBadMemberDecidesTheMessage() {
    assertThatThrownBy(() -> BoundTypes.of(Object.class, Circle.class, String[].class, null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Cannot bind [Ljava.lang.String;" + NOT_A_QUALIFIED_NAME);
    assertThatThrownBy(() -> BoundTypes.of(Shape.class, disguisedAsShape(String[].class)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Cannot bind [Ljava.lang.String;: it is not a " + SHAPE_NAME);
  }

  @Test
  @DisplayName("binding one class twice binds it once")
  void oneClassBoundTwiceIsBoundOnce() {
    BoundTypes<Shape> shapes = BoundTypes.of(Shape.class, Circle.class, Circle.class);

    assertThat(shapes).hasToString("[" + CIRCLE_NAME + "]");
  }

  @Test
  @DisplayName("toString lists the bound names in bind order; unrelated classes bind under Object")
  void toStringListsTheBoundNamesInBindOrder() {
    BoundTypes<Object> unrelated = BoundTypes.of(Object.class, String.class, Long.class, Integer.class);
    BoundTypes<Shape> empty = BoundTypes.of(Shape.class);

    assertThat(unrelated).hasToString("[java.lang.String, java.lang.Long, java.lang.Integer]");
    assertThat(empty).hasToString("[]");
  }

  // The cast lies on purpose: it slips a class past the compiler as a caller's raw use would, to reach the runtime check.
  @SuppressWarnings("unchecked")
  private static Class<? extends Shape> disguisedAsShape(Class<?> type) {
    return (Class<? extends Shape>) type;
  }

  interface Shape {}

  record Circle(double radius) implements Shape {}

  private static final class TwinLoader extends ClassLoader {

    TwinLoader() {
      super(BoundTypesTest.class.getClassLoader());
    }

    Class<?> defineCopyOf(Class<?> original) throws IOException {
      try (InputStream bytecode = original.getResourceAsStream(original.getSimpleName() + ".class")) {
        byte[] bytes = bytecode.readAllBytes();
        return defineClass(original.getName(), bytes, 0, bytes.length);
      }
    }
  }
}
