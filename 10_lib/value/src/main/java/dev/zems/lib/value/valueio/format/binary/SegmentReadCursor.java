package dev.zems.lib.value.valueio.format.binary;

/**
 * Read-side cursor over a byte source: a bounded {@link java.lang.foreign.MemorySegment MemorySegment} or a
 * {@link java.nio.channels.ReadableByteChannel ReadableByteChannel} refilled through a staging segment. Multi-byte
 * reads are big-endian. Cursors are thread-confined.
 */
public sealed interface SegmentReadCursor extends AutoCloseable permits BoundedReadCursor, StagedReadCursor {
  byte get();

  void get(byte[] dst, int off, int len);

  short getShort();

  int getInt();

  long getLong();

  float getFloat();

  double getDouble();

  boolean hasRemaining();

  /** Next byte without consuming it; {@link #hasRemaining()} must be true. */
  byte peek();

  /** Bytes consumed since construction. */
  long position();

  void skip(int n);

  @Override
  void close();
}
