package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common.junit.ContractTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@ContractTest
@DisplayName("VersionDialect")
class VersionDialectTest {

  @ParameterizedTest(name = "{0}")
  @CsvSource({ "SEMVER, semver, SemVer", "JDK, jdk, JDK", "MODULE, jpms, JPMS module" })
  @DisplayName("each dialect's symbol and display name are the ones the wire, the spec hint and the messages use")
  void symbolAndDisplayName(VersionDialect dialect, String symbol, String displayName) {
    assertThat(dialect.symbol()).isEqualTo(symbol);
    assertThat(dialect.displayName()).isEqualTo(displayName);
  }
}
