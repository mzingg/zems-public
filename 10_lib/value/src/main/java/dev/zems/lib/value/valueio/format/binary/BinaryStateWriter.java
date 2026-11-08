package dev.zems.lib.value.valueio.format.binary;

import dev.zems.lib.value.valueio.AbstractStateWriter;
import dev.zems.lib.value.valueio.Protocol;
import dev.zems.lib.value.valueio.SerializedThrowable;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Set;

/**
 * CBOR (RFC 8949) implementation of {@link dev.zems.lib.value.valueio.StateWriter StateWriter}. Emits canonical,
 * definite-length CBOR; constructed only through the {@code binary*} factories on {@code ValueIo}.
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
      directWriter.writeTag(CborTag.SELF_DESCRIBE);
    }
  }

  /**
   * Exact number of wire bytes written to the cursor so far, including the framed-mode header and terminator once
   * {@link #close()} has returned. Unlike {@link #byteCount()}, which counts logical chars and primitives, this is the
   * value to slice a pre-allocated destination segment by.
   */
  public long cursorPosition() {
    return cursor.position();
  }

  @Override
  protected void doWriteRecordOpen(int id, String name, TypeDescriptor<?> descriptor) {
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

  @Override
  protected void doClose() {
    cursor.close();
  }

  @Override
  protected void doWriteRecordSeparator() {
    // A CBOR Sequence (RFC 8742) has no separator between top-level items.
  }

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
    w.writeText(SerializedThrowable.wireClassName(throwable));
    w.writeUnsignedInt(1);
    w.writeText(throwable.getMessage() == null ? "" : throwable.getMessage());
  }

  @Override
  protected void doWriteUnsupported(int id, String name, String reason) {
    var w = beginEntry(id);
    w.writeTag(CborTag.STATE_UNSUPPORTED);
    w.writeText(reason);
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

  @Override
  protected void doEndNested(int id, String name) {
    closeMapFrame();
  }

  @Override
  protected void doBeginNested(int id, String name) {
    emitKeyInParent(id);
    pushFrame();
  }

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

  private void closeMapFrame() {
    var frame = stack.pop();
    byte[] body = frame.out.toByteArray();
    CborWriter target = currentWriter();
    target.writeMapHeader(frame.entryCount);
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

  private void emitStateMarker(int id, long tag) {
    var w = beginEntry(id);
    w.writeTag(tag);
    w.writeNull();
  }

  private CborWriter beginEntry(int id) {
    if (stack.isEmpty()) {
      return directWriter;
    }
    var top = stack.peek();
    top.writer.writeUnsignedInt(id);
    top.entryCount++;
    return top.writer;
  }

  private static final class RecordFrame {

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CborWriter writer;
    long entryCount;

    RecordFrame() {
      this.writer = new CborWriter(new BaosOutput(out));
    }
  }

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
