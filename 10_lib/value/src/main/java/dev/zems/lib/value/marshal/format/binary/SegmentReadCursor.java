package dev.zems.lib.value.marshal.format.binary;

/**
 * Read-side cursor over a byte source — abstracts over a single bounded
 * {@link java.lang.foreign.MemorySegment MemorySegment} (in-memory, off-heap, or mmap'd file) and a
 * {@link java.nio.channels.ReadableByteChannel ReadableByteChannel} backed by an internal staging segment that refills
 * on underflow.
 *
 * <p>
 * All multibyte reads are big-endian to match the on-wire format.
 *
 * <p>
 * Cursors are thread-confined; concurrent invocations have undefined behaviour.
 */
public sealed interface SegmentReadCursor extends AutoCloseable permits BoundedReadCursor, StagedReadCursor {
  byte get();

  void get(byte[] dst, int off, int len);

  short getShort();

  int getInt();

  long getLong();

  float getFloat();

  double getDouble();

  /** Returns true iff at least one more byte is available without blocking-on-EOF. */
  boolean hasRemaining();

  /** Non-destructive lookahead at the next byte. {@link #hasRemaining()} must be true. */
  byte peek();

  /** Total bytes consumed since construction. */
  long position();

  /**
   * Advances the cursor by {@code n} bytes without retaining them. Used to skip unknown slot payloads when the slot's
   * tag indicates a fixed or varint-prefixed payload size.
   */
  void skip(int n);

  @Override
  void close();
}
