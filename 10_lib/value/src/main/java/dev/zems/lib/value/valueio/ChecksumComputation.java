package dev.zems.lib.value.valueio;

import dev.zems.lib.value.ValueState;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Logical-stream digest for the reader and writer bases; a no-op instance when the algorithm is NONE. */
final class ChecksumComputation {

  private final boolean enabled;
  private final MessageDigest digest;
  private final byte[] scratch = new byte[Long.BYTES];
  private boolean suspended;
  // Allocated lazily: most instances never feed a string, and an eager encoder plus buffer per reader or writer
  // dominated allocation in profiling.
  private CharsetEncoder utf8Encoder;
  private ByteBuffer encodeBuffer;

  ChecksumComputation(ChecksumAlgorithm algorithm) {
    this.enabled = algorithm != ChecksumAlgorithm.NONE;
    if (enabled) {
      try {
        this.digest = MessageDigest.getInstance(algorithm.javaName());
      } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException("Algorithm not available: " + algorithm, e);
      }
    } else {
      this.digest = null;
    }
  }

  String hex() {
    return enabled ? HexFormat.of().formatHex(digest.digest()) : "";
  }

  void suspend() {
    suspended = true;
  }

  void resume() {
    suspended = false;
  }

  void feedSlotId(int id) {
    if (id < 0) {
      throw new IllegalArgumentException("slot id must be non-negative, got " + id);
    }
    int v = id;
    int i = 0;
    while ((v & ~0x7F) != 0) {
      scratch[i++] = (byte) ((v & 0x7F) | 0x80);
      v >>>= 7;
    }
    scratch[i++] = (byte) v;
    feed(scratch, i);
  }

  void feedBoolean(boolean value) {
    scratch[0] = (byte) (value ? 1 : 0);
    feed(scratch, 1);
  }

  void feedChar(char value) {
    scratch[0] = (byte) (value >>> 8);
    scratch[1] = (byte) value;
    feed(scratch, 2);
  }

  void feedShort(short value) {
    scratch[0] = (byte) (value >>> 8);
    scratch[1] = (byte) value;
    feed(scratch, 2);
  }

  void feedInt(int value) {
    scratch[0] = (byte) (value >>> 24);
    scratch[1] = (byte) (value >>> 16);
    scratch[2] = (byte) (value >>> 8);
    scratch[3] = (byte) value;
    feed(scratch, 4);
  }

  void feedLong(long value) {
    scratch[0] = (byte) (value >>> 56);
    scratch[1] = (byte) (value >>> 48);
    scratch[2] = (byte) (value >>> 40);
    scratch[3] = (byte) (value >>> 32);
    scratch[4] = (byte) (value >>> 24);
    scratch[5] = (byte) (value >>> 16);
    scratch[6] = (byte) (value >>> 8);
    scratch[7] = (byte) value;
    feed(scratch, 8);
  }

  void feedFloat(float value) {
    feedInt(Float.floatToRawIntBits(value));
  }

  void feedDouble(double value) {
    feedLong(Double.doubleToRawLongBits(value));
  }

  void feedString(String value) {
    feedUtf8(value);
  }

  void feedBytes(byte[] value) {
    feed(value);
  }

  void feedState(ValueState state) {
    scratch[0] = (byte) (state.ordinal() + 1);
    feed(scratch, 1);
  }

  void feedDescriptorName(String descriptorName) {
    feedUtf8(descriptorName);
  }

  void feedThrowable(Throwable throwable) {
    feedUtf8(SerializedThrowable.wireClassName(throwable));
    if (throwable.getMessage() != null) {
      feedUtf8(throwable.getMessage());
    }
  }

  private void feed(byte[] bytes) {
    if (enabled && !suspended) {
      digest.update(bytes);
    }
  }

  private void feed(byte[] bytes, int len) {
    if (enabled && !suspended) {
      digest.update(bytes, 0, len);
    }
  }

  private void feedUtf8(String value) {
    if (!enabled || suspended) {
      return;
    }
    if (value.isEmpty()) {
      return;
    }
    ensureEncoder();
    utf8Encoder.reset();
    CharBuffer input = CharBuffer.wrap(value);
    byte[] backing = encodeBuffer.array();
    while (true) {
      encodeBuffer.clear();
      CoderResult res = utf8Encoder.encode(input, encodeBuffer, true);
      if (encodeBuffer.position() > 0) {
        digest.update(backing, 0, encodeBuffer.position());
      }
      if (res.isUnderflow()) {
        break;
      }
      if (!res.isOverflow()) {
        throw new IllegalStateException("Unexpected UTF-8 encode result: " + res);
      }
    }
    encodeBuffer.clear();
    CoderResult flushRes = utf8Encoder.flush(encodeBuffer);
    if (encodeBuffer.position() > 0) {
      digest.update(backing, 0, encodeBuffer.position());
    }
    if (!flushRes.isUnderflow()) {
      throw new IllegalStateException("Unexpected UTF-8 flush result: " + flushRes);
    }
  }

  private void ensureEncoder() {
    if (utf8Encoder == null) {
      // REPLACE matches String.getBytes(UTF_8), so an unpaired surrogate digests the same as the getBytes route.
      utf8Encoder = StandardCharsets.UTF_8.newEncoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE);
      encodeBuffer = ByteBuffer.allocate(1024);
    }
  }
}
