package dev.zems.lib.value.valueio;

import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import dev.zems.lib.value.valueio.wire.WireConstraints;
import dev.zems.lib.value.valueio.wire.v1.Header;
import dev.zems.lib.value.valueio.wire.v1.Terminator;
import java.util.Objects;

/**
 * Versioned wire envelope (mode, header, terminator, checksum) consumed by the state readers and writers. {@link V1} is
 * the only protocol version; callers reach I/O through {@link ValueIo}, which configures a {@code V1} under the hood.
 */
public sealed interface Protocol permits Protocol.V1 {
  Mode mode();

  default ChecksumAlgorithm checksumAlgorithm() {
    return ChecksumAlgorithm.NONE;
  }

  default WireConstraints wireConstraints() {
    return WireConstraints.SECURE_DEFAULTS;
  }

  void onWriterStart(AbstractStateWriter writer);

  void onWriterClose(AbstractStateWriter writer);

  void onReaderStart(AbstractStateReader reader);

  void onReaderClose(AbstractStateReader reader);

  /**
   * A copy with the given checksum algorithm; throws {@link IllegalStateException} in STREAMING mode, which has no
   * terminator to carry the digest.
   */
  Protocol withChecksum(ChecksumAlgorithm algorithm);

  /** Creates a writer for a custom wire format and starts it (in FRAMED mode this writes the header). */
  default <W extends AbstractStateWriter> W writer(WriterFactory<W> factory) {
    W w = factory.create(this);
    w.start();
    return w;
  }

  /** Creates a reader for a custom wire format and starts it (in FRAMED mode this reads and verifies the header). */
  default <R extends AbstractStateReader> R reader(ReaderFactory<R> factory) {
    R r = factory.create(this);
    r.start();
    return r;
  }

  enum Mode {
    /** Single document: header on open, exactly one top-level record, terminator on close. */
    FRAMED,
    /** Open-ended record sequence: no header or terminator, a separator between top-level records, ends at EOF. */
    STREAMING,
  }

  @FunctionalInterface
  interface WriterFactory<W extends AbstractStateWriter> {
    W create(Protocol protocol);
  }

  @FunctionalInterface
  interface ReaderFactory<R extends AbstractStateReader> {
    R create(Protocol protocol);
  }

  /** Protocol version 1, the only version. */
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

    static V1 framed() {
      return new V1(Mode.FRAMED, false, ChecksumAlgorithm.NONE, WireConstraints.SECURE_DEFAULTS);
    }

    static V1 streaming() {
      return new V1(Mode.STREAMING, false, ChecksumAlgorithm.NONE, WireConstraints.SECURE_DEFAULTS);
    }

    public V1 usingTypeVerification() {
      return new V1(mode, true, checksum, wireConstraints);
    }

    public V1 withoutTypeVerification() {
      return new V1(mode, false, checksum, wireConstraints);
    }

    /** A copy with the given wire-level safety bounds in place of {@link WireConstraints#SECURE_DEFAULTS}. */
    public V1 withWireConstraints(WireConstraints constraints) {
      Objects.requireNonNull(constraints, "wireConstraints must not be null");
      return new V1(mode, typeVerification, checksum, constraints);
    }

    @Override
    public Mode mode() {
      return mode;
    }

    @Override
    public ChecksumAlgorithm checksumAlgorithm() {
      return checksum;
    }

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
