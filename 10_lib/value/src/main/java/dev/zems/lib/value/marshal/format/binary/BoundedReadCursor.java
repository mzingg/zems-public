package dev.zems.lib.value.marshal.format.binary;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Read cursor over a single bounded {@link MemorySegment}. Suitable for in-memory, off-heap, and mmap'd-file backing.
 * Throws on under-read.
 */
final class BoundedReadCursor implements SegmentReadCursor {

  private final MemorySegment segment;
  private final long size;
  private final Runnable onClose;
  private long pos;
  private boolean closed;

  BoundedReadCursor(MemorySegment segment) {
    this(segment, null);
  }

  BoundedReadCursor(MemorySegment segment, Runnable onClose) {
    this.segment = segment;
    this.size = segment.byteSize();
    this.onClose = onClose;
  }

  long offset() {
    return pos;
  }

  void seek(long newPos) {
    if (newPos < 0 || newPos > size) {
      throw new IllegalStateException("Seek out of bounds: " + newPos + " (size=" + size + ")");
    }
    pos = newPos;
  }

  @Override
  public byte get() {
    requireRemaining(1);
    byte v = segment.get(ValueLayout.JAVA_BYTE, pos);
    pos += 1;
    return v;
  }

  @Override
  public void get(byte[] dst, int off, int len) {
    requireRemaining(len);
    MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, pos, dst, off, len);
    pos += len;
  }

  @Override
  public short getShort() {
    requireRemaining(Short.BYTES);
    short v = segment.get(BinaryLayouts.I16, pos);
    pos += Short.BYTES;
    return v;
  }

  @Override
  public int getInt() {
    requireRemaining(Integer.BYTES);
    int v = segment.get(BinaryLayouts.I32, pos);
    pos += Integer.BYTES;
    return v;
  }

  @Override
  public long getLong() {
    requireRemaining(Long.BYTES);
    long v = segment.get(BinaryLayouts.I64, pos);
    pos += Long.BYTES;
    return v;
  }

  @Override
  public float getFloat() {
    requireRemaining(Float.BYTES);
    float v = segment.get(BinaryLayouts.F32, pos);
    pos += Float.BYTES;
    return v;
  }

  @Override
  public double getDouble() {
    requireRemaining(Double.BYTES);
    double v = segment.get(BinaryLayouts.F64, pos);
    pos += Double.BYTES;
    return v;
  }

  @Override
  public boolean hasRemaining() {
    return pos < size;
  }

  @Override
  public byte peek() {
    requireRemaining(1);
    return segment.get(ValueLayout.JAVA_BYTE, pos);
  }

  @Override
  public long position() {
    return pos;
  }

  @Override
  public void skip(int n) {
    if (n < 0) {
      throw new IllegalArgumentException("skip n must be non-negative, got " + n);
    }
    requireRemaining(n);
    pos += n;
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    if (onClose != null) {
      onClose.run();
    }
  }

  private void requireRemaining(int n) {
    if (pos + n > size) {
      throw new IllegalStateException(
        "Underflow: requested " + n + " bytes at position " + pos + " (size=" + size + ")"
      );
    }
  }
}
