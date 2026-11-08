package dev.zems.lib.value.valueio;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import dev.zems.lib.value.valueio.format.binary.BinaryStateReader;
import dev.zems.lib.value.valueio.format.binary.BinaryStateWriter;
import dev.zems.lib.value.valueio.format.binary.SegmentCursors;
import dev.zems.lib.value.valueio.format.binary.SizingStateWriter;
import dev.zems.lib.value.valueio.format.json.JsonStateReader;
import dev.zems.lib.value.valueio.format.json.JsonStateWriter;
import dev.zems.lib.value.valueio.wire.WireConstraints;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Entry point for reading and writing {@link Value Value}s: pick a mode ({@link #framed()} or {@link #streaming()}),
 * chain the envelope options, then call a terminal factory or a Stream-API method.
 *
 * <p>
 * Framed mode holds exactly one top-level record; streaming mode is an open-ended record sequence that ends at EOF and
 * cannot carry a checksum. Readers and writers are thread-confined. Caller-supplied channels and segments are never
 * closed; resources a factory opened (file channels, mapped segments, staging arenas) are released on {@code close()}.
 */
public final class ValueIo {

  /** Staging buffer size for channel-backed readers and writers. */
  public static final int DEFAULT_STAGING_BYTES = 8 * 1024;

  private static final String CHANNEL_NULL = "channel must not be null";
  private static final String PATH_NULL = "path must not be null";

  private final Protocol.V1 protocol;

  private ValueIo(Protocol.V1 protocol) {
    this.protocol = protocol;
  }

  /** Single-document envelope: header on open, terminator on close, exactly one top-level record in between. */
  public static ValueIo framed() {
    return new ValueIo(Protocol.V1.framed());
  }

  /** Open-ended sequence of records: no header, no terminator, end-of-stream is EOF. */
  public static ValueIo streaming() {
    return new ValueIo(Protocol.V1.streaming());
  }

  public ValueIo usingTypeVerification() {
    return new ValueIo(protocol.usingTypeVerification());
  }

  public ValueIo withoutTypeVerification() {
    return new ValueIo(protocol.withoutTypeVerification());
  }

  /** A copy with the given wire-level safety bounds in place of {@link WireConstraints#SECURE_DEFAULTS}. */
  public ValueIo withWireConstraints(WireConstraints constraints) {
    return new ValueIo(protocol.withWireConstraints(constraints));
  }

  /** A copy with the given checksum algorithm; throws {@link IllegalStateException} in {@link #streaming()} mode. */
  public ValueIo withChecksum(ChecksumAlgorithm algorithm) {
    return new ValueIo(protocol.withChecksum(algorithm));
  }

  public Protocol.Mode mode() {
    return protocol.mode();
  }

  public ChecksumAlgorithm checksumAlgorithm() {
    return protocol.checksumAlgorithm();
  }

  public WireConstraints wireConstraints() {
    return protocol.wireConstraints();
  }

  /** Whether each record also writes its descriptor name and signature for read-side verification. */
  public boolean typeVerificationEnabled() {
    return protocol.typeVerificationEnabled();
  }

  /** Creates and starts a writer for a custom wire format bound to this configuration. */
  public <W extends AbstractStateWriter> W writer(Protocol.WriterFactory<W> factory) {
    return protocol.writer(factory);
  }

  /** Creates and starts a reader for a custom wire format bound to this configuration. */
  public <R extends AbstractStateReader> R reader(Protocol.ReaderFactory<R> factory) {
    return protocol.reader(factory);
  }

  /** Writes into a bounded segment (heap, off-heap or mapped) that the caller owns and closes. */
  public BinaryStateWriter binaryWriter(MemorySegment segment) {
    Objects.requireNonNull(segment, "segment must not be null");
    return protocol.writer(p -> new BinaryStateWriter(p, SegmentCursors.boundedWriter(segment)));
  }

  /** Channel-backed writer staging through {@value #DEFAULT_STAGING_BYTES} bytes in an arena of its own. */
  public BinaryStateWriter binaryWriter(WritableByteChannel channel) {
    return binaryWriter(channel, DEFAULT_STAGING_BYTES);
  }

  public BinaryStateWriter binaryWriter(WritableByteChannel channel, int stagingBytes) {
    Objects.requireNonNull(channel, CHANNEL_NULL);
    return protocol.writer(p -> new BinaryStateWriter(p, SegmentCursors.stagedWriter(channel, stagingBytes, false)));
  }

  /** Channel-backed writer staging in the caller's {@link Arena}; the caller closes the arena after the writer. */
  public BinaryStateWriter binaryWriter(WritableByteChannel channel, Arena arena, int stagingBytes) {
    Objects.requireNonNull(channel, CHANNEL_NULL);
    Objects.requireNonNull(arena, "arena must not be null");
    return protocol.writer(p ->
      new BinaryStateWriter(p, SegmentCursors.stagedWriter(channel, arena, stagingBytes, false, false))
    );
  }

  public BinaryStateWriter binaryWriter(OutputStream out) {
    Objects.requireNonNull(out, "out must not be null");
    return binaryWriter(Channels.newChannel(out));
  }

  /** Reads from a bounded segment (heap, off-heap or mapped) that the caller owns and closes. */
  public BinaryStateReader binaryReader(MemorySegment segment) {
    Objects.requireNonNull(segment, "segment must not be null");
    return protocol.reader(p -> new BinaryStateReader(p, SegmentCursors.bounded(segment)));
  }

  public BinaryStateReader binaryReader(byte[] data) {
    Objects.requireNonNull(data, "data must not be null");
    return binaryReader(MemorySegment.ofArray(data));
  }

  public BinaryStateReader binaryReader(ReadableByteChannel channel) {
    return binaryReader(channel, DEFAULT_STAGING_BYTES);
  }

  public BinaryStateReader binaryReader(ReadableByteChannel channel, int stagingBytes) {
    Objects.requireNonNull(channel, CHANNEL_NULL);
    return protocol.reader(p -> new BinaryStateReader(p, SegmentCursors.stagedReader(channel, stagingBytes, false)));
  }

  public BinaryStateReader binaryReader(ReadableByteChannel channel, Arena arena, int stagingBytes) {
    Objects.requireNonNull(channel, CHANNEL_NULL);
    Objects.requireNonNull(arena, "arena must not be null");
    return protocol.reader(p ->
      new BinaryStateReader(p, SegmentCursors.stagedReader(channel, arena, stagingBytes, false, false))
    );
  }

  public BinaryStateReader binaryReader(InputStream in) {
    Objects.requireNonNull(in, "in must not be null");
    return binaryReader(Channels.newChannel(in));
  }

  /** Creates or truncates the file; the writer owns the channel and closes it. */
  public BinaryStateWriter binaryWriterToFile(Path path) {
    Objects.requireNonNull(path, PATH_NULL);
    return protocol.writer(p -> {
      try {
        var ch = FileChannel.open(
          path,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE
        );
        return new BinaryStateWriter(p, SegmentCursors.stagedWriter(ch, DEFAULT_STAGING_BYTES, true));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    });
  }

  /** Memory-maps the file read-only; the reader owns the mapping and the channel and releases both on close. */
  public BinaryStateReader binaryReaderFromFile(Path path) {
    Objects.requireNonNull(path, PATH_NULL);
    return protocol.reader(p -> new BinaryStateReader(p, SegmentCursors.mmapReadOnly(path)));
  }

  public SizingStateWriter sizingWriter() {
    return protocol.writer(SizingStateWriter::new);
  }

  public JsonStateWriter jsonWriter(Writer out) {
    Objects.requireNonNull(out, "out must not be null");
    return protocol.writer(p -> new JsonStateWriter(p, out));
  }

  public JsonStateWriter jsonWriter(WritableByteChannel channel) {
    Objects.requireNonNull(channel, CHANNEL_NULL);
    return jsonWriter(new OutputStreamWriter(Channels.newOutputStream(channel), StandardCharsets.UTF_8));
  }

  public JsonStateReader jsonReader(Reader in) {
    Objects.requireNonNull(in, "in must not be null");
    return protocol.reader(p -> new JsonStateReader(p, in));
  }

  public JsonStateReader jsonReader(ReadableByteChannel channel) {
    Objects.requireNonNull(channel, CHANNEL_NULL);
    return jsonReader(new InputStreamReader(Channels.newInputStream(channel), StandardCharsets.UTF_8));
  }

  public JsonStateWriter jsonWriterToFile(Path path) {
    Objects.requireNonNull(path, PATH_NULL);
    try {
      var ch = FileChannel.open(
        path,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE
      );
      return jsonWriter(ch);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public JsonStateReader jsonReaderFromFile(Path path) {
    Objects.requireNonNull(path, PATH_NULL);
    try {
      var ch = FileChannel.open(path, StandardOpenOption.READ);
      return jsonReader(ch);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public <T> Stream<Value<T>> binaryRecords(MemorySegment seg, Class<T> type) {
    return binaryRecords(seg, StreamApi.requireDescriptor(type));
  }

  public <T> Stream<Value<T>> binaryRecords(MemorySegment seg, TypeDescriptor<T> descriptor) {
    return StreamApi.recordsStream(binaryReader(seg), descriptor);
  }

  public <T> Stream<Value<T>> binaryRecords(ReadableByteChannel ch, Class<T> type) {
    return binaryRecords(ch, StreamApi.requireDescriptor(type));
  }

  public <T> Stream<Value<T>> binaryRecords(ReadableByteChannel ch, TypeDescriptor<T> descriptor) {
    return StreamApi.recordsStream(binaryReader(ch), descriptor);
  }

  public <T> Stream<Value<T>> binaryRecords(InputStream in, Class<T> type) {
    return binaryRecords(in, StreamApi.requireDescriptor(type));
  }

  public <T> Stream<Value<T>> binaryRecords(InputStream in, TypeDescriptor<T> descriptor) {
    return StreamApi.recordsStream(binaryReader(in), descriptor);
  }

  public <T> Stream<Value<T>> binaryRecordsFromFile(Path path, Class<T> type) {
    return binaryRecordsFromFile(path, StreamApi.requireDescriptor(type));
  }

  public <T> Stream<Value<T>> binaryRecordsFromFile(Path path, TypeDescriptor<T> descriptor) {
    return StreamApi.recordsStream(binaryReaderFromFile(path), descriptor);
  }

  public <T> void binaryWriteAll(MemorySegment seg, TypeDescriptor<T> descriptor, Stream<? extends Value<T>> values) {
    try (var w = binaryWriter(seg); values) {
      StreamApi.writeAll(w, descriptor, protocol.mode(), values);
    }
  }

  public <T> void binaryWriteAll(MemorySegment seg, Class<T> type, Stream<? extends Value<T>> values) {
    binaryWriteAll(seg, StreamApi.requireDescriptor(type), values);
  }

  public <T> void binaryWriteAll(
    WritableByteChannel ch,
    TypeDescriptor<T> descriptor,
    Stream<? extends Value<T>> values
  ) {
    try (var w = binaryWriter(ch); values) {
      StreamApi.writeAll(w, descriptor, protocol.mode(), values);
    }
  }

  public <T> void binaryWriteAll(WritableByteChannel ch, Class<T> type, Stream<? extends Value<T>> values) {
    binaryWriteAll(ch, StreamApi.requireDescriptor(type), values);
  }

  public <T> void binaryWriteAll(OutputStream out, TypeDescriptor<T> descriptor, Stream<? extends Value<T>> values) {
    try (var w = binaryWriter(out); values) {
      StreamApi.writeAll(w, descriptor, protocol.mode(), values);
    }
  }

  public <T> void binaryWriteAll(OutputStream out, Class<T> type, Stream<? extends Value<T>> values) {
    binaryWriteAll(out, StreamApi.requireDescriptor(type), values);
  }

  public <T> void binaryWriteAllToFile(Path path, TypeDescriptor<T> descriptor, Stream<? extends Value<T>> values) {
    try (var w = binaryWriterToFile(path); values) {
      StreamApi.writeAll(w, descriptor, protocol.mode(), values);
    }
  }

  public <T> void binaryWriteAllToFile(Path path, Class<T> type, Stream<? extends Value<T>> values) {
    binaryWriteAllToFile(path, StreamApi.requireDescriptor(type), values);
  }

  public <T> Stream<Value<T>> jsonRecords(Reader in, Class<T> type) {
    return jsonRecords(in, StreamApi.requireDescriptor(type));
  }

  public <T> Stream<Value<T>> jsonRecords(Reader in, TypeDescriptor<T> descriptor) {
    return StreamApi.recordsStream(jsonReader(in), descriptor);
  }

  public <T> Stream<Value<T>> jsonRecords(ReadableByteChannel ch, Class<T> type) {
    return jsonRecords(ch, StreamApi.requireDescriptor(type));
  }

  public <T> Stream<Value<T>> jsonRecords(ReadableByteChannel ch, TypeDescriptor<T> descriptor) {
    return StreamApi.recordsStream(jsonReader(ch), descriptor);
  }

  public <T> Stream<Value<T>> jsonRecordsFromFile(Path path, Class<T> type) {
    return jsonRecordsFromFile(path, StreamApi.requireDescriptor(type));
  }

  public <T> Stream<Value<T>> jsonRecordsFromFile(Path path, TypeDescriptor<T> descriptor) {
    return StreamApi.recordsStream(jsonReaderFromFile(path), descriptor);
  }

  public <T> void jsonWriteAll(Writer out, TypeDescriptor<T> descriptor, Stream<? extends Value<T>> values) {
    try (var w = jsonWriter(out); values) {
      StreamApi.writeAll(w, descriptor, protocol.mode(), values);
    }
  }

  public <T> void jsonWriteAll(Writer out, Class<T> type, Stream<? extends Value<T>> values) {
    jsonWriteAll(out, StreamApi.requireDescriptor(type), values);
  }

  public <T> void jsonWriteAll(
    WritableByteChannel ch,
    TypeDescriptor<T> descriptor,
    Stream<? extends Value<T>> values
  ) {
    try (var w = jsonWriter(ch); values) {
      StreamApi.writeAll(w, descriptor, protocol.mode(), values);
    }
  }

  public <T> void jsonWriteAll(WritableByteChannel ch, Class<T> type, Stream<? extends Value<T>> values) {
    jsonWriteAll(ch, StreamApi.requireDescriptor(type), values);
  }

  public <T> void jsonWriteAllToFile(Path path, TypeDescriptor<T> descriptor, Stream<? extends Value<T>> values) {
    try (var w = jsonWriterToFile(path); values) {
      StreamApi.writeAll(w, descriptor, protocol.mode(), values);
    }
  }

  public <T> void jsonWriteAllToFile(Path path, Class<T> type, Stream<? extends Value<T>> values) {
    jsonWriteAllToFile(path, StreamApi.requireDescriptor(type), values);
  }
}
