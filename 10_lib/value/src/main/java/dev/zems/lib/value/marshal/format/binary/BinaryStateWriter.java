package dev.zems.lib.value.marshal.format.binary;

import dev.zems.lib.value.marshal.AbstractStateWriter;
import dev.zems.lib.value.marshal.Protocol;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Set;

/**
 * Binary wire-format implementation of {@link dev.zems.lib.value.marshal.StateWriter StateWriter} over CBOR (RFC 8949).
 * The format is intentionally hidden behind the public {@code binary*} factories on {@code ValueIo}; callers see
 * "binary" — they get canonical CBOR.
 *
 * <p>
 * <b>Wire shape (summary).</b> Every record is a definite-length CBOR map keyed by integer slot
 * id. Top-level writes are bare CBOR data items (no enclosing key). State markers ride on private-use CBOR tags
 * wrapping {@code null} (or, for {@code ERROR}, a 2-entry map). When {@link Protocol.V1#typeVerificationEnabled()} is
 * enabled, the record is wrapped as {@code tag(49157) array(2) [verifyString, recordMap]} where {@code verifyString} is
 * {@code "descriptorName@signature"}.
 *
 * <p>
 * <b>Frame buffering.</b> CBOR maps need their entry count before the bytes — but the count
 * isn't known until {@code endRecord}. Each {@link RecordFrame} captures encoded entries in a heap buffer plus an entry
 * counter; on close, the map header is emitted and the bytes replayed to the parent frame (or to the underlying cursor
 * at top level). This composes with the channel-staging {@link StagedWriteCursor} — two layers, distinct concerns.
 *
 * <p>
 * Constructed only through {@code ValueIo} terminal factories.
 */
public final class BinaryStateWriter extends AbstractStateWriter {

  private static final Set<Protocol.Mode> SUPPORTED_MODES = Set.of(Protocol.Mode.FRAMED, Protocol.Mode.STREAMING);

  private final SegmentWriteCursor cursor;
  private final CborWriter directWriter;
  private final Deque<RecordFrame> stack = new ArrayDeque<>();

  public BinaryStateWriter(Protocol protocol, SegmentWriteCursor cursor) {
    super(protocol, SUPPORTED_MODES);
    this.cursor = Objects.requireNonNull(cursor, "cursor must not be null");
    this.directWriter = new CborWriter(cursor);
    if (protocol.mode() == Protocol.Mode.FRAMED) {
      // RFC 8949 §3.4.6 self-describe tag — three bytes 0xD9 0xD9 0xF7. Acts as the
      // "this is CBOR" magic so external tools can sniff the format. STREAMING omits the
      // prefix; concatenated body records are still a valid CBOR Sequence (RFC 8742).
      directWriter.writeTag(CborTag.SELF_DESCRIBE);
    }
  }

  /**
   * Total bytes written to the underlying cursor so far. Unlike {@link #byteCount()} on the abstract base — which
   * tracks a logical char/primitive count and undercounts multi-byte UTF-8 strings + CBOR variable-length integers —
   * this returns the exact wire byte count.
   *
   * <p>
   * After {@link #close()} returns, this equals the number of bytes the writer placed in the cursor's destination
   * (including framed-mode header + record + terminator). Callers who pre-allocated a destination segment by size
   * should compare against this value rather than {@link #byteCount()} when slicing the written region.
   */
  public long cursorPosition() {
    return cursor.position();
  }

  // ============ Composite records ============

  @Override
  protected void doWriteRecordOpen(int id, String name, TypeDescriptor<?> descriptor) {
    // Emit slot key in parent (if any). When type-verification is on, the record map is wrapped
    // in tag(49157) + array(2) carrying the verify string; otherwise we emit the bare map.
    emitKeyInParent(id);
    if (!isInEnvelope() && protocol instanceof Protocol.V1 v1 && v1.typeVerificationEnabled()) {
      var parent = currentWriter();
      parent.writeTag(CborTag.TYPE_VERIFY);
      parent.writeArrayHeader(2);
      parent.writeText(descriptor.descriptorName() + "@" + descriptor.signature());
    }
    pushFrame();
  }

  @Override
  protected void doWriteRecordClose(int id, String name, TypeDescriptor<?> descriptor) {
    closeMapFrame();
  }

  // ============ Lifecycle ============

  @Override
  protected void doClose() {
    cursor.close();
  }

  @Override
  protected void doWriteRecordSeparator() {
    // CBOR Sequence (RFC 8742): top-level records are bare CBOR data items concatenated. No
    // separator byte.
  }

  // ============ State markers ============

  @Override
  protected void doWriteTombstone(int id, String name) {
    emitStateMarker(id, CborTag.STATE_TOMBSTONE);
  }

  @Override
  protected void doWriteError(int id, String name, Throwable throwable) {
    var w = beginEntry(id);
    w.writeTag(CborTag.STATE_ERROR);
    w.writeMapHeader(2);
    w.writeUnsignedInt(0);
    w.writeText(throwable.getClass().getName());
    w.writeUnsignedInt(1);
    w.writeText(throwable.getMessage() == null ? "" : throwable.getMessage());
  }

  @Override
  protected void doWriteUnresolved(int id, String name) {
    emitStateMarker(id, CborTag.STATE_UNRESOLVED);
  }

  @Override
  protected void doWriteUndefined(int id, String name) {
    emitStateMarker(id, CborTag.STATE_UNDEFINED);
  }

  @Override
  protected void doWriteNull(int id, String name) {
    emitStateMarker(id, CborTag.STATE_NULL);
  }

  // ============ Structure (nested scopes) ============

  @Override
  protected void doEndNested(int id, String name) {
    closeMapFrame();
  }

  @Override
  protected void doBeginNested(int id, String name) {
    emitKeyInParent(id);
    pushFrame();
  }

  // ============ Primitives ============

  @Override
  protected void doWriteBytes(int id, String name, byte[] value) {
    var w = beginEntry(id);
    w.writeBytes(value);
  }

  @Override
  protected void doWriteString(int id, String name, String value) {
    var w = beginEntry(id);
    w.writeText(value);
  }

  @Override
  protected void doWriteDouble(int id, String name, double value) {
    var w = beginEntry(id);
    w.writeFloat64Canonical(value);
  }

  @Override
  protected void doWriteFloat(int id, String name, float value) {
    var w = beginEntry(id);
    w.writeFloat32(value);
  }

  @Override
  protected void doWriteLong(int id, String name, long value) {
    var w = beginEntry(id);
    w.writeInt64(value);
  }

  @Override
  protected void doWriteInt(int id, String name, int value) {
    var w = beginEntry(id);
    w.writeInt64(value);
  }

  @Override
  protected void doWriteShort(int id, String name, short value) {
    var w = beginEntry(id);
    w.writeInt64(value);
  }

  @Override
  protected void doWriteChar(int id, String name, char value) {
    var w = beginEntry(id);
    w.writeUnsignedInt(value);
  }

  @Override
  protected void doWriteBoolean(int id, String name, boolean value) {
    var w = beginEntry(id);
    w.writeBool(value);
  }

  // ============ Frame management ============

  /** Pops the top frame, emits {@code map(entryCount)} into the next destination, and replays the body bytes. */
  private void closeMapFrame() {
    var frame = stack.pop();
    byte[] body = frame.out.toByteArray();
    CborWriter target = currentWriter();
    target.writeMapHeader(frame.entryCount);
    // Replay the buffered body bytes through the destination writer's cursor. For the direct
    // writer we use the underlying cursor; for a nested frame we go through its CborWriter's
    // cursor too — both expose put(byte[],int,int) via SegmentWriteCursor / BaosCursor.
    writeRawBytes(target, body);
  }

  private void writeRawBytes(CborWriter target, byte[] body) {
    if (target == directWriter) {
      cursor.put(body, 0, body.length);
    } else {
      var parent = stack.peek();
      parent.out.write(body, 0, body.length);
    }
  }

  /** Emits a slot-id CBOR int key into the parent frame (or no-op if we're at the top level). */
  private void emitKeyInParent(int id) {
    if (stack.isEmpty()) {
      return;
    }
    var parent = stack.peek();
    parent.writer.writeUnsignedInt(id);
    parent.entryCount++;
  }

  private CborWriter currentWriter() {
    var top = stack.peek();
    return top == null ? directWriter : top.writer;
  }

  private void pushFrame() {
    stack.push(new RecordFrame());
  }

  /** Writes a state marker as {@code tag(N) null} into the appropriate destination. */
  private void emitStateMarker(int id, long tag) {
    var w = beginEntry(id);
    w.writeTag(tag);
    w.writeNull();
  }

  /**
   * Reserves a map entry in the current frame for an incoming value. If a frame is open, writes the slot key and
   * increments the entry count, returning the frame's writer so the caller can append the value. If no frame is open
   * (top-level write), returns the direct writer; no key is emitted.
   */
  private CborWriter beginEntry(int id) {
    if (stack.isEmpty()) {
      return directWriter;
    }
    var top = stack.peek();
    top.writer.writeUnsignedInt(id);
    top.entryCount++;
    return top.writer;
  }

  /** In-memory accumulator for a CBOR map body. Active map closes by emitting {@code map(entryCount) + bytes}. */
  private static final class RecordFrame {

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CborWriter writer;
    long entryCount;

    RecordFrame() {
      this.writer = new CborWriter(new BaosOutput(out));
    }
  }

  /**
   * In-memory {@link CborByteOutput} backed by a {@link ByteArrayOutputStream}. Used inside {@link RecordFrame} so the
   * same {@link CborWriter} works against in-memory frames and channel-backed cursors uniformly.
   */
  private static final class BaosOutput implements CborByteOutput {

    private final ByteArrayOutputStream out;

    BaosOutput(ByteArrayOutputStream out) {
      this.out = out;
    }

    @Override
    public void put(byte b) {
      out.write(b & 0xFF);
    }

    @Override
    public void put(byte[] src, int off, int len) {
      out.write(src, off, len);
    }

    @Override
    public void putShort(short v) {
      out.write((v >>> 8) & 0xFF);
      out.write(v & 0xFF);
    }

    @Override
    public void putInt(int v) {
      out.write((v >>> 24) & 0xFF);
      out.write((v >>> 16) & 0xFF);
      out.write((v >>> 8) & 0xFF);
      out.write(v & 0xFF);
    }

    @Override
    public void putLong(long v) {
      out.write((int) ((v >>> 56) & 0xFF));
      out.write((int) ((v >>> 48) & 0xFF));
      out.write((int) ((v >>> 40) & 0xFF));
      out.write((int) ((v >>> 32) & 0xFF));
      out.write((int) ((v >>> 24) & 0xFF));
      out.write((int) ((v >>> 16) & 0xFF));
      out.write((int) ((v >>> 8) & 0xFF));
      out.write((int) (v & 0xFF));
    }

    @Override
    public void putFloat(float v) {
      putInt(Float.floatToRawIntBits(v));
    }

    @Override
    public void putDouble(double v) {
      putLong(Double.doubleToRawLongBits(v));
    }
  }
}
