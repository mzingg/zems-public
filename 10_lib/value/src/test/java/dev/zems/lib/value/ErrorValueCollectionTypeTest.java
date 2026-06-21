package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.marshal.Protocol;
import dev.zems.lib.value.marshal.ValueIo;
import dev.zems.lib.value.marshal.descriptor.ListTypeDescriptor;
import dev.zems.lib.value.marshal.descriptor.MapTypeDescriptor;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression for review #4: an ErrorValue read through a {@link ListTypeDescriptor} or {@link MapTypeDescriptor}
 * previously lost element-type info, becoming an {@code ErrorValue<List>} (raw). With Shape B (ErrorValue carries a
 * {@link TypeDescriptor}), the original descriptor survives the round-trip.
 */
@DisplayName("ErrorValue collection type round-trip (review #4)")
@ContractTest
class ErrorValueCollectionTypeTest {

  @Test
  void listElementTypeSurvivesBinaryRoundTrip() {
    TypeDescriptor<String> elementType = TypeDescriptor.of("java.lang.String", String.class);
    TypeDescriptor<List<Value<String>>> listDesc = TypeDescriptor.ofList("List<String>", elementType);

    var original = Value.errorOf(new IOException("boom"), listDesc);
    var bos = new ByteArrayOutputStream();
    try (var w = ValueIo.framed().binaryWriter(bos)) {
      w.write(original, listDesc);
    }
    Value<List<Value<String>>> read;
    try (var r = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
      read = r.read(listDesc);
    }

    assertThat(read).isInstanceOf(ErrorValue.class);
    var ev = (ErrorValue<?>) read;
    assertThat(ev.expectedType()).isInstanceOf(ListTypeDescriptor.class);
    assertThat(ev.expectedType().qualifiedName()).isEqualTo("List<java.lang.String>");
  }

  @Test
  void mapKeyValueTypesSurviveBinaryRoundTrip() {
    var keyType = TypeDescriptor.of("java.lang.String", String.class);
    var valueType = TypeDescriptor.of("java.lang.Integer", Integer.class);
    TypeDescriptor<Map<String, Value<Integer>>> mapDesc = TypeDescriptor.ofMap(
      "Map<String,Integer>",
      keyType,
      valueType
    );

    var original = Value.errorOf(new IOException("boom"), mapDesc);
    var bos = new ByteArrayOutputStream();
    try (var w = ValueIo.framed().binaryWriter(bos)) {
      w.write(original, mapDesc);
    }
    Value<Map<String, Value<Integer>>> read;
    try (var r = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
      read = r.read(mapDesc);
    }

    assertThat(read).isInstanceOf(ErrorValue.class);
    var ev = (ErrorValue<?>) read;
    assertThat(ev.expectedType()).isInstanceOf(MapTypeDescriptor.class);
    assertThat(ev.expectedType().qualifiedName()).isEqualTo("Map<java.lang.String, java.lang.Integer>");
  }

  @Test
  void scalarTypeSurvivesBinaryRoundTrip() {
    var stringDesc = TypeDescriptor.of("java.lang.String", String.class);

    var original = Value.errorOf(new IOException("boom"), stringDesc);
    var bos = new ByteArrayOutputStream();
    try (var w = ValueIo.framed().binaryWriter(bos)) {
      w.write(original, stringDesc);
    }
    Value<String> read;
    try (var r = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
      read = r.read(stringDesc);
    }

    assertThat(read).isInstanceOf(ErrorValue.class);
    var ev = (ErrorValue<?>) read;
    assertThat(ev.expectedType().qualifiedName()).isEqualTo("java.lang.String");
  }

  @Test
  void listElementTypeSurvivesJsonRoundTrip() {
    TypeDescriptor<String> elementType = TypeDescriptor.of("java.lang.String", String.class);
    TypeDescriptor<List<Value<String>>> listDesc = TypeDescriptor.ofList("List<String>", elementType);

    var original = Value.errorOf(new IOException("boom"), listDesc);
    var sw = new StringWriter();
    try (var w = ValueIo.framed().jsonWriter(sw)) {
      w.write(original, listDesc);
    }
    Value<List<Value<String>>> read;
    try (var r = ValueIo.framed().jsonReader(new StringReader(sw.toString()))) {
      read = r.read(listDesc);
    }

    assertThat(read).isInstanceOf(ErrorValue.class);
    var ev = (ErrorValue<?>) read;
    assertThat(ev.expectedType().qualifiedName()).isEqualTo("List<java.lang.String>");
  }
}
