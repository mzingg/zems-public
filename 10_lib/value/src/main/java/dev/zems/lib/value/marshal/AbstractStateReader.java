package dev.zems.lib.value.marshal;

import dev.zems.lib.value.ValueState;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import dev.zems.lib.value.marshal.descriptor.UnknownSlotPolicy;
import dev.zems.lib.value.marshal.wire.WireConstraints;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Abstract base implementation of {@link StateReader}. Mirror of {@link AbstractStateWriter}: the public
 * {@link StateReader} methods are {@code final} and run protocol-envelope concerns (closed check, logical-stream
 * digest) before delegating to {@code do<Op>} hooks implemented by the wire-format subclass.
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

  /** The set of {@link Protocol.Mode}s this reader supports. Immutable. */
  public final Set<Protocol.Mode> supportedModes() {
    return supportedModes;
  }

  /** Invoked by the Protocol terminal factory after the subclass ctor returns. Reads + verifies the header. */
  final void start() {
    if (started) {
      return;
    }
    started = true;
    protocol.onReaderStart(this);
  }

  // ============================================================
  // Envelope orchestration (called by Protocol; package-private)
  // ============================================================

  /** Used by Protocol to read the header without going through external readHeader (which throws). */
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

  /**
   * Open a record scope on the read side. Symmetric to
   * {@link AbstractStateWriter#doWriteRecordOpen(int, String, TypeDescriptor)}.
   */
  protected abstract void doReadRecordOpen(int id, String name, TypeDescriptor<?> descriptor);

  /** Close a record scope on the read side. */
  protected abstract void doReadRecordClose(int id, String name, TypeDescriptor<?> descriptor);

  /** Used by Protocol to read the terminator without going through external readTerminator (which throws). */
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

  /**
   * True while the base is reading the header or terminator. Subclasses use this to suppress user-level concerns (e.g.
   * type verification) for envelope slots.
   */
  protected final boolean isInEnvelope() {
    return inEnvelope;
  }

  /** Lower-case hex of the checksum digest, or empty string if checksum is disabled. */
  public final String checksumHex() {
    return checksum.hex();
  }

  // ============ Signature stack ============

  /**
   * Subclass hook: push the wire signature for the record being entered. Format readers parse the
   * {@code descriptorName@signature} suffix when type verification is on and call this from inside their
   * {@code doReadRecordOpen}. Outside type verification (or when no suffix was present), pass {@code ""}.
   */
  protected final void pushRecordSignature(String signature) {
    signatureStack.push(signature == null ? "" : signature);
  }

  /** Subclass hook: pop the signature when leaving a record. Called from {@code doReadRecordClose}. */
  protected final void popRecordSignature() {
    if (!signatureStack.isEmpty()) {
      signatureStack.pop();
    }
  }

  // ============================================================
  // Public API — final, delegate to do<Op>
  // ============================================================

  // ============ Streaming-mode iteration ============

  /**
   * True iff there is at least one more top-level record available to consume. In FRAMED mode, returns true until
   * exactly one top-level record has been read (then false). In STREAMING mode, delegates to
   * {@link #doHasMoreRecords()} which performs a non-destructive peek on the underlying source.
   */
  public final boolean hasMoreRecords() {
    requireOpen();
    if (protocol.mode() == Protocol.Mode.FRAMED) {
      return topLevelRecordsRead == 0;
    }
    return doHasMoreRecords();
  }

  /**
   * Non-destructive peek: true iff at least one more top-level record is available on the source. Default body throws —
   * subclasses that declare {@link Protocol.Mode#STREAMING} support must override. FRAMED-only subclasses don't need to
   * override since the base never invokes this hook in FRAMED mode.
   */
  protected boolean doHasMoreRecords() {
    throw new UnsupportedOperationException(
      getClass().getSimpleName() + " declares STREAMING support but did not override doHasMoreRecords"
    );
  }

  /**
   * Consumes the format-specific record separator that follows a top-level record in {@link Protocol.Mode#STREAMING}
   * mode. No-op in FRAMED mode. The streaming spliterator calls this after each top-level read.
   */
  public final void consumeRecordSeparator() {
    requireOpen();
    if (protocol.mode() == Protocol.Mode.STREAMING) {
      doConsumeRecordSeparator();
    }
  }

  /**
   * Consumes the format-specific record separator that follows a top-level record in {@link Protocol.Mode#STREAMING}
   * mode. Default body throws — subclasses that declare {@link Protocol.Mode#STREAMING} support must override.
   * FRAMED-only subclasses don't need to override since the base never invokes this hook in FRAMED mode.
   */
  protected void doConsumeRecordSeparator() {
    throw new UnsupportedOperationException(
      getClass().getSimpleName() + " declares STREAMING support but did not override doConsumeRecordSeparator"
    );
  }

  // ============ Field presence ============

  @Override
  public final boolean hasField(int id, String name) {
    requireOpen();
    return doHasField(id, name);
  }

  // ============ Primitives ============

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

  // ============ Composite records ============

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
      // Capture unknowns before close so we can include them in a clean error.
      // For FAIL we drain to collect names; for SKIP close already advances past any leftover
      // payload so we skip the drain.
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

  // ============ Header / Terminator (envelope-only) ============

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

  // ============ Structure ============

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

  /** Wire-level safety bounds — exposes the underlying protocol's {@link WireConstraints}. */
  @Override
  public final WireConstraints wireConstraints() {
    return protocol.wireConstraints();
  }

  // ============ State markers ============

  @Override
  public final ValueState peekValueStateOrNull(int id, String name) {
    requireOpen();
    ValueState state = doPeekValueStateOrNull(id, name);
    if (state != null) {
      checksum.feedSlotId(id);
      checksum.feedState(state);
      // NULL / UNDEFINED / UNRESOLVED are fully consumed by the peek; ERROR still needs readError.
      if (
        state == ValueState.NULL ||
        state == ValueState.UNDEFINED ||
        state == ValueState.UNRESOLVED ||
        state == ValueState.TOMBSTONE
      ) {
        afterTopLevelRecordRead();
      }
    }
    return state;
  }

  @Override
  public final Throwable readError(int id, String name) {
    requireOpen();
    Throwable t = doReadError(id, name);
    checksum.feedThrowable(t);
    afterTopLevelRecordRead();
    return t;
  }

  // ============ Lifecycle ============

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
   * Subclass hook: drain any slots present in the current record body but not consumed by the descriptor's read lambda,
   * returning their names. The default returns an empty list (formats that don't track consumed slots opt out and will
   * not surface unknowns to FAIL).
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

  /** Called from {@link #close()} after the terminator is read. Default is no-op. */
  protected void doClose() {
    // default: no-op
  }

  // ============================================================
  // Wire-format hooks (subclasses implement)
  // ============================================================

  protected abstract Throwable doReadError(int id, String name);

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
