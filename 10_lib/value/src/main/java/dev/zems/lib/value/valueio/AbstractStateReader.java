package dev.zems.lib.value.valueio;

import dev.zems.lib.value.ValueState;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import dev.zems.lib.value.valueio.descriptor.UnknownSlotPolicy;
import dev.zems.lib.value.valueio.wire.WireConstraints;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Base class for {@link StateReader} implementations. The {@code StateReader} methods are {@code final} and run the
 * envelope concerns (closed check, checksum feed) before delegating to the {@code do<Op>} hooks a wire format
 * implements.
 */
public abstract class AbstractStateReader implements StateReader {

  protected final Protocol protocol;
  private final Set<Protocol.Mode> supportedModes;
  private final ChecksumComputation checksum;
  private final Deque<String> signatureStack = new ArrayDeque<>();
  private boolean started;
  private boolean closed;
  private boolean inEnvelope;
  private int recordDepth;
  private int topLevelRecordsRead;

  protected AbstractStateReader(Protocol protocol, Set<Protocol.Mode> supportedModes) {
    this.protocol = Objects.requireNonNull(protocol, "protocol must not be null");
    Objects.requireNonNull(supportedModes, "supportedModes must not be null");
    if (supportedModes.isEmpty()) {
      throw new IllegalArgumentException("supportedModes must not be empty");
    }
    if (!supportedModes.contains(protocol.mode())) {
      throw new IllegalArgumentException(
        getClass().getSimpleName() +
          " does not support " +
          protocol.mode() +
          " mode (supported: " +
          supportedModes +
          ")"
      );
    }
    this.supportedModes = Set.copyOf(supportedModes);
    this.checksum = new ChecksumComputation(protocol.checksumAlgorithm());
  }

  public final Set<Protocol.Mode> supportedModes() {
    return supportedModes;
  }

  final void start() {
    if (started) {
      return;
    }
    started = true;
    protocol.onReaderStart(this);
  }

  final <H> H readHeaderInternal(@SuppressWarnings("SameParameterValue") TypeDescriptor<H> descriptor) {
    inEnvelope = true;
    checksum.suspend();
    try {
      doReadRecordOpen(0, "$header", descriptor);
      H result = descriptor.read(this);
      doReadRecordClose(0, "$header", descriptor);
      return result;
    } finally {
      checksum.resume();
      inEnvelope = false;
    }
  }

  protected abstract void doReadRecordOpen(int id, String name, TypeDescriptor<?> descriptor);

  protected abstract void doReadRecordClose(int id, String name, TypeDescriptor<?> descriptor);

  final <F> F readTerminatorInternal(@SuppressWarnings("SameParameterValue") TypeDescriptor<F> descriptor) {
    inEnvelope = true;
    checksum.suspend();
    try {
      doReadRecordOpen(0, "$terminator", descriptor);
      F result = descriptor.read(this);
      doReadRecordClose(0, "$terminator", descriptor);
      return result;
    } finally {
      checksum.resume();
      inEnvelope = false;
    }
  }

  /** True while the header or terminator is being read; subclasses skip type verification for those slots. */
  protected final boolean isInEnvelope() {
    return inEnvelope;
  }

  /** Lower-case hex of the checksum digest, or the empty string when the checksum is disabled. */
  public final String checksumHex() {
    return checksum.hex();
  }

  /**
   * Pushes the wire signature of the record being entered; called from {@code doReadRecordOpen}. Pass {@code ""} when
   * type verification is off or the wire carried no {@code @signature} suffix.
   */
  protected final void pushRecordSignature(String signature) {
    signatureStack.push(signature == null ? "" : signature);
  }

  /** Pops the signature pushed by {@link #pushRecordSignature(String)}; called from {@code doReadRecordClose}. */
  protected final void popRecordSignature() {
    if (!signatureStack.isEmpty()) {
      signatureStack.pop();
    }
  }

  /**
   * True while at least one more top-level record can be read: in FRAMED mode until the one record has been read, in
   * STREAMING mode as long as {@link #doHasMoreRecords()} sees more input.
   */
  public final boolean hasMoreRecords() {
    requireOpen();
    if (protocol.mode() == Protocol.Mode.FRAMED) {
      return topLevelRecordsRead == 0;
    }
    return doHasMoreRecords();
  }

  /**
   * Non-destructive peek for STREAMING mode: true while the source holds another top-level record. A subclass that
   * declares STREAMING support must override it; the default throws and is never called in FRAMED mode.
   */
  protected boolean doHasMoreRecords() {
    throw new UnsupportedOperationException(
      getClass().getSimpleName() + " declares STREAMING support but did not override doHasMoreRecords"
    );
  }

  /** Consumes the record separator that follows a top-level record in STREAMING mode; a no-op in FRAMED mode. */
  public final void consumeRecordSeparator() {
    requireOpen();
    if (protocol.mode() == Protocol.Mode.STREAMING) {
      doConsumeRecordSeparator();
    }
  }

  /**
   * Consumes the format's record separator in STREAMING mode. A subclass that declares STREAMING support must override
   * it; the default throws and is never called in FRAMED mode.
   */
  protected void doConsumeRecordSeparator() {
    throw new UnsupportedOperationException(
      getClass().getSimpleName() + " declares STREAMING support but did not override doConsumeRecordSeparator"
    );
  }

  @Override
  public final boolean hasField(int id, String name) {
    requireOpen();
    return doHasField(id, name);
  }

  @Override
  public final boolean readBoolean(int id, String name) {
    requireOpen();
    checksum.feedSlotId(id);
    boolean v = doReadBoolean(id, name);
    checksum.feedBoolean(v);
    return v;
  }

  @Override
  public final char readChar(int id, String name) {
    requireOpen();
    checksum.feedSlotId(id);
    char v = doReadChar(id, name);
    checksum.feedChar(v);
    return v;
  }

  @Override
  public final short readShort(int id, String name) {
    requireOpen();
    checksum.feedSlotId(id);
    short v = doReadShort(id, name);
    checksum.feedShort(v);
    return v;
  }

  @Override
  public final int readInt(int id, String name) {
    requireOpen();
    checksum.feedSlotId(id);
    int v = doReadInt(id, name);
    checksum.feedInt(v);
    return v;
  }

  @Override
  public final long readLong(int id, String name) {
    requireOpen();
    checksum.feedSlotId(id);
    long v = doReadLong(id, name);
    checksum.feedLong(v);
    return v;
  }

  @Override
  public final float readFloat(int id, String name) {
    requireOpen();
    checksum.feedSlotId(id);
    float v = doReadFloat(id, name);
    checksum.feedFloat(v);
    return v;
  }

  @Override
  public final double readDouble(int id, String name) {
    requireOpen();
    checksum.feedSlotId(id);
    double v = doReadDouble(id, name);
    checksum.feedDouble(v);
    return v;
  }

  @Override
  public final String readString(int id, String name) {
    requireOpen();
    checksum.feedSlotId(id);
    String v = doReadString(id, name);
    checksum.feedString(v);
    return v;
  }

  @Override
  public final byte[] readBytes(int id, String name) {
    requireOpen();
    checksum.feedSlotId(id);
    byte[] v = doReadBytes(id, name);
    checksum.feedBytes(v);
    return v;
  }

  @Override
  public final <T> T readRecord(int id, String name, TypeDescriptor<T> descriptor) {
    Objects.requireNonNull(descriptor, "descriptor must not be null");
    requireOpen();
    checksum.feedSlotId(id);
    checksum.feedDescriptorName(descriptor.descriptorName());
    recordDepth++;
    T result;
    List<String> unknowns = List.of();
    try {
      doReadRecordOpen(id, name, descriptor);
      result = descriptor.read(this);
      if (descriptor.evolutionPolicy().unknownSlots() == UnknownSlotPolicy.FAIL) {
        unknowns = doDrainUnknownSlotNames();
      }
      doReadRecordClose(id, name, descriptor);
    } finally {
      recordDepth--;
    }
    if (!unknowns.isEmpty()) {
      throw new IllegalStateException(
        "Unknown slots in record '" +
          name +
          "' (descriptor=" +
          descriptor.descriptorName() +
          "): " +
          unknowns +
          ". Set evolutionPolicy to SKIP to ignore them."
      );
    }
    afterTopLevelRecordRead();
    return result;
  }

  @Override
  public final <H> H readHeader(TypeDescriptor<H> descriptor) {
    throw new IllegalStateException("Header is read by the Protocol envelope; do not call readHeader directly.");
  }

  @Override
  public final <F> F readTerminator(TypeDescriptor<F> descriptor) {
    throw new IllegalStateException(
      "Terminator is read by the Protocol envelope on close(); do not call readTerminator directly."
    );
  }

  @Override
  public final String recordSignature() {
    return signatureStack.isEmpty() ? "" : signatureStack.peek();
  }

  @Override
  public final void beginNested(int id, String name) {
    requireOpen();
    checksum.feedSlotId(id);
    doBeginNested(id, name);
  }

  @Override
  public final void endNested(int id, String name) {
    requireOpen();
    doEndNested(id, name);
  }

  @Override
  public final WireConstraints wireConstraints() {
    return protocol.wireConstraints();
  }

  @Override
  public final ValueState peekValueStateOrNull(int id, String name) {
    requireOpen();
    ValueState state = doPeekValueStateOrNull(id, name);
    if (state != null) {
      checksum.feedSlotId(id);
      checksum.feedState(state);
      if (isConsumedByPeek(state)) {
        afterTopLevelRecordRead();
      }
    }
    return state;
  }

  private static boolean isConsumedByPeek(ValueState state) {
    return (
      state == ValueState.NULL ||
      state == ValueState.UNDEFINED ||
      state == ValueState.UNRESOLVED ||
      state == ValueState.TOMBSTONE
    );
  }

  @Override
  public final Throwable readError(int id, String name) {
    requireOpen();
    Throwable t = doReadError(id, name);
    checksum.feedThrowable(t);
    afterTopLevelRecordRead();
    return t;
  }

  @Override
  public final String readUnsupported(int id, String name) {
    requireOpen();
    String reason = doReadUnsupported(id, name);
    checksum.feedString(reason);
    afterTopLevelRecordRead();
    return reason;
  }

  @Override
  public final void close() {
    if (closed) {
      return;
    }
    try {
      protocol.onReaderClose(this);
    } finally {
      closed = true;
      doClose();
    }
  }

  /**
   * Drains the slots of the current record that the descriptor did not consume and returns their names. The default
   * returns an empty list, so a format that does not track consumed slots never reports unknowns to the FAIL policy.
   */
  protected List<String> doDrainUnknownSlotNames() {
    return List.of();
  }

  private void afterTopLevelRecordRead() {
    if (recordDepth != 0 || inEnvelope) {
      return;
    }
    topLevelRecordsRead++;
  }

  /** Called from {@link #close()} after the terminator has been read. */
  protected void doClose() {}

  protected abstract Throwable doReadError(int id, String name);

  protected abstract String doReadUnsupported(int id, String name);

  protected abstract ValueState doPeekValueStateOrNull(int id, String name);

  protected abstract void doEndNested(int id, String name);

  protected abstract void doBeginNested(int id, String name);

  protected abstract byte[] doReadBytes(int id, String name);

  protected abstract String doReadString(int id, String name);

  protected abstract double doReadDouble(int id, String name);

  protected abstract float doReadFloat(int id, String name);

  protected abstract long doReadLong(int id, String name);

  protected abstract int doReadInt(int id, String name);

  protected abstract short doReadShort(int id, String name);

  protected abstract char doReadChar(int id, String name);

  private void requireOpen() {
    if (closed) {
      throw new IllegalStateException("Reader has been closed");
    }
  }

  protected abstract boolean doReadBoolean(int id, String name);

  protected abstract boolean doHasField(int id, String name);
}
