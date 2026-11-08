package dev.zems.lib.value.valueio;

import dev.zems.lib.value.ValueState;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import dev.zems.lib.value.valueio.wire.WireConstraintEnforcer;
import dev.zems.lib.value.valueio.wire.WireConstraints;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Base class for {@link StateWriter} implementations. The {@code StateWriter} methods are {@code final} and run the
 * envelope concerns (closed check, byte count, checksum feed, wire constraints) before delegating to the {@code do<Op>}
 * hooks a wire format implements. Instances come from {@link ValueIo} or {@link Protocol#writer}, which start them;
 * a directly constructed writer never writes its header.
 */
public abstract class AbstractStateWriter implements StateWriter {

  private static final int SCOPE_POOL_CAP = 16;
  protected final Protocol protocol;
  private final Set<Protocol.Mode> supportedModes;
  private final ChecksumComputation checksum;
  private final Deque<Set<String>> scopeNames = new ArrayDeque<>();
  // Scope-name sets are pooled: profiling showed a fresh HashSet per nested scope dominating allocation on record
  // writes. The cap keeps a one-off deep write from pinning oversized sets.
  private final Deque<Set<String>> scopePool = new ArrayDeque<>();
  private long byteCount;
  private boolean started;
  private boolean closed;
  private boolean inEnvelope;
  private int recordDepth;
  private int topLevelRecordsWritten;

  protected AbstractStateWriter(Protocol protocol, Set<Protocol.Mode> supportedModes) {
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
    this.scopeNames.push(takeScopeSet());
  }

  private Set<String> takeScopeSet() {
    Set<String> s = scopePool.pollLast();
    return s != null ? s : new HashSet<>();
  }

  public final Set<Protocol.Mode> supportedModes() {
    return supportedModes;
  }

  final void start() {
    if (started) {
      return;
    }
    started = true;
    protocol.onWriterStart(this);
  }

  final <H> void writeHeaderInternal(@SuppressWarnings("SameParameterValue") TypeDescriptor<H> descriptor, H header) {
    inEnvelope = true;
    checksum.suspend();
    try {
      doWriteRecordOpen(0, "$header", descriptor);
      descriptor.write(this, header);
      doWriteRecordClose(0, "$header", descriptor);
    } finally {
      checksum.resume();
      inEnvelope = false;
    }
  }

  protected abstract void doWriteRecordOpen(int id, String name, TypeDescriptor<?> descriptor);

  protected abstract void doWriteRecordClose(int id, String name, TypeDescriptor<?> descriptor);

  final <F> void writeTerminatorInternal(
    @SuppressWarnings("SameParameterValue") TypeDescriptor<F> descriptor,
    F terminator
  ) {
    inEnvelope = true;
    checksum.suspend();
    try {
      doWriteRecordOpen(0, "$terminator", descriptor);
      descriptor.write(this, terminator);
      doWriteRecordClose(0, "$terminator", descriptor);
    } finally {
      checksum.resume();
      inEnvelope = false;
    }
  }

  /** True while the header or terminator is being written; subclasses skip type verification for those slots. */
  protected final boolean isInEnvelope() {
    return inEnvelope;
  }

  /** Current record nesting depth: {@code 0} at the root scope, {@code 1} inside a top-level record's body. */
  protected final int recordDepth() {
    return recordDepth;
  }

  public final long byteCount() {
    return byteCount;
  }

  /** Lower-case hex of the checksum digest, or the empty string when the checksum is disabled. */
  public final String checksumHex() {
    return checksum.hex();
  }

  @Override
  public final WireConstraints wireConstraints() {
    return protocol.wireConstraints();
  }

  @Override
  public final void writeBoolean(int id, String name, boolean value) {
    requireOpen();
    enforceSlotName(name);
    checksum.feedSlotId(id);
    checksum.feedBoolean(value);
    byteCount += 1;
    doWriteBoolean(id, name, value);
  }

  @Override
  public final void writeChar(int id, String name, char value) {
    requireOpen();
    enforceSlotName(name);
    checksum.feedSlotId(id);
    checksum.feedChar(value);
    byteCount += Character.BYTES;
    doWriteChar(id, name, value);
  }

  @Override
  public final void writeShort(int id, String name, short value) {
    requireOpen();
    enforceSlotName(name);
    checksum.feedSlotId(id);
    checksum.feedShort(value);
    byteCount += Short.BYTES;
    doWriteShort(id, name, value);
  }

  @Override
  public final void writeInt(int id, String name, int value) {
    requireOpen();
    enforceSlotName(name);
    checksum.feedSlotId(id);
    checksum.feedInt(value);
    byteCount += Integer.BYTES;
    doWriteInt(id, name, value);
  }

  @Override
  public final void writeLong(int id, String name, long value) {
    requireOpen();
    enforceSlotName(name);
    checksum.feedSlotId(id);
    checksum.feedLong(value);
    byteCount += Long.BYTES;
    doWriteLong(id, name, value);
  }

  @Override
  public final void writeFloat(int id, String name, float value) {
    requireOpen();
    enforceSlotName(name);
    if (!inEnvelope) {
      WireConstraintEnforcer.checkFinite(value, protocol.wireConstraints());
    }
    checksum.feedSlotId(id);
    checksum.feedFloat(value);
    byteCount += Float.BYTES;
    doWriteFloat(id, name, value);
  }

  @Override
  public final void writeDouble(int id, String name, double value) {
    requireOpen();
    enforceSlotName(name);
    if (!inEnvelope) {
      WireConstraintEnforcer.checkFinite(value, protocol.wireConstraints());
    }
    checksum.feedSlotId(id);
    checksum.feedDouble(value);
    byteCount += Double.BYTES;
    doWriteDouble(id, name, value);
  }

  @Override
  public final void writeString(int id, String name, String value) {
    requireOpen();
    enforceSlotName(name);
    if (!inEnvelope) {
      WireConstraintEnforcer.checkStringLength(value.length(), protocol.wireConstraints());
    }
    checksum.feedSlotId(id);
    checksum.feedString(value);
    byteCount += value.length();
    doWriteString(id, name, value);
  }

  @Override
  public final void writeBytes(int id, String name, byte[] value) {
    requireOpen();
    enforceSlotName(name);
    if (!inEnvelope) {
      WireConstraintEnforcer.checkStringLength(value.length, protocol.wireConstraints());
    }
    checksum.feedSlotId(id);
    checksum.feedBytes(value);
    byteCount += value.length;
    doWriteBytes(id, name, value);
  }

  @Override
  public final <H> void writeHeader(TypeDescriptor<H> descriptor, H header) {
    throw new IllegalStateException("Header is written by the Protocol envelope; do not call writeHeader directly.");
  }

  @Override
  public final <F> void writeTerminator(TypeDescriptor<F> descriptor, F terminator) {
    throw new IllegalStateException(
      "Terminator is written by the Protocol envelope on close(); do not call writeTerminator directly."
    );
  }

  @Override
  public final void beginNested(int id, String name) {
    requireOpen();
    enterScope(name);
    checksum.feedSlotId(id);
    recordDepth++;
    doBeginNested(id, name);
  }

  @Override
  public final void endNested(int id, String name) {
    requireOpen();
    doEndNested(id, name);
    recordDepth--;
    leaveScope();
  }

  @Override
  public final void writeNull(int id, String name) {
    requireOpen();
    enforceSlotName(name);
    checksum.feedSlotId(id);
    checksum.feedState(ValueState.NULL);
    byteCount += 1;
    doWriteNull(id, name);
    afterTopLevelRecord();
  }

  @Override
  public final void writeUndefined(int id, String name) {
    requireOpen();
    enforceSlotName(name);
    checksum.feedSlotId(id);
    checksum.feedState(ValueState.UNDEFINED);
    byteCount += 1;
    doWriteUndefined(id, name);
    afterTopLevelRecord();
  }

  @Override
  public final void writeUnresolved(int id, String name) {
    requireOpen();
    enforceSlotName(name);
    checksum.feedSlotId(id);
    checksum.feedState(ValueState.UNRESOLVED);
    byteCount += 1;
    doWriteUnresolved(id, name);
    afterTopLevelRecord();
  }

  @Override
  public final void writeError(int id, String name, Throwable throwable) {
    Objects.requireNonNull(throwable, "throwable must not be null");
    requireOpen();
    enforceSlotName(name);
    checksum.feedSlotId(id);
    checksum.feedState(ValueState.ERROR);
    checksum.feedThrowable(throwable);
    byteCount += 1;
    doWriteError(id, name, throwable);
    afterTopLevelRecord();
  }

  @Override
  public final void writeUnsupported(int id, String name, String reason) {
    Objects.requireNonNull(reason, "reason must not be null");
    requireOpen();
    enforceSlotName(name);
    checksum.feedSlotId(id);
    checksum.feedState(ValueState.UNSUPPORTED);
    checksum.feedString(reason);
    byteCount += 1;
    doWriteUnsupported(id, name, reason);
    afterTopLevelRecord();
  }

  @Override
  public final void writeTombstone(int id, String name) {
    requireOpen();
    enforceSlotName(name);
    checksum.feedSlotId(id);
    checksum.feedState(ValueState.TOMBSTONE);
    byteCount += 1;
    doWriteTombstone(id, name);
    afterTopLevelRecord();
  }

  @Override
  public final <T> void writeRecord(int id, String name, TypeDescriptor<T> descriptor, T value) {
    Objects.requireNonNull(descriptor, "descriptor must not be null");
    Objects.requireNonNull(value, "value must not be null");
    requireOpen();
    enterScope(name);
    checksum.feedSlotId(id);
    checksum.feedDescriptorName(descriptor.descriptorName());
    recordDepth++;
    try {
      doWriteRecordOpen(id, name, descriptor);
      descriptor.write(this, value);
      doWriteRecordClose(id, name, descriptor);
    } finally {
      recordDepth--;
      leaveScope();
    }
    afterTopLevelRecord();
  }

  @Override
  public final void close() {
    if (closed) {
      return;
    }
    protocol.onWriterClose(this);
    closed = true;
    doClose();
  }

  /** Called from {@link #close()} after the terminator has been written. */
  protected void doClose() {}

  private void enterScope(String name) {
    enforceSlotName(name);
    if (inEnvelope) {
      return;
    }
    WireConstraintEnforcer.checkDepth(recordDepth + 1, protocol.wireConstraints());
    scopeNames.push(takeScopeSet());
  }

  private void leaveScope() {
    if (inEnvelope) {
      return;
    }
    releaseScopeSet(scopeNames.pop());
  }

  private void afterTopLevelRecord() {
    if (recordDepth != 0 || inEnvelope) {
      return;
    }
    topLevelRecordsWritten++;
    forgetSlotNamesOfFinishedRecord();
    if (protocol.mode() == Protocol.Mode.STREAMING) {
      doWriteRecordSeparator();
    }
  }

  private void forgetSlotNamesOfFinishedRecord() {
    Set<String> root = scopeNames.peek();
    if (root != null) {
      root.clear();
    }
  }

  private void releaseScopeSet(Set<String> s) {
    if (s == null) {
      return;
    }
    s.clear();
    if (scopePool.size() < SCOPE_POOL_CAP) {
      scopePool.addLast(s);
    }
  }

  /**
   * Writes the format's record separator after each top-level record or state marker in STREAMING mode. A subclass
   * that declares STREAMING support must override it; the default throws and is never called in FRAMED mode.
   */
  protected void doWriteRecordSeparator() {
    throw new UnsupportedOperationException(
      getClass().getSimpleName() + " declares STREAMING support but did not override doWriteRecordSeparator"
    );
  }

  protected abstract void doWriteTombstone(int id, String name);

  protected abstract void doWriteError(int id, String name, Throwable throwable);

  protected abstract void doWriteUnsupported(int id, String name, String reason);

  protected abstract void doWriteUnresolved(int id, String name);

  protected abstract void doWriteUndefined(int id, String name);

  protected abstract void doWriteNull(int id, String name);

  protected abstract void doEndNested(int id, String name);

  protected abstract void doBeginNested(int id, String name);

  protected abstract void doWriteBytes(int id, String name, byte[] value);

  protected abstract void doWriteString(int id, String name, String value);

  protected abstract void doWriteDouble(int id, String name, double value);

  protected abstract void doWriteFloat(int id, String name, float value);

  protected abstract void doWriteLong(int id, String name, long value);

  protected abstract void doWriteInt(int id, String name, int value);

  protected abstract void doWriteShort(int id, String name, short value);

  protected abstract void doWriteChar(int id, String name, char value);

  private void requireOpen() {
    if (closed) {
      throw new IllegalStateException("Writer has been closed");
    }
  }

  private void enforceSlotName(String name) {
    if (inEnvelope) {
      return;
    }
    Set<String> seen = scopeNames.peek();
    if (seen == null) {
      throw new IllegalStateException("Internal error: no active scope when validating slot name '" + name + "'");
    }
    if (seen.contains(name)) {
      WireConstraintEnforcer.checkDuplicateKey(name, protocol.wireConstraints());
    }
    seen.add(name);
  }

  protected abstract void doWriteBoolean(int id, String name, boolean value);

  protected final int topLevelRecordsWritten() {
    return topLevelRecordsWritten;
  }
}
