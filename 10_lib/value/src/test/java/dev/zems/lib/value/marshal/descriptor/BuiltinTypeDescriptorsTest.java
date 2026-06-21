package dev.zems.lib.value.marshal.descriptor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common._test.ContractTest;
import java.net.InetAddress;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@ContractTest
@DisplayName("BuiltinTypeDescriptors")
class BuiltinTypeDescriptorsTest {

  @Test
  void allContains27Descriptors() {
    assertThat(BuiltinTypeDescriptors.all()).hasSize(27);
  }

  @Test
  void findReturnsDescriptorForJdkTypes() {
    assertThat(BuiltinTypeDescriptors.find(String.class)).isPresent();
    assertThat(BuiltinTypeDescriptors.find(Integer.class)).isPresent();
    assertThat(BuiltinTypeDescriptors.find(Instant.class)).isPresent();
  }

  @Test
  void findReturnsEmptyForUnknownClass() {
    assertThat(BuiltinTypeDescriptors.find(Object.class)).isEmpty();
  }

  @Test
  void findResolvesSubtypeToRegisteredSupertypeDescriptor() {
    // InetAddress.ofLiteral returns a concrete subtype (Inet4Address); it must still resolve to the
    // InetAddress descriptor. Same for ZoneId.of (ZoneRegion) and ZoneOffset.
    assertThat(BuiltinTypeDescriptors.find(InetAddress.ofLiteral("192.168.1.1").getClass()).orElseThrow()).isSameAs(
      BuiltinTypeDescriptors.INET_ADDRESS
    );
    assertThat(BuiltinTypeDescriptors.find(ZoneId.of("Europe/Zurich").getClass()).orElseThrow()).isSameAs(
      BuiltinTypeDescriptors.ZONE_ID
    );
    assertThat(BuiltinTypeDescriptors.find(ZoneOffset.ofHours(2).getClass()).orElseThrow()).isSameAs(
      BuiltinTypeDescriptors.ZONE_ID
    );
  }

  @Test
  void descriptorNameMatchesQualifiedClassName() {
    assertThat(BuiltinTypeDescriptors.STRING.descriptorName()).isEqualTo("java.lang.String");
    assertThat(BuiltinTypeDescriptors.UUID_DESCRIPTOR.descriptorName()).isEqualTo("java.util.UUID");
    assertThat(BuiltinTypeDescriptors.URI_DESCRIPTOR.descriptorName()).isEqualTo("java.net.URI");
  }

  @Test
  void findBySymbolMapsShortNamesToDescriptors() {
    assertThat(BuiltinTypeDescriptors.findBySymbol("bigint")).contains(BuiltinTypeDescriptors.BIG_INTEGER);
    assertThat(BuiltinTypeDescriptors.findBySymbol("ip")).contains(BuiltinTypeDescriptors.INET_ADDRESS);
    assertThat(BuiltinTypeDescriptors.findBySymbol("bool")).contains(BuiltinTypeDescriptors.BOOLEAN);
  }

  @Test
  void findBySymbolReturnsEmptyForTreeOnlyAndUnknownSymbols() {
    // "number" and "ref" are tree-spec pseudo-symbols, not value-lib concrete types.
    assertThat(BuiltinTypeDescriptors.findBySymbol("number")).isEmpty();
    assertThat(BuiltinTypeDescriptors.findBySymbol("ref")).isEmpty();
    assertThat(BuiltinTypeDescriptors.findBySymbol("nope")).isEmpty();
  }

  @Test
  void everyBuiltinHasASymbolThatRoundTrips() {
    for (var descriptor : BuiltinTypeDescriptors.all()) {
      String symbol = BuiltinTypeDescriptors.symbolFor(descriptor).orElseThrow(() ->
        new AssertionError("no symbol for " + descriptor.descriptorName())
      );
      assertThat(BuiltinTypeDescriptors.findBySymbol(symbol)).contains(descriptor);
    }
  }
}
