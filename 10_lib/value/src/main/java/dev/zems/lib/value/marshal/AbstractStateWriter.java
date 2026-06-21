package dev.zems.lib.value.marshal;

import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import dev.zems.lib.value.marshal.wire.WireConstraintEnforcer;
import dev.zems.lib.value.marshal.wire.WireConstraints;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Abstract base implementation of {@link StateWriter}. Owns the protocol-envelope concerns (header on {@link #start()},
 * terminator on {@link #close()}, optional logical-stream checksum, byte counting) so concrete wire-format subclasses
 * only need to implement the {@code do<Op>} encoding hooks.
 *
 * <p>
 * Every {@link StateWriter} method on this class is {@code final}. Each runs the envelope concerns (closed check, byte
 * count, digest feed) and then delegates to a matching {@code do<Op>} hook. Subclasses extend by implementing the
 * hooks, never by overriding the public API.
 *
 * <p>
 * Lifecycle: the {@link Protocol} terminal factory constructs the subclass and immediately calls {@link #start()}
 * (package-private). Callers must NOT instantiate concrete wire-format classes directly — they go through
 * {@link Protocol}.
 */
public abstract class AbstractStateWriter implements StateWriter {

  private static final int SCOPE_POOL_CAP = 16;
  protected final Protocol protocol;
  private final Set<Protocol.Mode> supportedModes;
  private final ChecksumComputation checksum;
  private final Deque<Set<String>> scopeNames = new ArrayDeque<>();
  /**
   * Pool of cleared scope-name HashSets, reused across enterScope/leaveScope cycles. Prep profiling
   * (snapshot-20260516-185810) showed {@code new HashSet<>()} from {@code enterScope} accounting for ~12 GB across the
   * suite — each NodeValue write enters ~3 nested scopes. Pool is bounded by max nesting depth in practice (tree writes
   * peak around 4-5 deep); cap at 16 to avoid retaining oversized sets from one-off deep writes.
   */
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
    // Root scope. Header/terminator writes live above this and are tagged via inEnvelope.
    this.scopeNames.push(takeScopeSet());
  }

  /** Returns a cleared HashSet from the pool, or a fresh one if the pool is empty. */
  private Set<String> takeScopeSet() {
    Set<String> s = scopePool.pollLast();
    return s != null ? s : new HashSet<>();
  }

  // ============================================================
  // Envelope orchestration (called by Protocol; package-private)
  // ============================================================

  /** The set of {@link Protocol.Mode}s this writer supports. Immutable. */
  public final Set<Protocol.Mode> supportedModes() {
    return supportedModes;
  }

  /** Invoked by the Protocol terminal factory after the subclass ctor returns. Writes the header. */
  final void start() {
    if (started) {
      return;
    }
    started = true;
    protocol.onWriterStart(this);
  }

  /** Used by Protocol to emit the header without going through external writeHeader (which throws). */
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

  /** Open a record scope. Subclass writes outer name, type tag, optional descriptor name, inner-scope opener. */
  protected abstract void doWriteRecordOpen(int id, String name, TypeDescriptor<?> descriptor);

  /** Close a record scope. Subclass writes inner-scope closer (e.g. NESTED_END_MARKER, '}'). */
  protected abstract void doWriteRecordClose(int id, String name, TypeDescriptor<?> descriptor);

  /** Used by Protocol to emit the terminator without going through external writeTerminator (which throws). */
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

  /**
   * True while the base is writing the header or terminator. Subclasses use this to suppress user-level concerns (e.g.
   * type verification) for envelope slots.
   */
  protected final boolean isInEnvelope() {
    return inEnvelope;
  }

  // ============================================================
  // Public API — final, delegate to do<Op>
  // ============================================================

  /**
   * Current record nesting depth. {@code 0} means the writer is at the root scope (no top-level record open). {@code 1}
   * means a top-level record is currently open and the writer is inside its body. Used by streaming-mode formats to
   * detect top-level open/close transitions.
   */
  protected final int recordDepth() {
    return recordDepth;
  }

  /** Total payload bytes written (used by the protocol terminator). */
  public final long byteCount() {
    return byteCount;
  }

  /** Lower-case hex of the checksum digest, or empty string if checksum is disabled. */
  public final String checksumHex() {
    return checksum.hex();
  }

  @Override
  public final WireConstraints wireConstraints() {
    return protocol.wireConstraints();
  }

  // ============ Primitives ============

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

  // ============ Header / Terminator (envelope-only) ============

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

  // ============ Structure ============

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

  // ============ State markers ============

  @Override
  public final void writeNull(int id, String name) {
    requireOpen();
    enforceSlotName(name);
    checksum.feedSlotId(id);
    checksum.feedStateTag(1);
    byteCount += 1;
    doWriteNull(id, name);
    afterTopLevelRecord();
  }

  @Override
  public final void writeUndefined(int id, String name) {
    requireOpen();
    enforceSlotName(name);
    checksum.feedSlotId(id);
    checksum.feedStateTag(2);
    byteCount += 1;
    doWriteUndefined(id, name);
    afterTopLevelRecord();
  }

  @Override
  public final void writeUnresolved(int id, String name) {
    requireOpen();
    enforceSlotName(name);
    checksum.feedSlotId(id);
    checksum.feedStateTag(3);
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
    checksum.feedStateTag(4);
    checksum.feedThrowable(throwable);
    byteCount += 1;
    doWriteError(id, name, throwable);
    afterTopLevelRecord();
  }

  @Override
  public final void writeTombstone(int id, String name) {
    requireOpen();
    enforceSlotName(name);
    checksum.feedSlotId(id);
    checksum.feedStateTag(6);
    byteCount += 1;
    doWriteTombstone(id, name);
    afterTopLevelRecord();
  }

  // ============ Composite records ============

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

  // ============ Lifecycle ============

  @Override
  public final void close() {
    if (closed) {
      return;
    }
    protocol.onWriterClose(this);
    closed = true;
    doClose();
  }

  /** Called from {@link #close()} after the terminator is written. Default is no-op. */
  protected void doClose() {
    // default: no-op
  }

  /** Pushes a fresh scope set after writing the slot name in the parent scope. */
  private void enterScope(String name) {
    enforceSlotName(name);
    if (inEnvelope) {
      return;
    }
    WireConstraintEnforcer.checkDepth(recordDepth + 1, protocol.wireConstraints());
    scopeNames.push(takeScopeSet());
  }

  /** Pops the scope set pushed by {@link #enterScope(String)}. */
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
    // Each top-level record is a fresh document — duplicate-name tracking applies within a
    // record, not across them.
    Set<String> root = scopeNames.peek();
    if (root != null) {
      root.clear();
    }
    if (protocol.mode() == Protocol.Mode.STREAMING) {
      doWriteRecordSeparator();
    }
  }

  /** Clears the set and returns it to the pool (capped to bound retained memory). */
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
   * Called after each top-level record (or top-level state-marker) write in {@link Protocol.Mode#STREAMING} mode, to
   * emit the format's record separator. Default body throws — subclasses that declare {@link Protocol.Mode#STREAMING}
   * support must override. FRAMED-only subclasses don't need to override since the base never invokes this hook in
   * FRAMED mode.
   */
  protected void doWriteRecordSeparator() {
    throw new UnsupportedOperationException(
      getClass().getSimpleName() + " declares STREAMING support but did not override doWriteRecordSeparator"
    );
  }

  // ============================================================
  // Wire-format hooks (subclasses implement)
  // ============================================================

  protected abstract void doWriteTombstone(int id, String name);

  protected abstract void doWriteError(int id, String name, Throwable throwable);

  protected abstract void doWriteUnresolved(int id, String name);

  protected abstract void doWriteUndefined(int id, String name);

  protected abstract void doWriteNull(int id, String name);

  /** Close a nested scope opened by {@link #doBeginNested(int, String)}. */
  protected abstract void doEndNested(int id, String name);

  /** Open a nested scope (used by descriptor lambdas like Map for keyed entries, or by external callers). */
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

  /**
   * Records the slot name in the current scope; throws on duplicate when policy=FAIL. Skipped while inside the
   * envelope.
   */
  private void enforceSlotName(String name) {
    if (inEnvelope) {
      return;
    }
    Set<String> seen = scopeNames.peek();
    if (seen == null) {
      // The constructor pushes a root scope; it can only be missing if enter/exit are imbalanced.
      throw new IllegalStateException("Internal error: no active scope when validating slot name '" + name + "'");
    }
    if (seen.contains(name)) {
      WireConstraintEnforcer.checkDuplicateKey(name, protocol.wireConstraints());
    }
    seen.add(name);
  }

  protected abstract void doWriteBoolean(int id, String name, boolean value);

  /** Number of top-level records written so far. Used by the Stream API to enforce framed exactly-1. */
  protected final int topLevelRecordsWritten() {
    return topLevelRecordsWritten;
  }
}
