package dev.zems.lib.value.marshal.format.binary;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.WritableByteChannel;

/**
 * Write cursor that accumulates bytes in an internal staging {@link MemorySegment} allocated from an {@link Arena} and
 * flushes to a {@link WritableByteChannel} on overflow / on {@link #flush()} / on {@link #close()}. Large writes
 * (larger than staging) are streamed straight from the source array into the channel.
 */
final class StagedWriteCursor implements SegmentWriteCursor {

  private final WritableByteChannel channel;
  private final Arena arena;
  private final MemorySegment staging;
  private final ByteBuffer view;
  private final boolean ownsArena;
  private final boolean ownsChannel;
  private long totalWritten;

  StagedWriteCursor(
    WritableByteChannel channel,
    Arena arena,
    int stagingBytes,
    boolean ownsArena,
    boolean ownsChannel
  ) {
    if (stagingBytes < 64) {
      throw new IllegalArgumentException("stagingBytes must be at least 64, got " + stagingBytes);
    }
    this.channel = channel;
    this.arena = arena;
    this.ownsArena = ownsArena;
    this.ownsChannel = ownsChannel;
    this.staging = arena.allocate(stagingBytes);
    this.view = staging.asByteBuffer().order(ByteOrder.BIG_ENDIAN);
    // view starts in write mode (position=0, limit=capacity).
  }

  @Override
  public void put(byte b) {
    ensureCapacity(1);
    view.put(b);
    totalWritten += 1;
  }

  @Override
  public void put(byte[] src, int off, int len) {
    if (len == 0) {
      return;
    }
    int free = view.remaining();
    if (free >= len) {
      view.put(src, off, len);
      totalWritten += len;
      return;
    }
    // Fill what we can into the staging buffer, drain it, then stream the rest directly.
    if (free > 0) {
      view.put(src, off, free);
      off += free;
      len -= free;
      totalWritten += free;
    }
    drain();
    // For payloads larger than staging, write straight to the channel without copying through
    // the staging buffer.
    var srcWrap = ByteBuffer.wrap(src, off, len);
    while (srcWrap.hasRemaining()) {
      try {
        channel.write(srcWrap);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    totalWritten += len;
  }

  @Override
  public void putShort(short v) {
    ensureCapacity(Short.BYTES);
    view.putShort(v);
    totalWritten += Short.BYTES;
  }

  @Override
  public void putInt(int v) {
    ensureCapacity(Integer.BYTES);
    view.putInt(v);
    totalWritten += Integer.BYTES;
  }

  @Override
  public void putLong(long v) {
    ensureCapacity(Long.BYTES);
    view.putLong(v);
    totalWritten += Long.BYTES;
  }

  @Override
  public void putFloat(float v) {
    ensureCapacity(Float.BYTES);
    view.putFloat(v);
    totalWritten += Float.BYTES;
  }

  @Override
  public void putDouble(double v) {
    ensureCapacity(Double.BYTES);
    view.putDouble(v);
    totalWritten += Double.BYTES;
  }

  @Override
  public void flush() {
    drain();
  }

  @Override
  public long position() {
    return totalWritten;
  }

  @Override
  @SuppressWarnings("PMD.UseTryWithResources")
  // ordered drain/channel/arena cleanup with conditional ownership
  public void close() {
    try {
      drain();
    } finally {
      try {
        if (ownsChannel) {
          try {
            channel.close();
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        }
      } finally {
        if (ownsArena) {
          arena.close();
        }
      }
    }
  }

  private void ensureCapacity(int n) {
    if (n > view.capacity()) {
      throw new IllegalArgumentException("Primitive write of " + n + " exceeds staging size " + view.capacity());
    }
    if (view.remaining() < n) {
      drain();
    }
  }

  private void drain() {
    view.flip(); // → read mode for the channel
    try {
      while (view.hasRemaining()) {
        try {
          channel.write(view);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    } finally {
      view.clear(); // → write mode again, fully empty
    }
  }
}
