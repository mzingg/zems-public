package dev.zems.lib.value.valueio.format.binary;

/** Write-only byte sink for {@link CborWriter}; multi-byte writes are big-endian. */
interface CborByteOutput {
  void put(byte b);

  void put(byte[] src, int off, int len);

  void putShort(short v);

  void putInt(int v);

  void putLong(long v);

  void putFloat(float v);

  void putDouble(double v);
}
