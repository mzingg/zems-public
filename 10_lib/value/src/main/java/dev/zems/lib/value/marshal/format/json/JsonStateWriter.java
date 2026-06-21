package dev.zems.lib.value.marshal.format.json;

import dev.zems.lib.value.marshal.AbstractStateWriter;
import dev.zems.lib.value.marshal.Protocol;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.Objects;
import java.util.Set;

/**
 * JSON wire-format implementation of {@link dev.zems.lib.value.marshal.StateWriter StateWriter}. Streams output
 * incrementally to a {@link Writer} — never accumulates the whole document in memory. With
 * {@link Protocol.V1#typeVerificationEnabled()}, every {@code writeRecord} adds a {@code "__type"} field next to the
 * payload so the reader can verify the descriptor name.
 *
 * <p>
 * State markers are encoded as nested objects with a reserved {@code "__state"} field:
 * <code>&#123;"__state":"NULL"&#125;</code>, etc. Errors carry {@code "__errorClass"} and
 * {@code "__errorMessage"}. The reader counterpart is {@code JsonStateReader.detectStateMarkerInMap(...)}.
 *
 * <h2>Streaming envelope shape</h2>
 *
 * <p>
 * Two modes — same JSON token grammar inside, different outer framing.
 *
 * <p>
 * <b>FRAMED.</b> The constructor emits a single opening <code>&#123;</code> immediately and
 * pushes a frame onto the comma stack. The whole writer's lifetime stays inside that one outer object; records,
 * scalars, and state markers all write as named fields of it. Close emits the matching <code>&#125;</code> after the
 * terminator slot.
 *
 * <p>
 * <b>STREAMING.</b> The constructor does <i>not</i> emit an outer object. Each top-level
 * record is a self-contained JSON object on its own line — JSONL convention. Top-level records and top-level state
 * markers each open their own outer <code>&#123;</code> on entry and close it on exit;
 * {@link #doWriteRecordSeparator()} emits a {@code \n} between them.
 *
 * <p>
 * Three guards work together to manage the streaming outer wrapper. They look related but fire at different depths —
 * there is no single hook on the abstract base that subsumes them:
 *
 * <ul>
 * <li>{@link #JsonStateWriter(Protocol, Writer)} — the FRAMED constructor's
 * <code>append('&#123;')</code> (skipped when {@code streaming}).</li>
 * <li>{@link #doWriteRecordOpen(int, String, TypeDescriptor)} — STREAMING-only:
 * {@code streaming && recordDepth() == 1 && !isInEnvelope()} adds the per-record outer
 * <code>&#123;</code> before the named-slot <code>&#123;</code>.
 * {@link #doWriteRecordClose(int, String, TypeDescriptor)} closes it symmetrically.</li>
 * <li>{@code writeStateMarker} — STREAMING-only:
 * {@code streaming && recordDepth() == 0 && !isInEnvelope()} opens and closes a wrapper for
 * a top-level state marker. State markers are not records, so they never traverse
 * {@code doWriteRecordOpen} — this guard exists precisely to give them their own outer
 * object on the JSONL line.</li>
 * </ul>
 *
 * <p>
 * <b>Worked examples</b> (slot id 0 / name "$payload", descriptor for {@link String}, type
 * verification off):
 *
 * <pre>
 * // Value.of("x"):
 * FRAMED:    &#123;"$header":&#123;...&#125;,"$payload":"x","$terminator":&#123;...&#125;&#125;
 * STREAMING: &#123;"$payload":"x"&#125;\n
 *
 * // Value.nullValue():
 * FRAMED:    &#123;"$header":&#123;...&#125;,"$payload":&#123;"__state":"NULL"&#125;,"$terminator":&#123;...&#125;&#125;
 * STREAMING: &#123;"$payload":&#123;"__state":"NULL"&#125;&#125;\n
 * </pre>
 *
 * <p>
 * Constructed only through {@code ValueIo} terminal factories.
 */
public final class JsonStateWriter extends AbstractStateWriter {

  static final String STATE_FIELD = "__state";
  static final String ERROR_CLASS_FIELD = "__errorClass";
  static final String ERROR_MESSAGE_FIELD = "__errorMessage";
  static final String TYPE_FIELD = "__type";
  private static final Set<Protocol.Mode> SUPPORTED_MODES = Set.of(Protocol.Mode.FRAMED, Protocol.Mode.STREAMING);
  private final Writer out;
  private final Deque<Boolean> commaStack = new ArrayDeque<>();
  private final boolean streaming;
  private boolean rootClosed;

  public JsonStateWriter(Protocol protocol, Writer out) {
    super(protocol, SUPPORTED_MODES);
    this.out = Objects.requireNonNull(out, "out must not be null");
    this.streaming = protocol.mode() == Protocol.Mode.STREAMING;
    if (!streaming) {
      append('{');
      commaStack.push(Boolean.FALSE);
    }
  }

  private void writeStateMarker(int id, String name, String state, Throwable throwable) {
    boolean topLevelStreaming = streaming && recordDepth() == 0 && !isInEnvelope();
    if (topLevelStreaming) {
      append('{');
      commaStack.push(Boolean.FALSE);
    }
    writeCommaIfNeeded();
    writeKey(id, name);
    append('{');
    writeJsonString(STATE_FIELD);
    append(':');
    writeJsonString(state);
    if (throwable != null) {
      append(',');
      writeJsonString(ERROR_CLASS_FIELD);
      append(':');
      writeJsonString(throwable.getClass().getName());
      append(',');
      writeJsonString(ERROR_MESSAGE_FIELD);
      append(':');
      writeJsonString(throwable.getMessage() == null ? "" : throwable.getMessage());
    }
    append('}');
    if (topLevelStreaming) {
      append('}');
      commaStack.pop();
    }
  }

  // ============ Composite records ============

  @Override
  protected void doWriteRecordOpen(int id, String name, TypeDescriptor<?> descriptor) {
    if (streaming && recordDepth() == 1 && !isInEnvelope()) {
      append('{'); // per-record outer object
      commaStack.push(Boolean.FALSE);
    }
    writeCommaIfNeeded();
    writeKey(id, name);
    append('{');
    commaStack.push(Boolean.FALSE);
    if (!isInEnvelope() && protocol instanceof Protocol.V1 v1 && v1.typeVerificationEnabled()) {
      writeCommaIfNeeded();
      // TYPE_FIELD starts with __ — passes through effectiveKey unchanged.
      writeKey(0, TYPE_FIELD);
      // descriptorName@signature — see BinaryStateWriter.doWriteRecordOpen rationale.
      writeJsonString(descriptor.descriptorName() + "@" + descriptor.signature());
    }
  }

  @Override
  protected void doWriteRecordClose(int id, String name, TypeDescriptor<?> descriptor) {
    append('}');
    commaStack.pop();
    if (streaming && recordDepth() == 1 && !isInEnvelope()) {
      append('}'); // close per-record outer object
      commaStack.pop();
    }
  }

  // ============ Lifecycle ============

  @Override
  protected void doClose() {
    if (rootClosed) {
      return;
    }
    if (!streaming) {
      append('}');
      if (!commaStack.isEmpty()) {
        commaStack.pop();
      }
    }
    rootClosed = true;
    flushQuiet();
  }

  @Override
  protected void doWriteRecordSeparator() {
    append('\n');
    flushQuiet();
  }

  // ============ State markers ============

  @Override
  protected void doWriteTombstone(int id, String name) {
    writeStateMarker(id, name, "TOMBSTONE", null);
  }

  @Override
  protected void doWriteError(int id, String name, Throwable throwable) {
    writeStateMarker(id, name, "ERROR", throwable);
  }

  @Override
  protected void doWriteUnresolved(int id, String name) {
    writeStateMarker(id, name, "UNRESOLVED", null);
  }

  @Override
  protected void doWriteUndefined(int id, String name) {
    writeStateMarker(id, name, "UNDEFINED", null);
  }

  @Override
  protected void doWriteNull(int id, String name) {
    writeStateMarker(id, name, "NULL", null);
  }

  // ============ Structure ============

  @Override
  protected void doEndNested(int id, String name) {
    append('}');
    commaStack.pop();
  }

  @Override
  protected void doBeginNested(int id, String name) {
    writeCommaIfNeeded();
    writeKey(id, name);
    append('{');
    commaStack.push(Boolean.FALSE);
  }

  // ============ Primitives ============

  @Override
  protected void doWriteBytes(int id, String name, byte[] value) {
    writeCommaIfNeeded();
    writeKey(id, name);
    writeJsonString(Base64.getEncoder().encodeToString(value));
  }

  @Override
  protected void doWriteString(int id, String name, String value) {
    writeCommaIfNeeded();
    writeKey(id, name);
    writeJsonString(value);
  }

  @Override
  protected void doWriteDouble(int id, String name, double value) {
    writeCommaIfNeeded();
    writeKey(id, name);
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      throw new IllegalArgumentException("JSON does not support NaN or Infinity");
    }
    append(Double.toString(value));
  }

  @Override
  protected void doWriteFloat(int id, String name, float value) {
    writeCommaIfNeeded();
    writeKey(id, name);
    if (Float.isNaN(value) || Float.isInfinite(value)) {
      throw new IllegalArgumentException("JSON does not support NaN or Infinity");
    }
    append(Float.toString(value));
  }

  @Override
  protected void doWriteLong(int id, String name, long value) {
    writeCommaIfNeeded();
    writeKey(id, name);
    append(Long.toString(value));
  }

  @Override
  protected void doWriteInt(int id, String name, int value) {
    writeCommaIfNeeded();
    writeKey(id, name);
    append(Integer.toString(value));
  }

  @Override
  protected void doWriteShort(int id, String name, short value) {
    writeCommaIfNeeded();
    writeKey(id, name);
    append(Short.toString(value));
  }

  @Override
  protected void doWriteChar(int id, String name, char value) {
    writeCommaIfNeeded();
    writeKey(id, name);
    writeJsonString(String.valueOf(value));
  }

  @Override
  protected void doWriteBoolean(int id, String name, boolean value) {
    writeCommaIfNeeded();
    writeKey(id, name);
    append(Boolean.toString(value));
  }

  // ============ Internal ============

  private void writeCommaIfNeeded() {
    if (!commaStack.isEmpty()) {
      if (commaStack.peek()) {
        append(',');
      } else {
        commaStack.pop();
        commaStack.push(Boolean.TRUE);
      }
    }
  }

  /**
   * Writes the JSON key for slot {@code (id, name)}. Reserved names (envelope-level {@code $xxx} and per-record
   * metadata {@code __xxx}) are emitted literally; user slots are rewritten to {@code __slot<id>} so user-supplied
   * names never reach the wire — see {@link JsonWireKeys}.
   */
  private void writeKey(int id, String name) {
    writeJsonString(JsonWireKeys.effectiveKey(id, name));
    append(':');
  }

  private void writeJsonString(String value) {
    append('"');
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      switch (ch) {
        case '"' -> append("\\\"");
        case '\\' -> append("\\\\");
        case '\b' -> append("\\b");
        case '\f' -> append("\\f");
        case '\n' -> append("\\n");
        case '\r' -> append("\\r");
        case '\t' -> append("\\t");
        default -> {
          if (ch < 0x20) {
            append("\\u");
            append(String.format("%04x", (int) ch));
          } else {
            append(ch);
          }
        }
      }
    }
    append('"');
  }

  private void append(char c) {
    try {
      out.write(c);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void append(String s) {
    try {
      out.write(s);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void flushQuiet() {
    try {
      out.flush();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
