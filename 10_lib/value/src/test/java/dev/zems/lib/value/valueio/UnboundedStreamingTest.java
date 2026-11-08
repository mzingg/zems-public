package dev.zems.lib.value.valueio;

import static dev.zems.lib.value.junit.ValueAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zems.lib.common.junit.JourneyTest;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.valueio.descriptor.BuiltinTypeDescriptors;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Lazy / unbounded / multi-threaded round-trips")
class UnboundedStreamingTest {

  @Nested
  @DisplayName("Binary unbounded")
  class BinaryUnbounded {

    @Test
    @JourneyTest(speakingId = "streaming-binary-and-pipeline", acceptance = "a1")
    void streamingOverPipeRoundTripsManyRecords() throws Exception {
      var pipe = Pipe.open();
      int n = 5000;
      var writerFuture = CompletableFuture.runAsync(() -> {
        try (var w = ValueIo.streaming().binaryWriter(pipe.sink())) {
          for (int i = 0; i < n; i++) {
            w.write(Value.of("rec-" + i), TypeDescriptor.of(String.class));
          }
        } finally {
          try {
            pipe.sink().close();
          } catch (IOException ignored) {}
        }
      });
      try (var s = ValueIo.streaming().binaryRecords(pipe.source(), String.class)) {
        var collected = s.map(v -> v.convertTo(String.class).orElseThrow()).toList();
        assertThat(collected).hasSize(n);
        assertThat(collected.getFirst()).isEqualTo("rec-0");
        assertThat(collected.get(n - 1)).isEqualTo("rec-" + (n - 1));
      }
      writerFuture.get(10, TimeUnit.SECONDS);
    }

    @Test
    @JourneyTest(speakingId = "ffm-binary-staging-and-mmap", acceptance = "a1")
    void framedHugeRecordRoundTrip() {
      String big = "abcdefghijklmnopqrstuvwxyz".repeat(20_000);
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().binaryWriter(bos)) {
        w.write(Value.of(big), TypeDescriptor.of(String.class));
      }
      try (var r = ValueIo.framed().binaryReader(new ByteArrayInputStream(bos.toByteArray()))) {
        assertThat(r.read(String.class)).hasStringValue(big);
      }
    }

    @Test
    @JourneyTest(speakingId = "ffm-binary-staging-and-mmap", acceptance = "a2")
    void boundedSegmentRoundTrip() {
      try (var arena = Arena.ofConfined()) {
        MemorySegment seg = arena.allocate(2048);
        try (var w = ValueIo.framed().binaryWriter(seg)) {
          w.write(Value.of("via-segment"), TypeDescriptor.of(String.class));
        }
        try (var r = ValueIo.framed().binaryReader(seg)) {
          assertThat(r.read(String.class)).hasStringValue("via-segment");
        }
      }
    }

    @Test
    @JourneyTest(speakingId = "ffm-binary-staging-and-mmap", acceptance = "a3")
    void mmapFileRoundTrip(@TempDir Path tempDir) {
      Path file = tempDir.resolve("mmap-record.bin");
      try (var w = ValueIo.framed().binaryWriterToFile(file)) {
        w.write(Value.of("mmapped"), TypeDescriptor.of(String.class));
      }
      try (var r = ValueIo.framed().binaryReaderFromFile(file)) {
        assertThat(r.read(String.class)).hasStringValue("mmapped");
      }
    }

    @Test
    @JourneyTest(speakingId = "ffm-binary-staging-and-mmap", acceptance = "a4")
    void mmapStreamingFileWith1000Records(@TempDir Path tempDir) {
      Path file = tempDir.resolve("mmap-stream.bin");
      ValueIo.streaming().binaryWriteAllToFile(
        file,
        String.class,
        IntStream.range(0, 1000).mapToObj(i -> Value.of("e-" + i))
      );
      try (var s = ValueIo.streaming().binaryRecordsFromFile(file, String.class)) {
        var collected = s.map(v -> v.convertTo(String.class).orElseThrow()).toList();
        assertThat(collected).hasSize(1000);
        assertThat(collected.getFirst()).isEqualTo("e-0");
        assertThat(collected.getLast()).isEqualTo("e-999");
      }
    }

    @Test
    @JourneyTest(speakingId = "streaming-binary-and-pipeline", acceptance = "a4")
    void framedChecksumOverChannelRoundTrip() {
      var bos = new ByteArrayOutputStream();
      try (var w = ValueIo.framed().withChecksum(ChecksumAlgorithm.SHA_256).binaryWriter(Channels.newChannel(bos))) {
        w.write(Value.of("checksummed"), TypeDescriptor.of(String.class));
      }
      try (
        var r = ValueIo.framed()
          .withChecksum(ChecksumAlgorithm.SHA_256)
          .binaryReader(Channels.newChannel(new ByteArrayInputStream(bos.toByteArray())))
      ) {
        assertThat(r.read(String.class)).hasStringValue("checksummed");
      }
    }
  }

  @Nested
  @DisplayName("JSON unbounded")
  class JsonUnbounded {

    @Test
    @JourneyTest(speakingId = "streaming-json-and-resource-ownership", acceptance = "a1")
    void streamingOverPipedReaderWriterRoundTrip() throws Exception {
      var pipeIn = new PipedInputStream(64 * 1024);
      var pipeOut = new PipedOutputStream(pipeIn);
      int n = 1000;
      var writerFuture = CompletableFuture.runAsync(() -> {
        try (var w = ValueIo.streaming().jsonWriter(Channels.newChannel(pipeOut))) {
          for (int i = 0; i < n; i++) {
            w.write(Value.of("j-" + i), TypeDescriptor.of(String.class));
          }
        } finally {
          try {
            pipeOut.close();
          } catch (IOException ignored) {}
        }
      });
      try (var s = ValueIo.streaming().jsonRecords(Channels.newChannel(pipeIn), String.class)) {
        var collected = s.map(v -> v.convertTo(String.class).orElseThrow()).toList();
        assertThat(collected).hasSize(n);
        assertThat(collected.getFirst()).isEqualTo("j-0");
        assertThat(collected.getLast()).isEqualTo("j-999");
      }
      writerFuture.get(10, TimeUnit.SECONDS);
    }

    @Test
    @JourneyTest(speakingId = "streaming-json-and-resource-ownership", acceptance = "a2")
    void framedJsonHugeRecordRoundTrip() {
      String big = "Z".repeat(64_000);
      var sw = new StringWriter();
      try (var w = ValueIo.framed().jsonWriter(sw)) {
        w.write(Value.of(big), TypeDescriptor.of(String.class));
      }
      try (var r = ValueIo.framed().jsonReader(new StringReader(sw.toString()))) {
        assertThat(r.read(String.class)).hasStringValue(big);
      }
    }
  }

  @Nested
  @DisplayName("StreamPipeline (pipeline parallelism)")
  class Pipeline {

    @Test
    @JourneyTest(speakingId = "streaming-binary-and-pipeline", acceptance = "a2")
    void drainAndPumpRoundTrip() throws Exception {
      var bos = new ByteArrayOutputStream();
      var queue = new LinkedBlockingQueue<Value<String>>();
      Value<String> poison = Value.errorMessage("__END__", String.class);
      int n = 200;

      var executor = Executors.newSingleThreadExecutor();
      try {
        var drainFuture = StreamPipeline.drainToWriter(
          ValueIo.streaming().binaryWriter(bos),
          BuiltinTypeDescriptors.STRING,
          queue,
          poison,
          executor
        );
        for (int i = 0; i < n; i++) {
          queue.put(Value.of("p-" + i));
        }
        queue.put(poison);
        drainFuture.get(10, TimeUnit.SECONDS);
      } finally {
        executor.shutdown();
      }

      var sink = new LinkedBlockingQueue<Value<String>>();
      Value<String> endSentinel = Value.errorMessage("__END__", String.class);
      var consumerExecutor = Executors.newSingleThreadExecutor();
      try {
        var pumpFuture = StreamPipeline.pumpFromReader(
          ValueIo.streaming().binaryReader(new ByteArrayInputStream(bos.toByteArray())),
          BuiltinTypeDescriptors.STRING,
          sink,
          endSentinel,
          consumerExecutor
        );
        long count = pumpFuture.get(10, TimeUnit.SECONDS);
        assertThat(count).isEqualTo(n);
      } finally {
        consumerExecutor.shutdown();
      }

      var first = sink.poll();
      assertThat(first).isNotNull();
      assertThat(first).hasStringValue("p-0");
    }
  }

  @Nested
  @DisplayName("Resource ownership")
  class Ownership {

    @Test
    @JourneyTest(speakingId = "streaming-json-and-resource-ownership", acceptance = "a3")
    void readerClosesOwnedFileChannel(@TempDir Path tempDir) {
      Path file = tempDir.resolve("owned.bin");
      try (var w = ValueIo.framed().binaryWriterToFile(file)) {
        w.write(Value.of("data"), TypeDescriptor.of(String.class));
      }
      var r = ValueIo.framed().binaryReaderFromFile(file);
      r.read(String.class);
      r.close();
      assertThat(file.toFile().length()).isGreaterThan(0);
    }

    @Test
    @JourneyTest(speakingId = "streaming-json-and-resource-ownership", acceptance = "a4")
    void writerOwnedArenaIsClosedOnClose() {
      var bos = new ByteArrayOutputStream();
      var w = ValueIo.framed().binaryWriter(bos);
      w.write(Value.of("ok"), TypeDescriptor.of(String.class));
      w.close();
      assertThat(bos.size()).isGreaterThan(0);
    }
  }

  @Nested
  @DisplayName("Validation")
  class Validation {

    @Test
    @JourneyTest(speakingId = "ffm-binary-staging-and-mmap", acceptance = "a5")
    void boundedSegmentOverflowThrows() {
      try (var arena = Arena.ofConfined()) {
        MemorySegment seg = arena.allocate(8);
        assertThatThrownBy(() -> {
          var w = ValueIo.framed().binaryWriter(seg);
          w.write(Value.of("toolarge"), TypeDescriptor.of(String.class));
          w.close();
        })
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Overflow");
      }
    }

    @Test
    @JourneyTest(speakingId = "streaming-binary-and-pipeline", acceptance = "a3")
    void streamingWriteAllToFileEmptyProducesZeroRecords(@TempDir Path tempDir) {
      Path file = tempDir.resolve("empty.bin");
      ValueIo.streaming().binaryWriteAllToFile(file, String.class, Stream.empty());
      try (var s = ValueIo.streaming().binaryRecordsFromFile(file, String.class)) {
        assertThat(s.count()).isEqualTo(0L);
      }
    }
  }
}
