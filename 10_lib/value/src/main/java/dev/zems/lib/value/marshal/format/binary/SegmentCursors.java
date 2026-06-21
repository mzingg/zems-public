package dev.zems.lib.value.marshal.format.binary;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Factory entry point for {@link SegmentReadCursor} / {@link SegmentWriteCursor} construction. Lives in the binary
 * format package alongside the cursor implementations and is the public surface used by {@code ValueIo} (which
 * lives in a different package).
 */
public final class SegmentCursors {

  private SegmentCursors() {}

  // ============ Read cursors ============

  /** Bounded read cursor over a single segment. Caller manages the segment's arena lifecycle. */
  public static SegmentReadCursor bounded(MemorySegment segment) {
    return new BoundedReadCursor(segment);
  }

  /**
   * Channel-backed staged read cursor. The cursor allocates an internal {@link Arena#ofShared()} for its staging
   * buffer and closes it on {@link SegmentReadCursor#close()}. Whether the channel itself is closed on cursor close is
   * governed by {@code ownsChannel}.
   */
  public static SegmentReadCursor stagedReader(ReadableByteChannel channel, int stagingBytes, boolean ownsChannel) {
    return new StagedReadCursor(channel, Arena.ofShared(), stagingBytes, true, ownsChannel);
  }

  /**
   * Channel-backed staged read cursor with a caller-supplied arena. Caller closes the arena (after the cursor is
   * closed). Whether the channel is closed on cursor close is governed by {@code ownsChannel}.
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
   * Mmap-backed read cursor over a regular file. Opens the file READ-ONLY, mmaps the entire file region into a
   * {@link MemorySegment} bound to a {@link Arena#ofShared() shared arena}, and returns a bounded cursor. The cursor
   * owns the arena and the file channel; both are released on close.
   *
   * <p>
   * Zero-length files are handled specially: {@link FileChannel#map} of a zero-byte region throws on some platforms
   * (notably Windows), so the empty case short-circuits to a heap-backed empty {@link MemorySegment} and the channel is
   * closed before returning.
   */
  @SuppressWarnings("PMD.UseTryWithResources")
  // resources are intentionally retained on success and only released on failure or via the cursor's onClose
  public static SegmentReadCursor mmapReadOnly(Path path) {
    FileChannel ch = null;
    Arena arena = null;
    try {
      ch = FileChannel.open(path, StandardOpenOption.READ);
      long size = ch.size();
      if (size == 0L) {
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
        } catch (RuntimeException suppressed) {
          // ignore — re-throw the original IOException below
        }
      }
      if (ch != null) {
        try {
          ch.close();
        } catch (IOException suppressed) {
          // ignore — re-throw the original IOException below
        }
      }
      throw new UncheckedIOException(e);
    }
  }

  // ============ Write cursors ============

  /** Bounded write cursor over a writable segment. Caller manages the segment's arena lifecycle. */
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
