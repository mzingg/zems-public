package dev.zems.lib.value.marshal.format.binary;

/**
 * The write-only byte-sink surface {@link CborWriter} needs. Implemented by {@link SegmentWriteCursor} (for direct
 * channel / segment output) and by the in-memory frame buffer inside {@link BinaryStateWriter}. Kept narrow so the
 * encoder doesn't reach for cursor lifecycle concerns (close / flush / position).
 *
 * <p>
 * All multi-byte writes are big-endian (CBOR network byte order).
 */
interface CborByteOutput {
  void put(byte b);

  void put(byte[] src, int off, int len);

  void putShort(short v);

  void putInt(int v);

  void putLong(long v);

  void putFloat(float v);

  void putDouble(double v);
}
