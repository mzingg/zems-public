package dev.zems.lib.value;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zems.lib.common.junit.JourneyTest;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ValueTransformPipelineJourneyTest {

  private static int parsePort(Value<String> raw) {
    return raw
      .map(Integer::parseInt)
      .filter(port -> port > 0 && port < 65_536)
      .recover(8080)
      .orElse(80);
  }

  static Stream<PipelineCase> pipelineCases() {
    return Stream.of(
      new PipelineCase("present, in range", Value.of("8443"), 8443),
      new PipelineCase("explicit null", Value.nullValue(), 80),
      new PipelineCase("filtered out", Value.of("70000"), 80),
      new PipelineCase("parse error", Value.of("abc"), 8080)
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("pipelineCases")
  @JourneyTest(speakingId = "value-transform-combinators", acceptance = "pipeline")
  @DisplayName("map -> filter -> recover -> orElse produces the documented port for every input state")
  void portPipelineProducesDocumentedResult(PipelineCase pipelineCase) {
    int port = parsePort(pipelineCase.input());

    assertThat(port).isEqualTo(pipelineCase.expectedPort());
  }

  record PipelineCase(String label, Value<String> input, int expectedPort) {
    @Override
    public String toString() {
      return label;
    }
  }
}
