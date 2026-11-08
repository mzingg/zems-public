package dev.zems.lib.value;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.valueio.ValueIo;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
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
  @DisplayName("a tombstone is not null, is defined, is resolved and carries no error")
  void existingPredicatesUnchangedForTombstone() {
    var t = Value.<String>tombstone();
    assertThat(t.isNotNull()).isTrue();
    assertThat(t.isDefined()).isTrue();
    assertThat(t.isResolved()).isTrue();
    assertThat(t.error()).isEmpty();
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
