package dev.zems.lib.value.marshal;

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

/**
 * Logical-stream digest helper used by {@link AbstractStateWriter} and {@link AbstractStateReader} during marshalling.
 * Always non-null — when the protocol's checksum algorithm is {@link ChecksumAlgorithm#NONE} the instance is a no-op
 * (every {@code feed} returns immediately and {@link #hex()} returns the empty string), so callers never need a null
 * check.
 *
 * <p>
 * The digest covers the LOGICAL stream — slot names plus value bytes — so it is deterministic across wire formats.
 * Header and terminator slots do <em>not</em> contribute (the terminator carries the checksum itself; feeding it would
 * be circular). Toggle suppression with {@code suspend()} / {@code resume()}.
 *
 * <p>
 * The feed sequence here MUST stay in a lock-step with the matching reader-side feed sequence, so the writer's digest
 * equals what the reader recomputes. Any change must update both.
 *
 * <p>
 * Primitive feeds use a per-instance {@code scratch} byte buffer rather than allocating a fresh {@code ByteBuffer} per
 * call. The digest semantics are unchanged — big-endian byte ordering matches
 * {@code ByteBuffer.allocate(...).putXxx(value).array()}.
 */
final class ChecksumComputation {

  private final boolean enabled;
  private final MessageDigest digest;
  /**
   * Scratch slab reused by feedSlotId / feedShort / feedInt / feedLong / feedFloat / feedDouble / feedChar /
   * feedBoolean / feedStateTag. 8 bytes cover the widest primitive; varint slot ids reuse the first 5.
   */
  private final byte[] scratch = new byte[8];
  private boolean suspended;
  /**
   * Reusable UTF-8 encoder + chunk buffer for feedString/feedDescriptorName/feedThrowable. Lazy because
   * {@link ChecksumComputation} is constructed per writer/reader (millions of times during prep), but only ever uses
   * the encoder when {@code enabled} is true AND a string is actually fed. Eager construction added ~47.5 GB of
   * {@code HeapByteBuffer} allocations across the suite (snapshot-20260516-185810) — see ensureEncoder().
   */
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

  /** Returns the lower-case hex digest, or {@code ""} when this is a no-op instance. */
  String hex() {
    return enabled ? HexFormat.of().formatHex(digest.digest()) : "";
  }

  /** Suspend digest feed (used while writing/reading the header or terminator). */
  void suspend() {
    suspended = true;
  }

  /** Resume the digest feed (called after the header/terminator slot is done). */
  void resume() {
    suspended = false;
  }

  // ============ Primitives ============

  /**
   * Feeds the unsigned-varint encoding of a slot id. Slot ids are the cross-format identity for a slot (binary writes
   * ids on the wire; JSON ignores them). Feeding the id rather than the name keeps the digest cross-format consistent.
   */
  void feedSlotId(int id) {
    if (id < 0) {
      throw new IllegalArgumentException("slot id must be non-negative, got " + id);
    }
    // 7-bit unsigned varint (LSB-first, MSB=continuation). Up to 5 bytes for an int, written
    // into the shared scratch slab.
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

  // ============ State markers ============

  void feedState(ValueState state) {
    scratch[0] = (byte) (state.ordinal() + 1);
    feed(scratch, 1);
  }

  /** Convenience for the four discrete state-marker tags (NULL=1, UNDEFINED=2, UNRESOLVED=3, ERROR=4). */
  void feedStateTag(int tagOneToFour) {
    scratch[0] = (byte) tagOneToFour;
    feed(scratch, 1);
  }

  // ============ Composite records ============

  void feedDescriptorName(String descriptorName) {
    feedUtf8(descriptorName);
  }

  // ============ Errors ============

  void feedThrowable(Throwable throwable) {
    // A SerializedThrowable wraps the original wire-stored class as a separate field; its own
    // getClass() returns the wrapper. Use originalClassName() so reader/writer feeds stay
    // symmetric across a round-trip.
    String className = throwable instanceof SerializedThrowable st
      ? st.originalClassName()
      : throwable.getClass().getName();
    feedUtf8(className);
    if (throwable.getMessage() != null) {
      feedUtf8(throwable.getMessage());
    }
  }

  // ============ Internal ============

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

  /**
   * Encodes {@code value} as UTF-8 and feeds the bytes into the digest in 1 KiB chunks, reusing a single
   * {@link ByteBuffer}. Produces byte-identical input to {@code value.getBytes(UTF_8)} (verified by
   * {@code ChecksumComputationTest}). Empty strings feed zero bytes — the wrapping length prefix in the wire envelope
   * still uniquely identifies the slot.
   */
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
        // UTF-8 cannot produce malformed-input / unmappable for any valid Java String —
        // surface defensively if it ever did.
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

  /**
   * Lazy-allocates the UTF-8 encoder + 1 KiB chunk buffer on first use. Per-instance lifetime; subsequent feedUtf8
   * calls reuse them. REPLACE-on-malformed mirrors {@code String.getBytes(UTF_8)} so unpaired-surrogate inputs produce
   * the same digest as the previous getBytes-based path.
   */
  private void ensureEncoder() {
    if (utf8Encoder == null) {
      utf8Encoder = StandardCharsets.UTF_8.newEncoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE);
      encodeBuffer = ByteBuffer.allocate(1024);
    }
  }
}
