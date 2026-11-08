package dev.zems.lib.value.valueio.format.json;

import dev.zems.lib.value.valueio.AbstractStateWriter;
import dev.zems.lib.value.valueio.Protocol;
import dev.zems.lib.value.valueio.SerializedThrowable;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.Objects;
import java.util.Set;

/**
 * JSON implementation of {@link dev.zems.lib.value.valueio.StateWriter StateWriter}, streaming to a {@link Writer}
 * without buffering the document. FRAMED output is one root object; STREAMING output is one root object per record per
 * line. State markers are objects with a {@code "__state"} field (errors add {@code "__errorClass"} and
 * {@code "__errorMessage"}, unsupported markers a {@code "__reason"}); with type verification on, each record carries
 * a {@code "__type"} field. {@code NaN} and infinities throw {@link IllegalArgumentException} (RFC 8259 cannot encode
 * them). Constructed only through {@code ValueIo}.
 */
public final class JsonStateWriter extends AbstractStateWriter {

  static final String STATE_FIELD = "__state";
  static final String ERROR_CLASS_FIELD = "__errorClass";
  static final String ERROR_MESSAGE_FIELD = "__errorMessage";
  static final String REASON_FIELD = "__reason";
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

  private void writeStateMarker(int id, String name, String state, Runnable extraFields) {
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
    if (extraFields != null) {
      extraFields.run();
    }
    append('}');
    if (topLevelStreaming) {
      append('}');
      commaStack.pop();
    }
  }

  @Override
  protected void doWriteRecordOpen(int id, String name, TypeDescriptor<?> descriptor) {
    if (streaming && recordDepth() == 1 && !isInEnvelope()) {
      append('{');
      commaStack.push(Boolean.FALSE);
    }
    writeCommaIfNeeded();
    writeKey(id, name);
    append('{');
    commaStack.push(Boolean.FALSE);
    if (!isInEnvelope() && protocol instanceof Protocol.V1 v1 && v1.typeVerificationEnabled()) {
      writeCommaIfNeeded();
      writeKey(0, TYPE_FIELD);
      writeJsonString(descriptor.descriptorName() + "@" + descriptor.signature());
    }
  }

  @Override
  protected void doWriteRecordClose(int id, String name, TypeDescriptor<?> descriptor) {
    append('}');
    commaStack.pop();
    if (streaming && recordDepth() == 1 && !isInEnvelope()) {
      append('}');
      commaStack.pop();
    }
  }

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

  @Override
  protected void doWriteTombstone(int id, String name) {
    writeStateMarker(id, name, "TOMBSTONE", null);
  }

  @Override
  protected void doWriteError(int id, String name, Throwable throwable) {
    writeStateMarker(id, name, "ERROR", () -> writeErrorFields(throwable));
  }

  @Override
  protected void doWriteUnsupported(int id, String name, String reason) {
    writeStateMarker(id, name, "UNSUPPORTED", () -> writeStringField(REASON_FIELD, reason));
  }

  private void writeErrorFields(Throwable throwable) {
    writeStringField(ERROR_CLASS_FIELD, SerializedThrowable.wireClassName(throwable));
    writeStringField(ERROR_MESSAGE_FIELD, throwable.getMessage() == null ? "" : throwable.getMessage());
  }

  private void writeStringField(String field, String value) {
    append(',');
    writeJsonString(field);
    append(':');
    writeJsonString(value);
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
