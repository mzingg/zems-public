package dev.zems.lib.value.marshal.format.binary;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ReadableByteChannel;

/**
 * Read cursor that pulls bytes from a {@link ReadableByteChannel} into an internal staging {@link MemorySegment}
 * allocated from an {@link Arena}. The staging segment refills on underflow; large reads (larger than the staging size)
 * are streamed straight from the channel into the destination.
 */
final class StagedReadCursor implements SegmentReadCursor {

  private final ReadableByteChannel channel;
  private final Arena arena;
  private final MemorySegment staging;
  private final ByteBuffer view;
  private final boolean ownsArena;
  private final boolean ownsChannel;
  private long totalConsumed;
  private boolean eof;

  StagedReadCursor(ReadableByteChannel channel, Arena arena, int stagingBytes, boolean ownsArena, boolean ownsChannel) {
    if (stagingBytes < 64) {
      throw new IllegalArgumentException("stagingBytes must be at least 64, got " + stagingBytes);
    }
    this.channel = channel;
    this.arena = arena;
    this.ownsArena = ownsArena;
    this.ownsChannel = ownsChannel;
    this.staging = arena.allocate(stagingBytes);
    this.view = staging.asByteBuffer().order(ByteOrder.BIG_ENDIAN);
    this.view.position(0).limit(0); // start empty in read mode
  }

  @Override
  public byte get() {
    ensureAvailable(1);
    byte v = view.get();
    totalConsumed += 1;
    return v;
  }

  @Override
  public void get(byte[] dst, int off, int len) {
    if (len == 0) {
      return;
    }
    int available = view.remaining();
    if (available >= len) {
      view.get(dst, off, len);
      totalConsumed += len;
      return;
    }
    if (available > 0) {
      view.get(dst, off, available);
      off += available;
      len -= available;
      totalConsumed += available;
    }
    // Stream the remainder straight from the channel into the destination — bypasses the
    // staging buffer for payloads larger than its capacity.
    var dstWrap = ByteBuffer.wrap(dst, off, len);
    while (dstWrap.hasRemaining()) {
      int r;
      try {
        r = channel.read(dstWrap);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      if (r < 0) {
        eof = true;
        throw new IllegalStateException(
          "EOF: bulk read needed " +
            len +
            " more bytes, channel exhausted at total position " +
            (totalConsumed + (dstWrap.position() - off))
        );
      }
    }
    totalConsumed += len;
  }

  @Override
  public short getShort() {
    ensureAvailable(Short.BYTES);
    short v = view.getShort();
    totalConsumed += Short.BYTES;
    return v;
  }

  @Override
  public int getInt() {
    ensureAvailable(Integer.BYTES);
    int v = view.getInt();
    totalConsumed += Integer.BYTES;
    return v;
  }

  @Override
  public long getLong() {
    ensureAvailable(Long.BYTES);
    long v = view.getLong();
    totalConsumed += Long.BYTES;
    return v;
  }

  @Override
  public float getFloat() {
    ensureAvailable(Float.BYTES);
    float v = view.getFloat();
    totalConsumed += Float.BYTES;
    return v;
  }

  @Override
  public double getDouble() {
    ensureAvailable(Double.BYTES);
    double v = view.getDouble();
    totalConsumed += Double.BYTES;
    return v;
  }

  @Override
  public boolean hasRemaining() {
    if (view.hasRemaining()) {
      return true;
    }
    if (eof) {
      return false;
    }
    refill();
    return view.hasRemaining();
  }

  @Override
  public byte peek() {
    if (!view.hasRemaining()) {
      if (eof) {
        throw new IllegalStateException("Cannot peek past EOF at position " + totalConsumed);
      }
      refill();
      if (!view.hasRemaining()) {
        throw new IllegalStateException("Cannot peek past EOF at position " + totalConsumed);
      }
    }
    return view.get(view.position());
  }

  @Override
  public long position() {
    return totalConsumed;
  }

  @Override
  public void skip(int n) {
    if (n < 0) {
      throw new IllegalArgumentException("skip n must be non-negative, got " + n);
    }
    if (n == 0) {
      return;
    }
    int remaining = n;
    do {
      int take = Math.min(view.remaining(), remaining);
      if (take > 0) {
        view.position(view.position() + take);
        totalConsumed += take;
        remaining -= take;
      }
      if (remaining > 0) {
        if (eof) {
          throw new IllegalStateException(
            "EOF during skip: needed " + n + " bytes at position " + (totalConsumed - (n - remaining))
          );
        }
        refill();
      }
    } while (remaining > 0);
  }

  @Override
  @SuppressWarnings("PMD.UseTryWithResources")
  // channel/arena are conditionally owned; a flat try/finally expresses ordered clean-up most cleanly
  public void close() {
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

  private void ensureAvailable(int n) {
    if (n > staging.byteSize()) {
      throw new IllegalArgumentException("Primitive read of " + n + " exceeds staging size " + staging.byteSize());
    }
    while (view.remaining() < n) {
      if (eof) {
        throw new IllegalStateException(
          "EOF: needed " + n + " bytes at position " + totalConsumed + " but only " + view.remaining() + " available"
        );
      }
      refill();
    }
  }

  private void refill() {
    view.compact(); // → write mode; unread bytes preserved at start
    try {
      int r = channel.read(view);
      if (r < 0) {
        eof = true;
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      view.flip(); // → read mode
    }
  }
}
