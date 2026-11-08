package dev.zems.lib.value.valueio.format.binary;

import dev.zems.lib.value.valueio.AbstractStateWriter;
import dev.zems.lib.value.valueio.Protocol;
import dev.zems.lib.value.valueio.SerializedThrowable;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

/**
 * A {@link dev.zems.lib.value.valueio.StateWriter StateWriter} that counts the CBOR bytes {@link BinaryStateWriter}
 * would emit for the same calls, without writing anything. Reusable via {@link #reset()}; constructed through
 * {@code ValueIo.sizingWriter()} or {@link #sizing()}.
 */
public final class SizingStateWriter extends AbstractStateWriter {

  private static final Set<Protocol.Mode> SUPPORTED_MODES = Set.of(Protocol.Mode.FRAMED, Protocol.Mode.STREAMING);

  /** RFC 8949 §3: every data item starts with one initial byte, which also holds any argument up to 23. */
  private static final int INITIAL_BYTE_SIZE = 1;

  /** RFC 8949 §3.3: {@code false}, {@code true} and {@code null} are simple values packed into the initial byte. */
  private static final int SIMPLE_VALUE_SIZE = INITIAL_BYTE_SIZE;

  private static final int TWO_ENTRY_HEADER_SIZE = INITIAL_BYTE_SIZE;
  private static final int FLOAT32_ITEM_SIZE = INITIAL_BYTE_SIZE + Float.BYTES;

  /** RFC 8949 §3.4.6: the self-describe tag encodes as the three bytes {@code D9 D9 F7}. */
  private static final int SELF_DESCRIBE_TAG_SIZE = 3;

  private final Deque<Frame> frames = new ArrayDeque<>();
  private int size;

  public SizingStateWriter(Protocol protocol) {
    super(protocol, SUPPORTED_MODES);
    if (protocol.mode() == Protocol.Mode.FRAMED) {
      size += SELF_DESCRIBE_TAG_SIZE;
    }
  }

  public static Protocol.WriterFactory<SizingStateWriter> sizing() {
    return SizingStateWriter::new;
  }

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

  static int sizeOfFloat64Canonical(double v) {
    if (Double.isNaN(v)) {
      return 9;
    }
    float f = (float) v;
    return (double) f == v ? 5 : 9;
  }

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

  @Override
  protected void doWriteRecordOpen(int id, String name, TypeDescriptor<?> descriptor) {
    if (!frames.isEmpty()) {
      var parent = frames.peek();
      parent.bodySize += sizeOfUint(id);
      parent.entries++;
    }
    if (!isInEnvelope() && protocol instanceof Protocol.V1 v1 && v1.typeVerificationEnabled()) {
      String verify = descriptor.descriptorName() + "@" + descriptor.signature();
      int verifyBytes = utf8ByteLength(verify);
      int wrap = sizeOfUint(CborTag.TYPE_VERIFY) + TWO_ENTRY_HEADER_SIZE + sizeOfUint(verifyBytes) + verifyBytes;
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
    // A CBOR Sequence (RFC 8742) has no separator between top-level items.
  }

  @Override
  protected void doWriteTombstone(int id, String name) {
    addStateMarker(id, CborTag.STATE_TOMBSTONE);
  }

  @Override
  protected void doWriteError(int id, String name, Throwable throwable) {
    beginEntry(id);
    String className = SerializedThrowable.wireClassName(throwable);
    String message = throwable.getMessage() == null ? "" : throwable.getMessage();
    int classBytes = utf8ByteLength(className);
    int messageBytes = utf8ByteLength(message);
    int errorSize = sizeOfUint(CborTag.STATE_ERROR);
    errorSize += TWO_ENTRY_HEADER_SIZE;
    errorSize += sizeOfUint(0) + sizeOfUint(classBytes) + classBytes;
    errorSize += sizeOfUint(1) + sizeOfUint(messageBytes) + messageBytes;
    addBytes(errorSize);
  }

  @Override
  protected void doWriteUnsupported(int id, String name, String reason) {
    beginEntry(id);
    int reasonBytes = utf8ByteLength(reason);
    addBytes(sizeOfUint(CborTag.STATE_UNSUPPORTED) + sizeOfUint(reasonBytes) + reasonBytes);
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
    addBytes(FLOAT32_ITEM_SIZE);
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
    addBytes(SIMPLE_VALUE_SIZE);
  }

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
    addBytes(sizeOfUint(tag) + SIMPLE_VALUE_SIZE);
  }

  private void beginEntry(int id) {
    var parent = frames.peek();
    if (parent == null) {
      return;
    }
    parent.bodySize += sizeOfUint(id);
    parent.entries++;
  }

  private void addBytes(int n) {
    var top = frames.peek();
    if (top == null) {
      size += n;
    } else {
      top.bodySize += n;
    }
  }

  private static final class Frame {

    int bodySize;
    long entries;
  }
}
