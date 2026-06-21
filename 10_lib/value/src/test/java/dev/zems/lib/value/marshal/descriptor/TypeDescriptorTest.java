package dev.zems.lib.value.marshal.descriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.Protocol;
import dev.zems.lib.value.marshal.ValueIo;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TypeDescriptor")
@ContractTest
class TypeDescriptorTest {

  public record Point(int x, int y) {
    public static final TypeDescriptor<Point> DESCRIPTOR = TypeDescriptor.of(
      "test.Point",
      Point.class,
      r -> new Point(r.readInt(0, "x"), r.readInt(0, "y")),
      (w, p) -> {
        w.writeInt(0, "x", p.x());
        w.writeInt(0, "y", p.y());
      }
    );
  }

  public record Box(String label) {
    // No DESCRIPTOR field
  }

  @Nested
  @DisplayName("Metadata-only of(name, clazz)")
  class MetadataOnly {

    @Test
    void carriesDescriptorNameAndQualifiedName() {
      var d = TypeDescriptor.of("test.Box", Box.class);
      assertThat(d.descriptorName()).isEqualTo("test.Box");
      assertThat(d.qualifiedName()).isEqualTo(Box.class.getName());
      assertThat(d)
        .asInstanceOf(InstanceOfAssertFactories.type(ScalarTypeDescriptor.class))
        .extracting(ScalarTypeDescriptor::describedClass)
        .isEqualTo(Box.class);
    }

    @Test
    void readWriteThrowWhenUnwired() {
      var d = TypeDescriptor.of("test.Box", Box.class);
      try (var sw = ValueIo.framed().binaryWriter(new ByteArrayOutputStream())) {
        assertThatThrownBy(() -> d.write(sw, new Box("x")))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("No marshalling logic");
      }
    }

    @Test
    void adoptsBuiltinReadWriteForJdkTypes() {
      var d = TypeDescriptor.of("custom.Name", String.class);
      var bos = new ByteArrayOutputStream();
      try (var sw = ValueIo.framed().binaryWriter(bos)) {
        d.write(sw, "hello");
      }
      try (var sr = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThat(d.read(sr)).isEqualTo("hello");
      }
    }

    @Test
    void rejectsNullName() {
      assertThatThrownBy(() -> TypeDescriptor.of(null, String.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("descriptorName");
    }

    @Test
    void rejectsBlankName() {
      assertThatThrownBy(() -> TypeDescriptor.of("  ", String.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("descriptorName");
    }

    @Test
    void rejectsValueTypes() {
      assertThatThrownBy(() -> TypeDescriptor.of("x", dev.zems.lib.value.builtin.StringValue.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot describe Value types");
    }

    @Test
    void rejectsRawList() {
      assertThatThrownBy(() -> TypeDescriptor.of("x", List.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ofList");
    }
  }

  @Nested
  @DisplayName("Wired of(name, clazz, reader, writer)")
  class Wired {

    @Test
    void roundTripsViaCustomLogic() {
      var bos = new ByteArrayOutputStream();
      try (var sw = ValueIo.framed().binaryWriter(bos)) {
        Point.DESCRIPTOR.write(sw, new Point(3, 4));
      }
      try (var sr = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThat(Point.DESCRIPTOR.read(sr)).isEqualTo(new Point(3, 4));
      }
    }
  }

  @Nested
  @DisplayName("find(Class)")
  class Find {

    @Test
    void findsCustomDescriptorViaDESCRIPTORField() {
      var found = TypeDescriptor.find(Point.class);
      assertThat(found).isPresent().get().isSameAs(Point.DESCRIPTOR);
    }

    @Test
    void findsBuiltInForJdkTypes() {
      assertThat(TypeDescriptor.find(String.class)).isPresent();
      assertThat(TypeDescriptor.find(Integer.class)).isPresent();
      assertThat(TypeDescriptor.find(UUID.class)).isPresent();
    }

    @Test
    void emptyForNonRecordClassWithoutDESCRIPTOR() {
      assertThat(TypeDescriptor.find(PlainClass.class)).isEmpty();
    }

    /** Records without an explicit DESCRIPTOR field auto-synthesize on first find() (Phase 4b). */
    @Test
    void autoSynthesizesForRecordWithoutDESCRIPTOR() {
      var found = TypeDescriptor.find(Box.class);
      assertThat(found).isPresent();
      assertThat(found.get()).isInstanceOf(StructuredTypeDescriptor.class);
    }

    @Test
    void rejectsNullClass() {
      assertThatThrownBy(() -> TypeDescriptor.find(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("clazz must not be null");
    }

    /** A non-record class without a DESCRIPTOR field — find() returns empty (no auto-synth fallback for non-records). */
    public static final class PlainClass {}
  }

  @Nested
  @DisplayName("of(Class) — non-auto-dispatchable types")
  class NonAutoDispatchableTypes {

    @Test
    @DisplayName("sealed interface throws with permits list and remediation")
    void sealedInterfaceRejected() {
      assertThatThrownBy(() -> TypeDescriptor.of(Shape.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("sealed interface")
        .hasMessageContaining("auto-dispatch over permitted subtypes is not supported")
        .hasMessageContaining("[Circle, Square]")
        .hasMessageContaining("StructuredTypeDescriptor<Shape>");
    }

    @Test
    @DisplayName("plain (non-sealed) interface throws with interface-specific hint")
    void plainInterfaceRejected() {
      assertThatThrownBy(() -> TypeDescriptor.of(PlainInterface.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("interface")
        .hasMessageContaining("no auto-synthesisable shape");
    }

    @Test
    @DisplayName("abstract class throws with abstract-specific hint")
    void abstractClassRejected() {
      assertThatThrownBy(() -> TypeDescriptor.of(AbstractType.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("abstract class")
        .hasMessageContaining("no canonical constructor");
    }

    @Test
    @DisplayName("record component of sealed-interface type fails record-synth with the sealed-specific hint")
    void recordComponentOfSealedInterfaceRejected() {
      assertThatThrownBy(() -> TypeDescriptor.of(Drawing.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cannot resolve component type")
        .hasMessageContaining("sealed interface")
        .hasMessageContaining("auto-dispatch over permitted subtypes is not supported");
    }

    public sealed interface Shape permits Circle, Square {}

    public interface PlainInterface {}

    public record Circle(double r) implements Shape {}

    public record Square(double side) implements Shape {}

    public abstract static class AbstractType {}

    /**
     * A record component whose declared type is a sealed interface — auto-synth on the parent record must fail with the
     * same sharpened hint.
     */
    public record Drawing(String name, Shape shape) {}
  }

  @Nested
  @DisplayName("ListTypeDescriptor")
  class ListDescriptor {

    @Test
    void roundTripsListOfStrings() {
      var d = TypeDescriptor.ofList("test.list.string", BuiltinTypeDescriptors.STRING);
      var bos = new ByteArrayOutputStream();
      try (var sw = ValueIo.framed().binaryWriter(bos)) {
        d.write(
          sw,
          List.of(dev.zems.lib.value.Value.of("a"), dev.zems.lib.value.Value.of("b"), dev.zems.lib.value.Value.of("c"))
        );
      }
      try (var sr = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThat(d.read(sr)).containsExactly(
          dev.zems.lib.value.Value.of("a"),
          dev.zems.lib.value.Value.of("b"),
          dev.zems.lib.value.Value.of("c")
        );
      }
    }

    @Test
    void rejectsNullName() {
      assertThatThrownBy(() -> TypeDescriptor.ofList(null, BuiltinTypeDescriptors.STRING))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("descriptorName must not be null");
    }

    @Test
    void rejectsBlankName() {
      assertThatThrownBy(() -> TypeDescriptor.ofList("  ", BuiltinTypeDescriptors.STRING))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("descriptorName must not be blank");
    }

    @Test
    void rejectsNullElementType() {
      assertThatThrownBy(() -> TypeDescriptor.ofList("test.list", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("elementType must not be null");
    }

    @Test
    void rejectsNullResultSupplier() {
      assertThatThrownBy(() -> ListTypeDescriptor.of("test.list", BuiltinTypeDescriptors.STRING, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("resultListSupplier must not be null");
    }
  }

  @Nested
  @DisplayName("MapTypeDescriptor")
  class MapDescriptor {

    @Test
    void roundTripsMapOfStringToInt() {
      var d = TypeDescriptor.ofMap("test.map.s2i", BuiltinTypeDescriptors.STRING, BuiltinTypeDescriptors.INTEGER);
      var bos = new ByteArrayOutputStream();
      try (var sw = ValueIo.framed().binaryWriter(bos)) {
        d.write(sw, Map.of("a", dev.zems.lib.value.Value.of(1), "b", dev.zems.lib.value.Value.of(2)));
      }
      try (var sr = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThat(d.read(sr))
          .containsEntry("a", dev.zems.lib.value.Value.of(1))
          .containsEntry("b", dev.zems.lib.value.Value.of(2));
      }
    }

    @Test
    void rejectsNullName() {
      assertThatThrownBy(() ->
        TypeDescriptor.ofMap(null, BuiltinTypeDescriptors.STRING, BuiltinTypeDescriptors.INTEGER)
      )
        .isInstanceOf(NullPointerException.class)
        .hasMessage("descriptorName must not be null");
    }

    @Test
    void rejectsBlankName() {
      assertThatThrownBy(() ->
        TypeDescriptor.ofMap("  ", BuiltinTypeDescriptors.STRING, BuiltinTypeDescriptors.INTEGER)
      )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("descriptorName must not be blank");
    }

    @Test
    void rejectsNullKeyType() {
      assertThatThrownBy(() -> TypeDescriptor.ofMap("test.map", null, BuiltinTypeDescriptors.INTEGER))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("keyType must not be null");
    }

    @Test
    void rejectsNullValueType() {
      assertThatThrownBy(() -> TypeDescriptor.ofMap("test.map", BuiltinTypeDescriptors.STRING, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("valueType must not be null");
    }

    @Test
    void rejectsNullResultSupplier() {
      assertThatThrownBy(() ->
        MapTypeDescriptor.of("test.map", BuiltinTypeDescriptors.STRING, BuiltinTypeDescriptors.INTEGER, null)
      )
        .isInstanceOf(NullPointerException.class)
        .hasMessage("resultMapSupplier must not be null");
    }

    @Test
    @DisplayName("read accumulates into the supplied map type — a TreeMap restores natural-key order")
    void readUsesResultSupplier() {
      var d = MapTypeDescriptor.of(
        "test.map.tree",
        BuiltinTypeDescriptors.STRING,
        BuiltinTypeDescriptors.INTEGER,
        size -> new TreeMap<>()
      );
      var bos = new ByteArrayOutputStream();
      try (var sw = ValueIo.framed().binaryWriter(bos)) {
        var unsorted = new LinkedHashMap<String, Value<Integer>>();
        unsorted.put("c", Value.of(3));
        unsorted.put("a", Value.of(1));
        unsorted.put("b", Value.of(2));
        d.write(sw, unsorted);
      }
      try (var sr = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThat(d.read(sr).keySet()).containsExactly("a", "b", "c");
      }
    }
  }

  @Nested
  @DisplayName("SortedMapTypeDescriptor")
  class SortedMapDescriptor {

    @Test
    void roundTripsSortedMapStringInt() {
      var d = TypeDescriptor.ofSortedMap(
        "test.sortedmap.s2i",
        BuiltinTypeDescriptors.STRING,
        BuiltinTypeDescriptors.INTEGER
      );
      var bos = new ByteArrayOutputStream();
      try (var sw = ValueIo.framed().binaryWriter(bos)) {
        // Write in unsorted order — the reader should still restore natural-key order.
        var unsorted = new java.util.TreeMap<String, dev.zems.lib.value.Value<Integer>>();
        unsorted.put("c", dev.zems.lib.value.Value.of(3));
        unsorted.put("a", dev.zems.lib.value.Value.of(1));
        unsorted.put("b", dev.zems.lib.value.Value.of(2));
        d.write(sw, unsorted);
      }
      try (var sr = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        var reread = d.read(sr);
        assertThat(reread.firstKey()).isEqualTo("a");
        assertThat(reread.lastKey()).isEqualTo("c");
      }
    }

    @Test
    void qualifiedNameReportsSortedMapWrapper() {
      var d = TypeDescriptor.ofSortedMap(
        "test.sortedmap.s2i",
        BuiltinTypeDescriptors.STRING,
        BuiltinTypeDescriptors.INTEGER
      );
      assertThat(d.qualifiedName()).isEqualTo("SortedMap<java.lang.String, java.lang.Integer>");
    }

    @Test
    void rejectsNullName() {
      assertThatThrownBy(() ->
        TypeDescriptor.ofSortedMap(null, BuiltinTypeDescriptors.STRING, BuiltinTypeDescriptors.INTEGER)
      )
        .isInstanceOf(NullPointerException.class)
        .hasMessage("descriptorName must not be null");
    }

    @Test
    void rejectsBlankName() {
      assertThatThrownBy(() ->
        TypeDescriptor.ofSortedMap("  ", BuiltinTypeDescriptors.STRING, BuiltinTypeDescriptors.INTEGER)
      )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("descriptorName must not be blank");
    }

    @Test
    void rejectsNullKeyType() {
      assertThatThrownBy(() -> TypeDescriptor.ofSortedMap("test.sortedmap", null, BuiltinTypeDescriptors.INTEGER))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("keyType must not be null");
    }

    @Test
    void rejectsNullValueType() {
      assertThatThrownBy(() -> TypeDescriptor.ofSortedMap("test.sortedmap", BuiltinTypeDescriptors.STRING, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("valueType must not be null");
    }
  }

  @Nested
  @DisplayName("SetTypeDescriptor")
  class SetDescriptor {

    @Test
    void roundTripsSetOfStrings() {
      var d = TypeDescriptor.ofSet("test.set.string", BuiltinTypeDescriptors.STRING);
      var bos = new ByteArrayOutputStream();
      try (var sw = ValueIo.framed().binaryWriter(bos)) {
        d.write(
          sw,
          Set.of(dev.zems.lib.value.Value.of("a"), dev.zems.lib.value.Value.of("b"), dev.zems.lib.value.Value.of("c"))
        );
      }
      try (var sr = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThat(d.read(sr)).containsExactlyInAnyOrder(
          dev.zems.lib.value.Value.of("a"),
          dev.zems.lib.value.Value.of("b"),
          dev.zems.lib.value.Value.of("c")
        );
      }
    }

    @Test
    void qualifiedNameReportsSetWrapperOverElement() {
      var d = TypeDescriptor.ofSet("test.set.string", BuiltinTypeDescriptors.STRING);
      assertThat(d.qualifiedName()).isEqualTo("Set<java.lang.String>");
    }

    @Test
    void rejectsNullElementType() {
      assertThatThrownBy(() -> TypeDescriptor.ofSet("test.set", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("elementType must not be null");
    }

    @Test
    void rejectsNullName() {
      assertThatThrownBy(() -> TypeDescriptor.ofSet(null, BuiltinTypeDescriptors.STRING))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("descriptorName must not be null");
    }

    @Test
    void rejectsNullResultSupplier() {
      assertThatThrownBy(() -> SetTypeDescriptor.of("test.set", BuiltinTypeDescriptors.STRING, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("resultSetSupplier must not be null");
    }

    @Test
    void rejectsBlankDescriptorName() {
      assertThatThrownBy(() -> TypeDescriptor.ofSet("  ", BuiltinTypeDescriptors.STRING))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("descriptorName");
    }

    @Test
    void dedupesEqualElementsOnRead() {
      // Duplicate "a" entries on the wire are silently coalesced into a single set element.
      var d = TypeDescriptor.ofSet("test.set.dedupe", BuiltinTypeDescriptors.STRING);
      var bos = new ByteArrayOutputStream();
      try (var sw = ValueIo.framed().binaryWriter(bos)) {
        // Write 3 entries with one duplicate via List (the writer doesn't enforce set semantics on its end).
        var listLike = new LinkedHashSet<dev.zems.lib.value.Value<String>>();
        listLike.add(dev.zems.lib.value.Value.of("a"));
        listLike.add(dev.zems.lib.value.Value.of("b"));
        d.write(sw, listLike);
      }
      try (var sr = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThat(d.read(sr)).containsExactlyInAnyOrder(
          dev.zems.lib.value.Value.of("a"),
          dev.zems.lib.value.Value.of("b")
        );
      }
    }
  }
}
