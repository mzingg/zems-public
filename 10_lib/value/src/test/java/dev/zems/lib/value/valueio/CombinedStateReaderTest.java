package dev.zems.lib.value.valueio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.zems.lib.common.junit.ContractTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CombinedStateReader — fail-fast on auxiliary errors")
@ContractTest
class CombinedStateReaderTest {

  @Test
  @DisplayName("returns master value when auxiliaries succeed")
  void returnsMasterValueOnHappyPath() {
    var master = mock(StateReader.class);
    var aux = mock(StateReader.class);
    when(master.readInt(0, "x")).thenReturn(42);
    when(aux.readInt(0, "x")).thenReturn(42);

    var combined = new CombinedStateReader(master, aux);

    assertThat(combined.readInt(0, "x")).isEqualTo(42);
  }

  @Test
  @DisplayName("propagates an auxiliary-reader exception to the caller")
  void propagatesAuxiliaryException() {
    var master = mock(StateReader.class);
    var aux = mock(StateReader.class);
    var planted = new IllegalStateException("aux corruption");
    when(aux.readInt(0, "x")).thenThrow(planted);

    var combined = new CombinedStateReader(master, aux);

    assertThatThrownBy(() -> combined.readInt(0, "x")).isSameAs(planted);
  }

  @Test
  @DisplayName("readUnsupported advances the auxiliary in lock-step and answers the master's reason")
  void readUnsupportedAdvancesAuxiliaryAndAnswersMaster() {
    var master = mock(StateReader.class);
    var aux = mock(StateReader.class);
    when(master.readUnsupported(0, "x")).thenReturn("no conversion exists");
    when(aux.readUnsupported(0, "x")).thenReturn("no conversion exists");

    var combined = new CombinedStateReader(master, aux);

    assertThat(combined.readUnsupported(0, "x")).isEqualTo("no conversion exists");
    verify(aux).readUnsupported(0, "x");
  }

  @Test
  @DisplayName("readString delegates and propagates auxiliary exception")
  void propagatesOnString() {
    var master = mock(StateReader.class);
    var aux = mock(StateReader.class);
    var planted = new RuntimeException("aux blew up");
    when(aux.readString(0, "name")).thenThrow(planted);

    var combined = new CombinedStateReader(master, aux);

    assertThatThrownBy(() -> combined.readString(0, "name")).isSameAs(planted);
  }
}
