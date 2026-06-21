package dev.zems.lib.value;

import static dev.zems.lib.value.ValueAssertions.assertThat;

import dev.zems.lib.common._test.ContractTest;
import dev.zems.lib.value.marshal.Protocol;
import dev.zems.lib.value.marshal.ValueIo;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TombstoneValue")
@ContractTest
class TombstoneValueTest {

  @Test
  void factoryReturnsSingleton() {
    Value<String> a = Value.tombstone();
    Value<Integer> b = Value.tombstone();
    assertThat(a).isTombstone();
    assertThat(b).isTombstone();
    assertThat(a).isEqualTo(b);
  }

  @Test
  void isTombstoneTrueOnlyForTombstone() {
    assertThat(Value.tombstone().isTombstone()).isTrue();
    assertThat(Value.nullValue().isTombstone()).isFalse();
    assertThat(Value.undefined().isTombstone()).isFalse();
    assertThat(Value.unresolved().isTombstone()).isFalse();
    assertThat(Value.of("hello").isTombstone()).isFalse();
  }

  @Test
  void existingPredicatesUnchangedForTombstone() {
    var t = Value.<String>tombstone();
    // Tombstone is its own state; the existing predicates only check for their specific state.
    assertThat(t.isNotNull()).isTrue(); // not specifically NullValue
    assertThat(t.isDefined()).isTrue(); // not specifically UndefinedValue
    assertThat(t.isResolved()).isTrue(); // not specifically UnresolvedValue
    assertThat(t.error()).isEmpty(); // not ErrorValue
  }

  @Test
  void toStringIsStable() {
    assertThat(Value.tombstone().toString()).isEqualTo("TombstoneValue[]");
  }

  @Test
  void binaryRoundTrip() {
    var bos = new ByteArrayOutputStream();
    try (var w = ValueIo.framed().binaryWriter(bos)) {
      w.write(Value.tombstone(), TypeDescriptor.of(String.class));
    }
    try (var r = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
      Value<String> v = r.read(String.class);
      assertThat(v).isTombstone();
    }
  }

  @Test
  void jsonRoundTrip() {
    var sw = new StringWriter();
    try (var w = ValueIo.framed().jsonWriter(sw)) {
      w.write(Value.tombstone(), TypeDescriptor.of(String.class));
    }
    assertThat(sw.toString()).contains("TOMBSTONE");
    try (var r = ValueIo.framed().jsonReader(new StringReader(sw.toString()))) {
      Value<String> v = r.read(String.class);
      assertThat(v).isTombstone();
    }
  }
}
