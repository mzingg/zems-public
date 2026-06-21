package dev.zems.lib.value.marshal.format.binary;

import dev.zems.lib.value.ValueState;
import dev.zems.lib.value.marshal.AbstractStateReader;
import dev.zems.lib.value.marshal.Protocol;
import dev.zems.lib.value.marshal.SerializedThrowable;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import dev.zems.lib.value.marshal.wire.WireConstraintEnforcer;
import dev.zems.lib.value.marshal.wire.WireConstraints;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Binary wire-format implementation of {@link dev.zems.lib.value.marshal.StateReader StateReader} over CBOR (RFC 8949).
 * Reads canonical CBOR produced by {@link BinaryStateWriter} and also tolerates non-canonical input (indefinite-length
 * items, float64 not down-shifted) so that peers like Jackson CBOR can interoperate.
 *
 * <p>
 * Each record / nested scope is read as a CBOR map keyed by integer slot id; entries are consumed in wire order. Type
 * verification — when on — reads {@code tag(49157) array(2) [verifyString, recordMap]} and matches {@code verifyString}
 * against {@code descriptor.descriptorName()} (plus aliases). State markers ride on private-use tags 49152–49156.
 *
 * <p>
 * Constructed only through {@code ValueIo} terminal factories.
 */
public final class BinaryStateReader extends AbstractStateReader {

  private static final Set<Protocol.Mode> SUPPORTED_MODES = Set.of(Protocol.Mode.FRAMED, Protocol.Mode.STREAMING);
  private static final String QUOTE_COLON = "': ";

  private final SegmentReadCursor cursor;
  private final CborReader cborReader;
  private final Deque<MapFrame> stack = new ArrayDeque<>();

  // One-slot lookahead — `slotPeeked` true iff the next entry's key has been read into
  // `peekedKey` but the value has not yet been consumed.
  private boolean slotPeeked;
  private long peekedKey;

  // Set by doPeekValueStateOrNull when STATE_ERROR was detected; readError consumes the error-map next.
  private boolean pendingErrorMap;

  // Set by doPeekValueStateOrNull when TYPE_VERIFY tag wrapped the upcoming record; doReadRecordOpen
  // uses this verification string instead of expecting the wrapping in the wire stream.
  private String pendingVerifyString;

  public BinaryStateReader(Protocol protocol, SegmentReadCursor cursor) {
    super(protocol, SUPPORTED_MODES);
    this.cursor = Objects.requireNonNull(cursor, "cursor must not be null");
    this.cborReader = new CborReader(cursor, protocol.wireConstraints().maxStringLength());
    // In FRAMED mode our writer always emits the self-describe tag (RFC 8949 §3.4.6) up
    // front. Be lenient: consume the tag if present, but don't require it — external CBOR
    // producers (Jackson, fxamacker, …) usually don't emit it, and the header that follows
    // is the same shape regardless.
    if (
      protocol.mode() == Protocol.Mode.FRAMED &&
      cursor.hasRemaining() &&
      cborReader.peekMajorType() == CborMajorType.TAG
    ) {
      long tag = cborReader.readTag();
      if (tag != CborTag.SELF_DESCRIBE) {
        throw new CborProtocolException("expected self-describe tag (55799) at FRAMED stream start, got tag " + tag);
      }
    }
  }

  private static void verifyDescriptor(String name, TypeDescriptor<?> descriptor, String verifyString) {
    int at = verifyString.indexOf('@');
    String wireName = at < 0 ? verifyString : verifyString.substring(0, at);
    if (!wireName.equals(descriptor.descriptorName()) && !descriptor.nameAliases().contains(wireName)) {
      throw new IllegalStateException(
        "Type mismatch at '" +
          name +
          "': expected " +
          descriptor.descriptorName() +
          (descriptor.nameAliases().isEmpty() ? "" : " (or aliases " + descriptor.nameAliases() + ")") +
          " but stream contained " +
          wireName
      );
    }
  }

  private static void decrementEntriesRemaining(MapFrame frame) {
    if (frame.entriesRemaining != CborReader.INDEFINITE_LENGTH) {
      frame.entriesRemaining--;
    }
  }

  // ============ Composite records ============

  @Override
  protected void doReadRecordOpen(int id, String name, TypeDescriptor<?> descriptor) {
    consumeKey(id, name);
    String verifyString = pendingVerifyString;
    pendingVerifyString = null;
    if (verifyString == null) {
      // No prior peek — handle the tag-wrap inline if present.
      if (cborReader.peekMajorType() == CborMajorType.TAG) {
        long tag = cborReader.readTag();
        if (tag != CborTag.TYPE_VERIFY) {
          throw new CborProtocolException("unexpected tag " + tag + " at record '" + name + "'");
        }
        long arrLen = cborReader.readArrayHeader();
        if (arrLen != 2L) {
          throw new CborProtocolException("type-verify tag must wrap a 2-element array, got length " + arrLen);
        }
        verifyString = cborReader.readText();
      }
    }
    if (verifyString != null && !isInEnvelope() && protocol instanceof Protocol.V1 v1 && v1.typeVerificationEnabled()) {
      verifyDescriptor(name, descriptor, verifyString);
    } else if (verifyString != null) {
      // We consumed a tag wrap but type-verification isn't expected. Just drop the string —
      // the wire was over-verified, which is harmless.
    }
    long entries = cborReader.readMapHeader();
    WireConstraints constraints = protocol.wireConstraints();
    if (entries != CborReader.INDEFINITE_LENGTH) {
      WireConstraintEnforcer.checkMapEntries(Math.toIntExact(entries), constraints);
    }
    WireConstraintEnforcer.checkDepth(stack.size() + 1, constraints);
    stack.push(new MapFrame(entries));
    String wireSignature = "";
    if (verifyString != null) {
      int at = verifyString.indexOf('@');
      wireSignature = at < 0 ? "" : verifyString.substring(at + 1);
    }
    pushRecordSignature(wireSignature);
  }

  @Override
  protected void doReadRecordClose(int id, String name, TypeDescriptor<?> descriptor) {
    drainAndPopFrame(name);
    popRecordSignature();
  }

  @Override
  protected boolean doHasMoreRecords() {
    return cursor.hasRemaining();
  }

  @Override
  protected void doConsumeRecordSeparator() {
    // CBOR Sequence — top-level data items concatenate without a separator. Nothing to consume.
  }

  // ============ Unknown-slot drain ============

  @Override
  protected List<String> doDrainUnknownSlotNames() {
    var frame = stack.peek();
    if (frame == null) {
      return List.of();
    }
    List<String> names = new ArrayList<>();
    // A key may already be peeked; treat it as unknown and skip its value.
    if (slotPeeked) {
      cborReader.skipItem();
      names.add("id=" + peekedKey);
      slotPeeked = false;
      decrementEntriesRemaining(frame);
    }
    while (hasMoreEntries(frame)) {
      long key = cborReader.readInt64();
      cborReader.skipItem();
      names.add("id=" + key);
      decrementEntriesRemaining(frame);
    }
    return names;
  }

  // ============ Lifecycle ============

  @Override
  protected void doClose() {
    cursor.close();
  }

  // ============ State markers ============

  @Override
  protected Throwable doReadError(int id, String name) {
    if (!pendingErrorMap) {
      throw new IllegalStateException(
        "readError called without preceding peekValueStateOrNull returning ERROR at '" + name + "'"
      );
    }
    pendingErrorMap = false;
    long entries = cborReader.readMapHeader();
    if (entries != 2L && entries != CborReader.INDEFINITE_LENGTH) {
      throw new CborProtocolException("error-map must have 2 entries, got " + entries);
    }
    String errorClass = null;
    String errorMessage = null;
    long readEntries = 0;
    while (entries == CborReader.INDEFINITE_LENGTH ? !cborReader.peekIsBreak() : readEntries < entries) {
      long key = cborReader.readUnsignedInt();
      String text = cborReader.readText();
      if (key == 0L) {
        errorClass = text;
      } else if (key == 1L) {
        errorMessage = text;
      }
      readEntries++;
    }
    if (entries == CborReader.INDEFINITE_LENGTH) {
      cborReader.readBreak();
    }
    if (errorClass == null) {
      throw new CborProtocolException("error-map missing class (key 0)");
    }
    return new SerializedThrowable(errorClass, errorMessage == null ? "" : errorMessage);
  }

  @Override
  protected ValueState doPeekValueStateOrNull(int id, String name) {
    peekKey(id, name);
    CborMajorType valueType = cborReader.peekMajorType();
    if (valueType == CborMajorType.TAG) {
      // Consume the tag; dispatch on its value. State-marker tags consume their payload now;
      // TYPE_VERIFY consumes the wrapping and saves the verify string for doReadRecordOpen.
      long tag = cborReader.readTag();
      if (tag == CborTag.STATE_NULL) {
        cborReader.readNull();
        consumeKeyAck();
        return ValueState.NULL;
      } else if (tag == CborTag.STATE_UNDEFINED) {
        cborReader.readNull();
        consumeKeyAck();
        return ValueState.UNDEFINED;
      } else if (tag == CborTag.STATE_UNRESOLVED) {
        cborReader.readNull();
        consumeKeyAck();
        return ValueState.UNRESOLVED;
      } else if (tag == CborTag.STATE_TOMBSTONE) {
        cborReader.readNull();
        consumeKeyAck();
        return ValueState.TOMBSTONE;
      } else if (tag == CborTag.STATE_ERROR) {
        pendingErrorMap = true;
        consumeKeyAck();
        return ValueState.ERROR;
      } else if (tag == CborTag.TYPE_VERIFY) {
        long arrLen = cborReader.readArrayHeader();
        if (arrLen != 2L) {
          throw new CborProtocolException("type-verify tag must wrap a 2-element array, got length " + arrLen);
        }
        pendingVerifyString = cborReader.readText();
        // Value is the upcoming map; do not ack the key — doReadRecordOpen will handle it.
        return null;
      } else {
        throw new CborProtocolException("unknown CBOR tag at '" + name + "': " + tag);
      }
    }
    // No tag — the value is a primitive or a record (map). Defer; the next read consumes it.
    return null;
  }

  // ============ Structure (nested scopes) ============

  @Override
  protected void doEndNested(int id, String name) {
    drainAndPopFrame(name);
  }

  @Override
  protected void doBeginNested(int id, String name) {
    consumeKey(id, name);
    long entries = cborReader.readMapHeader();
    WireConstraints constraints = protocol.wireConstraints();
    if (entries != CborReader.INDEFINITE_LENGTH) {
      WireConstraintEnforcer.checkMapEntries(Math.toIntExact(entries), constraints);
    }
    WireConstraintEnforcer.checkDepth(stack.size() + 1, constraints);
    stack.push(new MapFrame(entries));
  }

  // ============ Primitives ============

  @Override
  protected byte[] doReadBytes(int id, String name) {
    consumeKey(id, name);
    // CborReader bounds the wire-declared length (in bytes) before allocating; no post-check needed.
    return cborReader.readBytes();
  }

  @Override
  protected String doReadString(int id, String name) {
    consumeKey(id, name);
    // Length is bounded in wire bytes (UTF-8) before allocating, matching the byte-string path.
    return cborReader.readText();
  }

  @Override
  protected double doReadDouble(int id, String name) {
    consumeKey(id, name);
    double v = cborReader.readFloatAny();
    WireConstraintEnforcer.checkFinite(v, protocol.wireConstraints());
    return v;
  }

  @Override
  protected float doReadFloat(int id, String name) {
    consumeKey(id, name);
    float v = cborReader.readFloat32();
    WireConstraintEnforcer.checkFinite(v, protocol.wireConstraints());
    return v;
  }

  @Override
  protected long doReadLong(int id, String name) {
    consumeKey(id, name);
    return cborReader.readInt64();
  }

  @Override
  protected int doReadInt(int id, String name) {
    consumeKey(id, name);
    long v = cborReader.readInt64();
    if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
      throw new CborProtocolException("int value out of range at '" + name + QUOTE_COLON + v);
    }
    return (int) v;
  }

  @Override
  protected short doReadShort(int id, String name) {
    consumeKey(id, name);
    long v = cborReader.readInt64();
    if (v < Short.MIN_VALUE || v > Short.MAX_VALUE) {
      throw new CborProtocolException("short value out of range at '" + name + QUOTE_COLON + v);
    }
    return (short) v;
  }

  @Override
  protected char doReadChar(int id, String name) {
    consumeKey(id, name);
    long v = cborReader.readUnsignedInt();
    if (v < 0 || v > 0xFFFFL) {
      throw new CborProtocolException("char value out of range at '" + name + QUOTE_COLON + v);
    }
    return (char) v;
  }

  @Override
  protected boolean doReadBoolean(int id, String name) {
    consumeKey(id, name);
    return cborReader.readBool();
  }

  // ============ Field presence ============

  @Override
  protected boolean doHasField(int id, String name) {
    if (slotPeeked) {
      return id == peekedKey;
    }
    var top = stack.peek();
    if (top == null) {
      return cursor.hasRemaining();
    }
    if (top.entriesRemaining == 0L) {
      return false;
    }
    if (top.entriesRemaining == CborReader.INDEFINITE_LENGTH && cborReader.peekIsBreak()) {
      return false;
    }
    try {
      peekedKey = cborReader.readInt64();
      slotPeeked = true;
    } catch (Exception e) {
      return false;
    }
    return id == peekedKey;
  }

  // ============ Internal: frame + key plumbing ============

  /**
   * Drains any unread entries from the top frame (skipping their values), consumes the break stop-code for an
   * indefinite frame, and pops the frame off the stack.
   */
  private void drainAndPopFrame(String name) {
    var frame = stack.pop();
    if (slotPeeked) {
      // We peeked a key but never consumed the value — skip the value to keep the wire in sync.
      cborReader.skipItem();
      slotPeeked = false;
      decrementEntriesRemaining(frame);
    }
    while (hasMoreEntries(frame)) {
      cborReader.readInt64(); // key
      cborReader.skipItem(); // value
      decrementEntriesRemaining(frame);
    }
    if (frame.entriesRemaining == CborReader.INDEFINITE_LENGTH) {
      cborReader.readBreak();
    }
  }

  private boolean hasMoreEntries(MapFrame frame) {
    if (frame.entriesRemaining == CborReader.INDEFINITE_LENGTH) {
      return !cborReader.peekIsBreak();
    }
    return frame.entriesRemaining > 0;
  }

  /**
   * Reads the next entry's key from the current frame (or uses the peeked one), verifies it matches {@code expectedId},
   * and decrements the frame's entries counter. For top-level reads (no frame on stack) the key step is skipped — the
   * upcoming CBOR data item IS the value.
   */
  private void consumeKey(int expectedId, String name) {
    var top = stack.peek();
    if (top == null) {
      // Top-level read: there's no enclosing map, so there's no key to consume.
      return;
    }
    long key;
    if (slotPeeked) {
      key = peekedKey;
      slotPeeked = false;
    } else {
      key = cborReader.readInt64();
    }
    decrementEntriesRemaining(top);
    if (key != expectedId) {
      throw new IllegalStateException("Expected slot id " + expectedId + " (" + name + ") but found id " + key);
    }
  }

  /**
   * Peeks the next entry's key into {@link #slotPeeked}/{@link #peekedKey} without decrementing the frame counter; used
   * by {@link #doPeekValueStateOrNull} so the actual value-read step (or the ack via {@link #consumeKeyAck()}) can
   * consume the slot.
   */
  private void peekKey(int expectedId, String name) {
    var top = stack.peek();
    if (top == null) {
      return;
    }
    if (!slotPeeked) {
      peekedKey = cborReader.readInt64();
      slotPeeked = true;
    }
    if (peekedKey != expectedId) {
      throw new IllegalStateException("Expected slot id " + expectedId + " (" + name + ") but found id " + peekedKey);
    }
  }

  /**
   * Acknowledges that a peeked key has been "spent" by a state-marker read (which consumes the value via
   * {@link CborReader} directly, not through {@link #consumeKey}). Clears the peek flag and decrements the current
   * frame's entry counter.
   */
  private void consumeKeyAck() {
    var top = stack.peek();
    if (top == null) {
      slotPeeked = false;
      return;
    }
    slotPeeked = false;
    decrementEntriesRemaining(top);
  }

  /** Tracks an open CBOR map's remaining-entry count. {@code -1} = indefinite-length. */
  private static final class MapFrame {

    long entriesRemaining;

    MapFrame(long entriesRemaining) {
      this.entriesRemaining = entriesRemaining;
    }
  }
}
