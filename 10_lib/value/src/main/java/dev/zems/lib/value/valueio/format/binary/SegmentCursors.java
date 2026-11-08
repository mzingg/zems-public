package dev.zems.lib.value.valueio.format.binary;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Factories for {@link SegmentReadCursor} and {@link SegmentWriteCursor}. */
public final class SegmentCursors {

  private SegmentCursors() {}

  /** Read cursor over {@code segment}; the caller keeps ownership of the segment's arena. */
  public static SegmentReadCursor bounded(MemorySegment segment) {
    return new BoundedReadCursor(segment);
  }

  /**
   * Channel-backed read cursor with its own staging arena, released on close; the channel is closed on cursor close
   * only when {@code ownsChannel} is set.
   */
  public static SegmentReadCursor stagedReader(ReadableByteChannel channel, int stagingBytes, boolean ownsChannel) {
    return new StagedReadCursor(channel, Arena.ofShared(), stagingBytes, true, ownsChannel);
  }

  /**
   * Channel-backed read cursor staging through {@code arena}; the arena and the channel are closed on cursor close
   * only when the matching {@code owns*} flag is set.
   */
  public static SegmentReadCursor stagedReader(
    ReadableByteChannel channel,
    Arena arena,
    int stagingBytes,
    boolean ownsArena,
    boolean ownsChannel
  ) {
    return new StagedReadCursor(channel, arena, stagingBytes, ownsArena, ownsChannel);
  }

  /**
   * Read cursor over the whole of a regular file, memory-mapped read-only. The cursor owns the file channel and the
   * mapping's arena and releases both on close; an empty file yields an empty heap-backed cursor.
   */
  @SuppressWarnings("PMD.UseTryWithResources") // the cursor's onClose releases the channel and the arena
  public static SegmentReadCursor mmapReadOnly(Path path) {
    FileChannel ch = null;
    Arena arena = null;
    try {
      ch = FileChannel.open(path, StandardOpenOption.READ);
      long size = ch.size();
      if (size == 0L) {
        // FileChannel.map rejects a zero-byte region on some platforms (Windows), so an empty file never reaches mmap.
        ch.close();
        ch = null;
        return new BoundedReadCursor(MemorySegment.ofArray(new byte[0]));
      }
      arena = Arena.ofShared();
      MemorySegment segment = ch.map(FileChannel.MapMode.READ_ONLY, 0, size, arena);
      final FileChannel finalCh = ch;
      final Arena finalArena = arena;
      return new BoundedReadCursor(segment, () -> {
        try {
          finalCh.close();
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        } finally {
          finalArena.close();
        }
      });
    } catch (IOException e) {
      if (arena != null) {
        try {
          arena.close();
        } catch (RuntimeException ignored) {}
      }
      if (ch != null) {
        try {
          ch.close();
        } catch (IOException ignored) {}
      }
      throw new UncheckedIOException(e);
    }
  }

  /** Write cursor over {@code segment}; the caller keeps ownership of the segment's arena. */
  public static SegmentWriteCursor boundedWriter(MemorySegment segment) {
    return new BoundedWriteCursor(segment);
  }

  public static SegmentWriteCursor stagedWriter(WritableByteChannel channel, int stagingBytes, boolean ownsChannel) {
    return new StagedWriteCursor(channel, Arena.ofShared(), stagingBytes, true, ownsChannel);
  }

  public static SegmentWriteCursor stagedWriter(
    WritableByteChannel channel,
    Arena arena,
    int stagingBytes,
    boolean ownsArena,
    boolean ownsChannel
  ) {
    return new StagedWriteCursor(channel, arena, stagingBytes, ownsArena, ownsChannel);
  }
}
