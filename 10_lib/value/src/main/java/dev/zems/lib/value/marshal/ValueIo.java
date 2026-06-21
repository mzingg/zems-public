package dev.zems.lib.value.marshal;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import dev.zems.lib.value.marshal.format.binary.BinaryStateReader;
import dev.zems.lib.value.marshal.format.binary.BinaryStateWriter;
import dev.zems.lib.value.marshal.format.binary.SegmentCursors;
import dev.zems.lib.value.marshal.format.binary.SizingStateWriter;
import dev.zems.lib.value.marshal.format.json.JsonStateReader;
import dev.zems.lib.value.marshal.format.json.JsonStateWriter;
import dev.zems.lib.value.marshal.wire.WireConstraints;
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
 * Entry point for reading and writing {@link Value Value}s. Pick a mode ({@link #framed()} or {@link #streaming()}),
 * optionally configure the envelope (checksum, type verification, wire constraints), then call a terminal factory or a
 * Stream-API method.
 *
 * <p>
 * The wire-format version is implicit: there is one version ({@link Protocol.V1}) and it is intended to stay that way,
 * so callers never spell it. {@code ValueIo} configures a {@link Protocol.V1} envelope under the hood and hands it to
 * the concrete reader/writer. {@code Protocol} remains the versioned envelope contract; {@code ValueIo} is the I/O
 * surface on top of it.
 *
 * <h2>I/O surface</h2>
 *
 * <p>
 * The canonical I/O surface is Foreign Function &amp; Memory (FFM) + NIO:
 *
 * <ul>
 * <li>{@link MemorySegment} (in-memory, off-heap, mmap'd) for bounded binary I/O</li>
 * <li>{@link ReadableByteChannel}/{@link WritableByteChannel} for unbounded streamed I/O</li>
 * <li>{@link Path} for mmap-backed file readers and FileChannel-backed file writers</li>
 * <li>{@link Reader}/{@link Writer} for JSON char-based I/O</li>
 * <li>{@link InputStream}/{@link OutputStream} as thin shims for binary callers that don't speak NIO</li>
 * </ul>
 *
 * <p>
 * Configuration (mode, checksum, type verification, wire constraints) lives on {@code ValueIo} as immutable-builder
 * chain methods; the terminal factory call materialises the typed writer/reader.
 *
 * <p>
 * Single-line usage:
 *
 * <pre>{@code
 * try (var w = ValueIo.framed().binaryWriter(channel)) {
 *   w.write(Value.of("hello"), TypeDescriptor.of(String.class));
 * }
 *
 * try (var r = ValueIo.framed().binaryReaderFromFile(path)) {
 *   Value<String> v = r.read(String.class);
 * }
 * }</pre>
 */
public final class ValueIo {

  /** Default staging buffer size for channel-backed cursors and parsers. */
  public static final int DEFAULT_STAGING_BYTES = 8 * 1024;

  private static final String CHANNEL_NULL = "channel must not be null";
  private static final String PATH_NULL = "path must not be null";

  private final Protocol.V1 protocol;

  private ValueIo(Protocol.V1 protocol) {
    this.protocol = protocol;
  }

  // ============ Mode factories ============

  /** Single-document envelope: header on open, terminator on close, exactly one top-level record in between. */
  public static ValueIo framed() {
    return new ValueIo(Protocol.V1.framed());
  }

  /** Open-ended sequence of records: no header, no terminator, end-of-stream is EOF. */
  public static ValueIo streaming() {
    return new ValueIo(Protocol.V1.streaming());
  }

  // ============ Chain customizers ============

  public ValueIo usingTypeVerification() {
    return new ValueIo(protocol.usingTypeVerification());
  }

  public ValueIo withoutTypeVerification() {
    return new ValueIo(protocol.withoutTypeVerification());
  }

  /**
   * Returns a new {@code ValueIo} with the given wire-level safety bounds. Plain {@link #framed()} and
   * {@link #streaming()} are seeded with {@link WireConstraints#SECURE_DEFAULTS}; call this to override individual
   * fields (via the builder) or to opt out wholesale via {@link WireConstraints#UNCHECKED}.
   */
  public ValueIo withWireConstraints(WireConstraints constraints) {
    return new ValueIo(protocol.withWireConstraints(constraints));
  }

  /** Returns a new {@code ValueIo} with the given checksum algorithm. Not supported in {@link #streaming()} mode. */
  public ValueIo withChecksum(ChecksumAlgorithm algorithm) {
    return new ValueIo(protocol.withChecksum(algorithm));
  }

  // ============ Accessors ============

  /** The envelope mode this configuration carries. */
  public Protocol.Mode mode() {
    return protocol.mode();
  }

  /** The checksum algorithm this configuration uses. */
  public ChecksumAlgorithm checksumAlgorithm() {
    return protocol.checksumAlgorithm();
  }

  /** The wire-level safety bounds applied by readers and writers. */
  public WireConstraints wireConstraints() {
    return protocol.wireConstraints();
  }

  /** Whether each record slot also writes its descriptor name for read-side verification. */
  public boolean typeVerificationEnabled() {
    return protocol.typeVerificationEnabled();
  }

  // ============ Custom-format extension point ============

  /**
   * Plugs in a custom wire format: the factory constructs an {@link AbstractStateWriter} subclass bound to this
   * configuration. The returned writer has {@code start()} already invoked.
   */
  public <W extends AbstractStateWriter> W writer(Protocol.WriterFactory<W> factory) {
    return protocol.writer(factory);
  }

  /** Custom-format reader — symmetric to {@link #writer(Protocol.WriterFactory)}. */
  public <R extends AbstractStateReader> R reader(Protocol.ReaderFactory<R> factory) {
    return protocol.reader(factory);
  }

  // ============ Binary terminal factories ============

  /** Bounded segment (heap, off-heap, or mmap'd) — caller manages lifecycle. */
  public BinaryStateWriter binaryWriter(MemorySegment segment) {
    Objects.requireNonNull(segment, "segment must not be null");
    return protocol.writer(p -> new BinaryStateWriter(p, SegmentCursors.boundedWriter(segment)));
  }

  /**
   * Channel-backed unbounded writer. The writer owns an internal {@code Arena.ofShared()} for staging. Default staging
   * size is {@value #DEFAULT_STAGING_BYTES}.
   */
  public BinaryStateWriter binaryWriter(WritableByteChannel channel) {
    return binaryWriter(channel, DEFAULT_STAGING_BYTES);
  }

  public BinaryStateWriter binaryWriter(WritableByteChannel channel, int stagingBytes) {
    Objects.requireNonNull(channel, CHANNEL_NULL);
    return protocol.writer(p -> new BinaryStateWriter(p, SegmentCursors.stagedWriter(channel, stagingBytes, false)));
  }

  /**
   * Channel-backed unbounded writer with a caller-supplied {@link Arena}. Useful for sharing an arena across multiple
   * writers. Caller closes the arena after the writer.
   */
  public BinaryStateWriter binaryWriter(WritableByteChannel channel, Arena arena, int stagingBytes) {
    Objects.requireNonNull(channel, CHANNEL_NULL);
    Objects.requireNonNull(arena, "arena must not be null");
    return protocol.writer(p ->
      new BinaryStateWriter(p, SegmentCursors.stagedWriter(channel, arena, stagingBytes, false, false))
    );
  }

  /** Convenience: java.io shim over {@link Channels#newChannel(OutputStream)}. */
  public BinaryStateWriter binaryWriter(OutputStream out) {
    Objects.requireNonNull(out, "out must not be null");
    return binaryWriter(Channels.newChannel(out));
  }

  /** Bounded segment (heap, off-heap, or mmap'd) — caller manages lifecycle. */
  public BinaryStateReader binaryReader(MemorySegment segment) {
    Objects.requireNonNull(segment, "segment must not be null");
    return protocol.reader(p -> new BinaryStateReader(p, SegmentCursors.bounded(segment)));
  }

  /**
   * Convenience for in-memory byte arrays — wraps {@code data} as a {@link MemorySegment} via
   * {@link MemorySegment#ofArray(byte[])} and reads from it directly. Avoids the {@link Channels#newChannel(InputStream)}
   * transfer-buffer allocation per {@code .read()} that the {@link #binaryReader(InputStream)} convenience pays when
   * callers already hold the bytes in heap memory.
   */
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

  /**
   * File-backed writer. Opens the file via {@link FileChannel#open} for writing (CREATE + TRUNCATE_EXISTING), wraps it
   * as a {@link WritableByteChannel} with caller-owned staging.
   */
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

  /**
   * Mmap-backed file reader. Opens the file via {@link FileChannel#open(Path, java.nio.file.OpenOption...)} READ-ONLY,
   * mmaps it, and feeds the resulting {@link MemorySegment} to the reader. The reader owns the mapped segment and the
   * file channel; both are released on close.
   */
  public BinaryStateReader binaryReaderFromFile(Path path) {
    Objects.requireNonNull(path, PATH_NULL);
    return protocol.reader(p -> new BinaryStateReader(p, SegmentCursors.mmapReadOnly(path)));
  }

  public SizingStateWriter sizingWriter() {
    return protocol.writer(SizingStateWriter::new);
  }

  // ============ JSON terminal factories ============

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

  // ============ Stream API — symmetric overloads across formats ============

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
