package dev.zems.lib.value.valueio.format.binary;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/** Write cursor over one pre-sized {@link MemorySegment}; throws on overflow. */
final class BoundedWriteCursor implements SegmentWriteCursor {

  private final MemorySegment segment;
  private final long size;
  private long pos;

  BoundedWriteCursor(MemorySegment segment) {
    this.segment = segment;
    this.size = segment.byteSize();
  }

  @Override
  public void put(byte b) {
    requireRemaining(1);
    segment.set(ValueLayout.JAVA_BYTE, pos, b);
    pos += 1;
  }

  @Override
  public void put(byte[] src, int off, int len) {
    requireRemaining(len);
    MemorySegment.copy(src, off, segment, ValueLayout.JAVA_BYTE, pos, len);
    pos += len;
  }

  @Override
  public void putShort(short v) {
    requireRemaining(Short.BYTES);
    segment.set(BinaryLayouts.I16, pos, v);
    pos += Short.BYTES;
  }

  @Override
  public void putInt(int v) {
    requireRemaining(Integer.BYTES);
    segment.set(BinaryLayouts.I32, pos, v);
    pos += Integer.BYTES;
  }

  @Override
  public void putLong(long v) {
    requireRemaining(Long.BYTES);
    segment.set(BinaryLayouts.I64, pos, v);
    pos += Long.BYTES;
  }

  @Override
  public void putFloat(float v) {
    requireRemaining(Float.BYTES);
    segment.set(BinaryLayouts.F32, pos, v);
    pos += Float.BYTES;
  }

  @Override
  public void putDouble(double v) {
    requireRemaining(Double.BYTES);
    segment.set(BinaryLayouts.F64, pos, v);
    pos += Double.BYTES;
  }

  @Override
  public void flush() {}

  @Override
  public long position() {
    return pos;
  }

  @Override
  public void close() {}

  private void requireRemaining(int n) {
    if (pos + n > size) {
      throw new IllegalStateException(
        "Overflow: requested " + n + " bytes at position " + pos + " (size=" + size + ")"
      );
    }
  }
}
