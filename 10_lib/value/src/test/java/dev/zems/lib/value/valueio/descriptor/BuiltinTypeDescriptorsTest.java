package dev.zems.lib.value.valueio.descriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.ContractTest;
import dev.zems.lib.value.Quantity;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.Version;
import dev.zems.lib.value.VersionDialect;
import dev.zems.lib.value.junit.RoundTripFormatHarness;
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
  void allContains29Descriptors() {
    assertThat(BuiltinTypeDescriptors.all()).hasSize(29);
  }

  @Test
  @DisplayName("VERSION is appended after TYPE_NAME: the declared order is wire-relevant")
  void versionIsTheLastEntryOfAllAndTypeNameKeepsItsIndex() {
    assertThat(BuiltinTypeDescriptors.all().get(27)).isSameAs(BuiltinTypeDescriptors.TYPE_NAME);
    assertThat(BuiltinTypeDescriptors.all().getLast()).isSameAs(BuiltinTypeDescriptors.VERSION);
  }

  @Test
  @DisplayName("only the dialect-less VERSION joins all(); the dialect descriptors stay out of the union")
  void dialectDescriptorsAreNotInAll() {
    assertThat(BuiltinTypeDescriptors.all()).doesNotContain(
      BuiltinTypeDescriptors.VERSION_SEMVER,
      BuiltinTypeDescriptors.VERSION_JDK,
      BuiltinTypeDescriptors.VERSION_MODULE
    );
  }

  @Test
  void findResolvesVersionToTheDialectLessDescriptor() {
    assertThat(BuiltinTypeDescriptors.find(Version.class).orElseThrow()).isSameAs(BuiltinTypeDescriptors.VERSION);
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
    assertThat(BuiltinTypeDescriptors.TYPE_NAME.descriptorName()).isEqualTo("dev.zems.lib.value.TypeName");
  }

  @Test
  void findBySymbolResolvesTheTypeNameSymbol() {
    assertThat(BuiltinTypeDescriptors.findBySymbol("type")).contains(BuiltinTypeDescriptors.TYPE_NAME);
    assertThat(BuiltinTypeDescriptors.symbolFor(BuiltinTypeDescriptors.TYPE_NAME)).hasValue("type");
  }

  @Test
  void findBySymbolResolvesTheVersionSymbols() {
    assertThat(BuiltinTypeDescriptors.findBySymbol("semver")).contains(BuiltinTypeDescriptors.VERSION_SEMVER);
    assertThat(BuiltinTypeDescriptors.findBySymbol("jdk")).contains(BuiltinTypeDescriptors.VERSION_JDK);
    assertThat(BuiltinTypeDescriptors.findBySymbol("jpms")).contains(BuiltinTypeDescriptors.VERSION_MODULE);
    assertThat(BuiltinTypeDescriptors.findBySymbol("version")).contains(BuiltinTypeDescriptors.VERSION);
  }

  @Test
  void symbolForNamesEachVersionDescriptor() {
    assertThat(BuiltinTypeDescriptors.symbolFor(BuiltinTypeDescriptors.VERSION_SEMVER)).hasValue("semver");
    assertThat(BuiltinTypeDescriptors.symbolFor(BuiltinTypeDescriptors.VERSION_JDK)).hasValue("jdk");
    assertThat(BuiltinTypeDescriptors.symbolFor(BuiltinTypeDescriptors.VERSION_MODULE)).hasValue("jpms");
    assertThat(BuiltinTypeDescriptors.symbolFor(BuiltinTypeDescriptors.VERSION)).hasValue("version");
  }

  @Test
  @DisplayName("the dialect descriptors are named after Version plus the dialect symbol")
  void versionDescriptorNamesCarryTheDialect() {
    assertThat(BuiltinTypeDescriptors.VERSION.descriptorName()).isEqualTo("dev.zems.lib.value.Version");
    assertThat(BuiltinTypeDescriptors.VERSION_SEMVER.descriptorName()).isEqualTo("dev.zems.lib.value.Version:semver");
    assertThat(BuiltinTypeDescriptors.VERSION_JDK.descriptorName()).isEqualTo("dev.zems.lib.value.Version:jdk");
    assertThat(BuiltinTypeDescriptors.VERSION_MODULE.descriptorName()).isEqualTo("dev.zems.lib.value.Version:jpms");
  }

  @Test
  @DisplayName("a dialect descriptor refuses to read another dialect's version")
  void dialectDescriptorRefusesAnotherDialect() {
    Value<Version> module = Value.versionOf("1.0", VersionDialect.MODULE);
    RoundTripFormatHarness.Format binary = RoundTripFormatHarness.binary();
    byte[] wire = binary.write(module, BuiltinTypeDescriptors.VERSION);

    assertThatThrownBy(() -> binary.read(wire, BuiltinTypeDescriptors.VERSION_SEMVER))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Expected a SemVer version but read 'jpms:1.0'");
    assertThat(binary.read(wire, BuiltinTypeDescriptors.VERSION_MODULE)).isEqualTo(module);
  }

  @Test
  @DisplayName("QUANTITY is found by its payload class but stays out of all() and has no type symbol")
  void quantityIsFoundByClassOnly() {
    assertThat(BuiltinTypeDescriptors.find(Quantity.class)).contains(BuiltinTypeDescriptors.QUANTITY);
    assertThat(BuiltinTypeDescriptors.QUANTITY.descriptorName()).isEqualTo("dev.zems.lib.value.Quantity");
    assertThat(BuiltinTypeDescriptors.all()).doesNotContain(BuiltinTypeDescriptors.QUANTITY);
    assertThat(BuiltinTypeDescriptors.symbolFor(BuiltinTypeDescriptors.QUANTITY)).isEmpty();
  }

  @Test
  void findBySymbolMapsShortNamesToDescriptors() {
    assertThat(BuiltinTypeDescriptors.findBySymbol("bigint")).contains(BuiltinTypeDescriptors.BIG_INTEGER);
    assertThat(BuiltinTypeDescriptors.findBySymbol("ip")).contains(BuiltinTypeDescriptors.INET_ADDRESS);
    assertThat(BuiltinTypeDescriptors.findBySymbol("bool")).contains(BuiltinTypeDescriptors.BOOLEAN);
  }

  @Test
  void findBySymbolReturnsEmptyForTreeOnlyAndUnknownSymbols() {
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
