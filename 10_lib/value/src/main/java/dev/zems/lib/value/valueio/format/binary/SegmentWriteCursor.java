package dev.zems.lib.value.valueio.format.binary;

/**
 * Write-side cursor over a byte sink: a bounded {@link java.lang.foreign.MemorySegment MemorySegment} or a
 * {@link java.nio.channels.WritableByteChannel WritableByteChannel} fed through a staging segment. Multi-byte writes
 * are big-endian. Cursors are thread-confined.
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

  /** Drains any staging buffer to the sink; a no-op for bounded cursors. */
  void flush();

  /** Bytes written since construction. */
  long position();

  @Override
  void close();
}
