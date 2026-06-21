package dev.zems.lib.value.marshal.format.binary;

/**
 * Write-side cursor over a byte sink — abstracts over a single bounded
 * {@link java.lang.foreign.MemorySegment MemorySegment} and a
 * {@link java.nio.channels.WritableByteChannel WritableByteChannel} backed by an internal staging segment that flushes
 * on overflow.
 *
 * <p>
 * All multi-byte writes are big-endian to match the on-wire format.
 *
 * <p>
 * Cursors are thread-confined; concurrent invocations have undefined behaviour.
 */
public sealed interface SegmentWriteCursor
  extends CborByteOutput, AutoCloseable
  permits BoundedWriteCursor, StagedWriteCursor
{
  @Override
  void put(byte b);

  @Override
  void put(byte[] src, int off, int len);

  @Override
  void putShort(short v);

  @Override
  void putInt(int v);

  @Override
  void putLong(long v);

  @Override
  void putFloat(float v);

  @Override
  void putDouble(double v);

  /** Drains any internal staging buffer to the underlying sink. No-op for bounded cursors. */
  void flush();

  /** Total bytes written since construction. */
  long position();

  @Override
  void close();
}
