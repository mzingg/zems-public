package dev.zems.lib.value.marshal;

import dev.zems.lib.value.Value;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import dev.zems.lib.value.marshal.wire.WireConstraints;
import dev.zems.lib.value.marshal.wire.v1.Header;
import dev.zems.lib.value.marshal.wire.v1.Terminator;
import java.util.Objects;

/**
 * Versioned wire envelope for {@link Value Value} marshalling. Sealed: only {@link V1} exists.
 *
 * <p>
 * {@code Protocol} is the envelope contract — header, terminator, mode, checksum — that the state readers/writers
 * consume. The public I/O surface (terminal factories, Stream API) lives on {@link ValueIo}, which configures a
 * {@code V1} under the hood and hands it to the reader/writer.
 *
 * <h2>Versioning policy</h2>
 *
 * <p>
 * <b>V1 is the only version we ship and is intended to stay that way.</b> Adding a {@code V2} is not a default
 * expectation — it would require a deliberate new decision driven by a forcing wire-format change. There is no
 * speculative scaffolding for a future version, no deprecation contract for V1, and no parallel-versions story. The
 * version is the single implicit default: callers reach I/O through {@link ValueIo} and never spell it.
 *
 * <p>
 * The {@code int version} field on {@code Header} (always {@code 1} on write, verified on read) is a defensive safety
 * net — it lets a V1 reader reject a hypothetical foreign-version stream with a clear error rather than crashing on
 * misinterpreted bytes. It is not a hook for future versions.
 *
 * <p>
 * <b>No format detection.</b> We deliberately do not offer a {@code detect(...)} / format-sniffing API. Callers pick
 * the format and mode up-front via {@link ValueIo} ({@code ValueIo.framed().binaryWriter(...)},
 * {@code ValueIo.streaming().jsonReader(...)}). One less ambiguous code path; one less way for two systems to silently
 * disagree about the wire.
 */
public sealed interface Protocol permits Protocol.V1 {
  /** The envelope mode this protocol carries. */
  Mode mode();

  /** The checksum algorithm this protocol uses (default {@link ChecksumAlgorithm#NONE}). */
  default ChecksumAlgorithm checksumAlgorithm() {
    return ChecksumAlgorithm.NONE;
  }

  /** The wire-level safety bounds this protocol enforces (default {@link WireConstraints#SECURE_DEFAULTS}). */
  default WireConstraints wireConstraints() {
    return WireConstraints.SECURE_DEFAULTS;
  }

  /** Lifecycle: writer started — implementation writes the header (or no-op). */
  void onWriterStart(AbstractStateWriter writer);

  /** Lifecycle: writer closing — implementation writes the terminator (or no-op). */
  void onWriterClose(AbstractStateWriter writer);

  /** Lifecycle: reader started — implementation reads + verifies the header (or no-op). */
  void onReaderStart(AbstractStateReader reader);

  /** Lifecycle: reader closing — implementation reads + verifies the terminator (or no-op). */
  void onReaderClose(AbstractStateReader reader);

  /** Returns a new protocol instance with the given checksum algorithm. */
  Protocol withChecksum(ChecksumAlgorithm algorithm);

  /**
   * Custom-format extension point: pass a factory that constructs an {@link AbstractStateWriter} subclass. The factory
   * receives this protocol; the returned writer has {@code start()} invoked before being handed back.
   */
  default <W extends AbstractStateWriter> W writer(WriterFactory<W> factory) {
    W w = factory.create(this);
    w.start();
    return w;
  }

  /** Custom-format extension point for readers — symmetric to {@link #writer(WriterFactory)}. */
  default <R extends AbstractStateReader> R reader(ReaderFactory<R> factory) {
    R r = factory.create(this);
    r.start();
    return r;
  }

  /** Envelope mode — single-document {@link #FRAMED} or open-ended {@link #STREAMING}. */
  enum Mode {
    /** Single-document envelope: header on open, terminator on close. */
    FRAMED,
    /** Multi-record stream: header on open, separator between top-level records, terminator on close. */
    STREAMING,
  }

  /** Factory-functional-interface for a custom wire-format writer. */
  @FunctionalInterface
  interface WriterFactory<W extends AbstractStateWriter> {
    W create(Protocol protocol);
  }

  /** Factory-functional-interface for a custom wire-format reader. */
  @FunctionalInterface
  interface ReaderFactory<R extends AbstractStateReader> {
    R create(Protocol protocol);
  }

  /**
   * Version 1 of the protocol. Header carries
   * {@code (int version, Mode mode, boolean typeVerification, ChecksumAlgorithm checksum)}; terminator carries
   * {@code (long byteCount, String checksumHex)}.
   */
  final class V1 implements Protocol {

    public static final int VERSION = 1;

    public static final TypeDescriptor<Header> HEADER_DESCRIPTOR = TypeDescriptor.of(
      "zems.protocol.v1.Header",
      Header.class,
      r ->
        new Header(
          r.readInt(0, "version"),
          Mode.values()[r.readInt(1, "mode")],
          r.readBoolean(2, "typeVerification"),
          ChecksumAlgorithm.values()[r.readInt(3, "checksum")]
        ),
      (w, h) -> {
        w.writeInt(0, "version", h.version());
        w.writeInt(1, "mode", h.mode().ordinal());
        w.writeBoolean(2, "typeVerification", h.typeVerification());
        w.writeInt(3, "checksum", h.checksum().ordinal());
      }
    );

    public static final TypeDescriptor<Terminator> TERMINATOR_DESCRIPTOR = TypeDescriptor.of(
      "zems.protocol.v1.Terminator",
      Terminator.class,
      r -> new Terminator(r.readLong(0, "byteCount"), r.readString(1, "checksumHex")),
      (w, t) -> {
        w.writeLong(0, "byteCount", t.byteCount());
        w.writeString(1, "checksumHex", t.checksumHex());
      }
    );

    private final Mode mode;
    private final boolean typeVerification;
    private final ChecksumAlgorithm checksum;
    private final WireConstraints wireConstraints;

    private V1(Mode mode, boolean typeVerification, ChecksumAlgorithm checksum, WireConstraints wireConstraints) {
      this.mode = mode;
      this.typeVerification = typeVerification;
      this.checksum = checksum;
      this.wireConstraints = wireConstraints;
    }

    // ============ Mode factories (package-private — the public I/O entry is ValueIo) ============

    static V1 framed() {
      return new V1(Mode.FRAMED, false, ChecksumAlgorithm.NONE, WireConstraints.SECURE_DEFAULTS);
    }

    static V1 streaming() {
      return new V1(Mode.STREAMING, false, ChecksumAlgorithm.NONE, WireConstraints.SECURE_DEFAULTS);
    }

    // ============ Chain customizers ============

    public V1 usingTypeVerification() {
      return new V1(mode, true, checksum, wireConstraints);
    }

    public V1 withoutTypeVerification() {
      return new V1(mode, false, checksum, wireConstraints);
    }

    /**
     * Returns a new protocol with the given wire-level safety bounds. Plain {@code framed()} and {@code streaming()}
     * are seeded with {@link WireConstraints#SECURE_DEFAULTS}; call this to override individual fields (via the
     * builder) or to opt out wholesale via {@link WireConstraints#UNCHECKED}.
     */
    public V1 withWireConstraints(WireConstraints constraints) {
      Objects.requireNonNull(constraints, "wireConstraints must not be null");
      return new V1(mode, typeVerification, checksum, constraints);
    }

    @Override
    public Mode mode() {
      return mode;
    }

    // ============ Accessors ============

    @Override
    public ChecksumAlgorithm checksumAlgorithm() {
      return checksum;
    }

    /** The wire-level safety bounds applied by readers and writers. */
    @Override
    public WireConstraints wireConstraints() {
      return wireConstraints;
    }

    @Override
    public void onWriterStart(AbstractStateWriter w) {
      if (mode == Mode.STREAMING) {
        return;
      }
      w.writeHeaderInternal(
        HEADER_DESCRIPTOR,
        new Header(VERSION, mode, typeVerificationEnabled(), checksumAlgorithm())
      );
    }

    @Override
    public void onWriterClose(AbstractStateWriter w) {
      if (mode == Mode.STREAMING) {
        return;
      }
      w.writeTerminatorInternal(TERMINATOR_DESCRIPTOR, new Terminator(w.byteCount(), w.checksumHex()));
    }

    @Override
    public void onReaderStart(AbstractStateReader r) {
      if (mode == Mode.STREAMING) {
        return;
      }
      Header header = r.readHeaderInternal(HEADER_DESCRIPTOR);
      verifyHeader(header);
    }

    @Override
    public void onReaderClose(AbstractStateReader r) {
      if (mode == Mode.STREAMING) {
        return;
      }
      Terminator terminator = r.readTerminatorInternal(TERMINATOR_DESCRIPTOR);
      String computed = r.checksumHex();
      if (checksumAlgorithm() != ChecksumAlgorithm.NONE && !computed.equals(terminator.checksumHex())) {
        throw new IllegalStateException(
          "Checksum mismatch: stream terminator has " + terminator.checksumHex() + " but reader computed " + computed
        );
      }
    }

    @Override
    public V1 withChecksum(ChecksumAlgorithm algorithm) {
      Objects.requireNonNull(algorithm, "algorithm must not be null");
      if (mode == Mode.STREAMING && algorithm != ChecksumAlgorithm.NONE) {
        throw new IllegalStateException(
          "Checksum is not supported in STREAMING mode (no terminator to carry the digest). " +
            "Use FRAMED mode for end-to-end integrity, or rely on transport-level integrity."
        );
      }
      return new V1(mode, typeVerification, algorithm, wireConstraints);
    }

    public boolean typeVerificationEnabled() {
      return typeVerification;
    }

    // ============ Envelope orchestration ============

    private void verifyHeader(Header header) {
      if (header.version() != VERSION) {
        throw new IllegalStateException(
          "Protocol version mismatch: expected " + VERSION + " but stream has version " + header.version()
        );
      }
      if (header.mode() != mode) {
        throw new IllegalStateException(
          "Protocol mode mismatch: reader expects " + mode + " but stream is " + header.mode()
        );
      }
      if (header.typeVerification() != typeVerification) {
        throw new IllegalStateException(
          "Reader requires typeVerification=" +
            typeVerification +
            " but stream header has typeVerification=" +
            header.typeVerification()
        );
      }
      if (header.checksum() != checksum) {
        throw new IllegalStateException(
          "Reader requires checksum=" + checksum + " but stream header has checksum=" + header.checksum()
        );
      }
    }
  }
}
