package dev.zems.lib.value.marshal.format.binary;

import dev.zems.lib.value.marshal.AbstractStateWriter;
import dev.zems.lib.value.marshal.Protocol;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

/**
 * A {@link dev.zems.lib.value.marshal.StateWriter StateWriter} that counts CBOR wire bytes without writing to any
 * buffer. Each method adds the exact byte count that {@link BinaryStateWriter} would write for the same call.
 *
 * <p>
 * Mirrors {@link BinaryStateWriter}'s framing: every record / nested scope is a definite-length CBOR map keyed by
 * integer slot id; type-verification wraps the map as {@code tag(49157) array(2) [verifyString, recordMap]}. State
 * markers ride on private-use CBOR tags. The per-frame counter tracks both encoded body size and entry count, so the
 * closing {@code map(N)} header can be sized from the entry count.
 *
 * <p>
 * Reusable via {@link #reset()}. Constructed via {@code ValueIo.sizingWriter()} or via {@link #sizing()}.
 */
public final class SizingStateWriter extends AbstractStateWriter {

  private static final Set<Protocol.Mode> SUPPORTED_MODES = Set.of(Protocol.Mode.FRAMED, Protocol.Mode.STREAMING);
  private final Deque<Frame> frames = new ArrayDeque<>();
  private int size;

  public SizingStateWriter(Protocol protocol) {
    super(protocol, SUPPORTED_MODES);
    if (protocol.mode() == Protocol.Mode.FRAMED) {
      // BinaryStateWriter emits the self-describe tag (0xD9 0xD9 0xF7, 3 bytes) at construction
      // time in FRAMED mode. The sizing writer mirrors that overhead so capacity-planning
      // callers don't undershoot.
      size += 3;
    }
  }

  /** Factory for use with {@link Protocol#writer(Protocol.WriterFactory)}. */
  public static Protocol.WriterFactory<SizingStateWriter> sizing() {
    return SizingStateWriter::new;
  }

  // ============ CBOR encoding-size helpers ============

  /** Bytes consumed by a CBOR unsigned-int argument (including the initial byte). */
  static int sizeOfUint(long v) {
    if (v < 0) {
      throw new IllegalArgumentException("uint must be non-negative, got " + v);
    }
    if (v <= 23L) {
      return 1;
    }
    if (v <= 0xFFL) {
      return 2;
    }
    if (v <= 0xFFFFL) {
      return 3;
    }
    if (v <= 0xFFFFFFFFL) {
      return 5;
    }
    return 9;
  }

  /** Bytes consumed by a canonical-encoded float64 (5 if round-trippable via float32, else 9). */
  static int sizeOfFloat64Canonical(double v) {
    if (Double.isNaN(v)) {
      return 9;
    }
    float f = (float) v;
    return ((double) f == v) ? 5 : 9;
  }

  /** Bytes consumed by a CBOR signed int (major type 0 or 1, plus argument). */
  static int sizeOfInt64(long v) {
    long arg = v < 0 ? -1L - v : v;
    return sizeOfUint(arg);
  }

  private static int utf8ByteLength(String value) {
    int length = value.length();
    for (int i = 0; i < length; i++) {
      if (value.charAt(i) >= 0x80) {
        return value.getBytes(StandardCharsets.UTF_8).length;
      }
    }
    return length;
  }

  public int size() {
    return size;
  }

  public void reset() {
    size = 0;
    frames.clear();
  }

  // ============ Composite records ============

  @Override
  protected void doWriteRecordOpen(int id, String name, TypeDescriptor<?> descriptor) {
    // Emit slot id key in parent (no entry count update; that's tracked separately for the
    // frame whose value position we now occupy).
    if (!frames.isEmpty()) {
      var parent = frames.peek();
      parent.bodySize += sizeOfUint(id);
      parent.entries++;
    }
    if (!isInEnvelope() && protocol instanceof Protocol.V1 v1 && v1.typeVerificationEnabled()) {
      // tag(TYPE_VERIFY) + array(2) + verifyString
      String verify = descriptor.descriptorName() + "@" + descriptor.signature();
      int verifyBytes = utf8ByteLength(verify);
      int wrap = sizeOfUint(CborTag.TYPE_VERIFY) + 1 /* array(2) inline */ + sizeOfUint(verifyBytes) + verifyBytes;
      if (frames.isEmpty()) {
        size += wrap;
      } else {
        frames.peek().bodySize += wrap;
      }
    }
    frames.push(new Frame());
  }

  @Override
  protected void doWriteRecordClose(int id, String name, TypeDescriptor<?> descriptor) {
    commitFrame();
  }

  @Override
  protected void doWriteRecordSeparator() {
    // CBOR Sequence — no separator byte.
  }

  // ============ State markers ============

  @Override
  protected void doWriteTombstone(int id, String name) {
    addStateMarker(id, CborTag.STATE_TOMBSTONE);
  }

  @Override
  protected void doWriteError(int id, String name, Throwable throwable) {
    beginEntry(id);
    String className = throwable.getClass().getName();
    String message = throwable.getMessage() == null ? "" : throwable.getMessage();
    int classBytes = utf8ByteLength(className);
    int messageBytes = utf8ByteLength(message);
    // tag(STATE_ERROR) + map(2) { 0: class, 1: message }
    int errorSize = sizeOfUint(CborTag.STATE_ERROR);
    errorSize += 1; // map(2) initial byte (entries=2 inline)
    errorSize += sizeOfUint(0) + sizeOfUint(classBytes) + classBytes;
    errorSize += sizeOfUint(1) + sizeOfUint(messageBytes) + messageBytes;
    addBytes(errorSize);
  }

  @Override
  protected void doWriteUnresolved(int id, String name) {
    addStateMarker(id, CborTag.STATE_UNRESOLVED);
  }

  @Override
  protected void doWriteUndefined(int id, String name) {
    addStateMarker(id, CborTag.STATE_UNDEFINED);
  }

  @Override
  protected void doWriteNull(int id, String name) {
    addStateMarker(id, CborTag.STATE_NULL);
  }

  // ============ Structure ============

  @Override
  protected void doEndNested(int id, String name) {
    commitFrame();
  }

  @Override
  protected void doBeginNested(int id, String name) {
    if (!frames.isEmpty()) {
      var parent = frames.peek();
      parent.bodySize += sizeOfUint(id);
      parent.entries++;
    }
    frames.push(new Frame());
  }

  // ============ Primitives ============

  @Override
  protected void doWriteBytes(int id, String name, byte[] value) {
    beginEntry(id);
    addBytes(sizeOfUint(value.length) + value.length);
  }

  @Override
  protected void doWriteString(int id, String name, String value) {
    beginEntry(id);
    int utf8 = utf8ByteLength(value);
    addBytes(sizeOfUint(utf8) + utf8);
  }

  @Override
  protected void doWriteDouble(int id, String name, double value) {
    beginEntry(id);
    addBytes(sizeOfFloat64Canonical(value));
  }

  @Override
  protected void doWriteFloat(int id, String name, float value) {
    beginEntry(id);
    addBytes(5); // initial byte + 4 bytes float32
  }

  @Override
  protected void doWriteLong(int id, String name, long value) {
    beginEntry(id);
    addBytes(sizeOfInt64(value));
  }

  @Override
  protected void doWriteInt(int id, String name, int value) {
    beginEntry(id);
    addBytes(sizeOfInt64(value));
  }

  @Override
  protected void doWriteShort(int id, String name, short value) {
    beginEntry(id);
    addBytes(sizeOfInt64(value));
  }

  @Override
  protected void doWriteChar(int id, String name, char value) {
    beginEntry(id);
    addBytes(sizeOfUint(value));
  }

  @Override
  protected void doWriteBoolean(int id, String name, boolean value) {
    beginEntry(id);
    addBytes(1); // bool simple value initial byte
  }

  // ============ Internal ============

  /** Closes the top frame and contributes its full encoded form ({@code map(N) + body}) to the parent. */
  private void commitFrame() {
    Frame frame = frames.pop();
    int total = sizeOfUint(frame.entries) + frame.bodySize;
    if (frames.isEmpty()) {
      size += total;
    } else {
      frames.peek().bodySize += total;
    }
  }

  private void addStateMarker(int id, long tag) {
    beginEntry(id);
    addBytes(sizeOfUint(tag) + 1 /* null */);
  }

  /**
   * Accounts for a slot's key (parent-side) and increments the parent's entry counter. For top-level writes (no frame
   * open), this is a no-op — there's no enclosing map.
   */
  private void beginEntry(int id) {
    var parent = frames.peek();
    if (parent == null) {
      return;
    }
    parent.bodySize += sizeOfUint(id);
    parent.entries++;
  }

  /** Adds {@code n} bytes to the active accumulator (frame body or total). */
  private void addBytes(int n) {
    var top = frames.peek();
    if (top == null) {
      size += n;
    } else {
      top.bodySize += n;
    }
  }

  /** Tracks the encoded body size and entry count of an open map. */
  private static final class Frame {

    int bodySize;
    long entries;
  }
}
