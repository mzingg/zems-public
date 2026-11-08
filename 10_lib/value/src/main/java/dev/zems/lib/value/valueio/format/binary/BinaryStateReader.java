package dev.zems.lib.value.valueio.format.binary;

import dev.zems.lib.value.ValueState;
import dev.zems.lib.value.valueio.AbstractStateReader;
import dev.zems.lib.value.valueio.Protocol;
import dev.zems.lib.value.valueio.SerializedThrowable;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import dev.zems.lib.value.valueio.wire.WireConstraintEnforcer;
import dev.zems.lib.value.valueio.wire.WireConstraints;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * CBOR (RFC 8949) implementation of {@link dev.zems.lib.value.valueio.StateReader StateReader}. Reads the canonical
 * CBOR that {@link BinaryStateWriter} emits and also tolerates non-canonical input (indefinite-length items, float64
 * that was not down-shifted) so that peers like Jackson CBOR interoperate. Constructed only through {@code ValueIo}.
 */
public final class BinaryStateReader extends AbstractStateReader {

  private static final Set<Protocol.Mode> SUPPORTED_MODES = Set.of(Protocol.Mode.FRAMED, Protocol.Mode.STREAMING);
  private static final String QUOTE_COLON = "': ";

  private final SegmentReadCursor cursor;
  private final CborReader cborReader;
  private final Deque<MapFrame> stack = new ArrayDeque<>();

  private boolean slotPeeked;
  private long peekedKey;
  private boolean pendingErrorMap;
  private boolean pendingUnsupported;
  private String pendingVerifyString;

  public BinaryStateReader(Protocol protocol, SegmentReadCursor cursor) {
    super(protocol, SUPPORTED_MODES);
    this.cursor = Objects.requireNonNull(cursor, "cursor must not be null");
    this.cborReader = new CborReader(cursor, protocol.wireConstraints().maxStringLength());
    // The self-describe tag is optional on read: external producers (Jackson, fxamacker) usually omit it, and the
    // header that follows has the same shape either way.
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

  @Override
  protected void doReadRecordOpen(int id, String name, TypeDescriptor<?> descriptor) {
    consumeKey(id, name);
    String verifyString = pendingVerifyString;
    pendingVerifyString = null;
    if (verifyString == null) {
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
      // A wire written with type verification on stays readable with it off: the verify string is dropped unchecked.
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
    // A CBOR Sequence (RFC 8742) has no separator between top-level items.
  }

  @Override
  protected List<String> doDrainUnknownSlotNames() {
    var frame = stack.peek();
    if (frame == null) {
      return List.of();
    }
    List<String> names = new ArrayList<>();
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

  @Override
  protected void doClose() {
    cursor.close();
  }

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
  protected String doReadUnsupported(int id, String name) {
    if (!pendingUnsupported) {
      throw new IllegalStateException(
        "readUnsupported called without preceding peekValueStateOrNull returning UNSUPPORTED at '" + name + "'"
      );
    }
    pendingUnsupported = false;
    return cborReader.readText();
  }

  @Override
  protected ValueState doPeekValueStateOrNull(int id, String name) {
    peekKey(id, name);
    CborMajorType valueType = cborReader.peekMajorType();
    if (valueType == CborMajorType.TAG) {
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
      } else if (tag == CborTag.STATE_UNSUPPORTED) {
        pendingUnsupported = true;
        consumeKeyAck();
        return ValueState.UNSUPPORTED;
      } else if (tag == CborTag.TYPE_VERIFY) {
        long arrLen = cborReader.readArrayHeader();
        if (arrLen != 2L) {
          throw new CborProtocolException("type-verify tag must wrap a 2-element array, got length " + arrLen);
        }
        pendingVerifyString = cborReader.readText();
        return null;
      } else {
        throw new CborProtocolException(
          "unknown CBOR tag " +
            tag +
            " at '" +
            name +
            "': expected a state-marker tag (" +
            CborTag.STATE_NULL +
            "-" +
            CborTag.STATE_UNSUPPORTED +
            ") or the type-verify tag (" +
            CborTag.TYPE_VERIFY +
            ")"
        );
      }
    }
    return null;
  }

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

  @Override
  protected byte[] doReadBytes(int id, String name) {
    consumeKey(id, name);
    return cborReader.readBytes();
  }

  @Override
  protected String doReadString(int id, String name) {
    consumeKey(id, name);
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

  private void drainAndPopFrame(String name) {
    var frame = stack.pop();
    if (slotPeeked) {
      cborReader.skipItem();
      slotPeeked = false;
      decrementEntriesRemaining(frame);
    }
    while (hasMoreEntries(frame)) {
      cborReader.readInt64();
      cborReader.skipItem();
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

  private void consumeKey(int expectedId, String name) {
    var top = stack.peek();
    if (top == null) {
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

  private void consumeKeyAck() {
    var top = stack.peek();
    if (top == null) {
      slotPeeked = false;
      return;
    }
    slotPeeked = false;
    decrementEntriesRemaining(top);
  }

  private static final class MapFrame {

    long entriesRemaining;

    MapFrame(long entriesRemaining) {
      this.entriesRemaining = entriesRemaining;
    }
  }
}
