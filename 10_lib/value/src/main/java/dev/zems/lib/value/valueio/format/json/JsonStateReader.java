package dev.zems.lib.value.valueio.format.json;

import dev.zems.lib.value.ValueState;
import dev.zems.lib.value.valueio.AbstractStateReader;
import dev.zems.lib.value.valueio.Protocol;
import dev.zems.lib.value.valueio.SerializedThrowable;
import dev.zems.lib.value.valueio.descriptor.TypeDescriptor;
import dev.zems.lib.value.valueio.wire.WireConstraintEnforcer;
import java.io.Reader;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * JSON implementation of {@link dev.zems.lib.value.valueio.StateReader StateReader}, pull-parsing incrementally through
 * {@link JsonStreamParser}. Fields are expected in written order; an out-of-order read buffers the skipped fields of
 * the current record, so memory is bounded by one record. Constructed only through {@code ValueIo}.
 */
public final class JsonStateReader extends AbstractStateReader {

  private static final Set<Protocol.Mode> SUPPORTED_MODES = Set.of(Protocol.Mode.FRAMED, Protocol.Mode.STREAMING);

  private static final String FIELD_PREFIX = "Field '";
  private static final String NO_CURRENT_FRAME = "': no current frame";
  private static final String GOT_SUFFIX = "', got ";
  private static final String OBJECT_KIND = "object";

  private final JsonStreamParser parser;
  private final Deque<RecordFrame> stack = new ArrayDeque<>();
  private final boolean streaming;
  private boolean wrapperOpen;

  public JsonStateReader(Protocol protocol, Reader reader) {
    super(protocol, SUPPORTED_MODES);
    Objects.requireNonNull(reader, "reader must not be null");
    this.streaming = protocol.mode() == Protocol.Mode.STREAMING;
    this.parser = new JsonStreamParser(reader, protocol.wireConstraints());
    if (!streaming) {
      JsonStreamParser.Token t = parser.nextToken();
      if (t != JsonStreamParser.Token.OBJECT_START) {
        throw new IllegalStateException("Framed JSON must start with '{'; got " + t);
      }
      stack.push(new LiveFrame());
    }
  }

  @Override
  protected void doReadRecordOpen(int id, String name, TypeDescriptor<?> descriptor) {
    String wireKey = JsonWireKeys.effectiveKey(id, name);
    var top = stack.peek();
    if (top instanceof LiveFrame lf) {
      lf.consumed.add(wireKey);
      Map<String, Object> bufferedHit = takeBuffered(lf, wireKey);
      if (bufferedHit != null) {
        validateRecordBody(name, descriptor, bufferedHit);
        stack.push(new BufferedFrame(bufferedHit));
        return;
      }
      driveToFieldName(lf, wireKey);
      JsonStreamParser.Token t = parser.nextToken();
      if (t != JsonStreamParser.Token.OBJECT_START) {
        throw new IllegalStateException("Expected '{' for record '" + name + GOT_SUFFIX + t);
      }
      consumeTypeVerificationIfPresent(name, descriptor);
      stack.push(new LiveFrame());
      return;
    }
    if (top instanceof BufferedFrame bf) {
      Object raw = bf.body.get(wireKey);
      if (raw == null && !bf.body.containsKey(wireKey)) {
        throw new IllegalStateException(FIELD_PREFIX + name + "' not found in current object");
      }
      bf.consumed.add(wireKey);
      if (!(raw instanceof Map<?, ?> map)) {
        throw fieldTypeError(name, OBJECT_KIND, raw);
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> innerBody = (Map<String, Object>) map;
      validateRecordBody(name, descriptor, innerBody);
      stack.push(new BufferedFrame(innerBody));
      return;
    }
    throw new IllegalStateException("Cannot open record '" + name + NO_CURRENT_FRAME);
  }

  @Override
  protected void doReadRecordClose(int id, String name, TypeDescriptor<?> descriptor) {
    var top = stack.pop();
    if (top instanceof LiveFrame) {
      drainToObjectEnd();
    }
    popRecordSignature();
  }

  @Override
  protected boolean doHasMoreRecords() {
    if (wrapperOpen) {
      return true;
    }
    JsonStreamParser.Token t = parser.peekToken();
    if (t == JsonStreamParser.Token.EOF) {
      return false;
    }
    if (t != JsonStreamParser.Token.OBJECT_START) {
      throw new IllegalStateException("Streaming JSON: expected '{' or EOF between records, got " + t);
    }
    parser.nextToken();
    stack.push(new LiveFrame());
    wrapperOpen = true;
    return true;
  }

  @Override
  protected void doConsumeRecordSeparator() {
    if (!wrapperOpen) {
      return;
    }
    drainToObjectEnd();
    var popped = stack.pop();
    if (!(popped instanceof LiveFrame)) {
      throw new IllegalStateException(
        "Streaming wrapper close: expected LiveFrame, got " + popped.getClass().getSimpleName()
      );
    }
    wrapperOpen = false;
  }

  @Override
  protected List<String> doDrainUnknownSlotNames() {
    var top = stack.peek();
    if (top instanceof LiveFrame lf) {
      List<String> names = new ArrayList<>();
      for (String key : lf.buffered.keySet()) {
        if (!lf.consumed.contains(key)) {
          names.add(key);
        }
      }
      lf.buffered.clear();
      while (true) {
        JsonStreamParser.Token next = parser.peekToken();
        if (next == JsonStreamParser.Token.OBJECT_END || next == JsonStreamParser.Token.EOF) {
          break;
        }
        if (next != JsonStreamParser.Token.FIELD_NAME) {
          break;
        }
        parser.nextToken();
        names.add(parser.stringValue());
        parser.skipValue();
      }
      return names;
    }
    if (top instanceof BufferedFrame bf) {
      List<String> names = new ArrayList<>();
      for (String key : bf.body.keySet()) {
        if (!key.equals(JsonStateWriter.TYPE_FIELD) && !bf.consumed.contains(key)) {
          names.add(key);
        }
      }
      return names;
    }
    return List.of();
  }

  @Override
  protected void doClose() {
    if (!streaming) {
      while (!stack.isEmpty()) {
        var frame = stack.pop();
        if (frame instanceof LiveFrame) {
          drainToObjectEnd();
        }
      }
    }
    parser.close();
  }

  @Override
  protected Throwable doReadError(int id, String name) {
    var value = lookupOrAdvance(JsonWireKeys.effectiveKey(id, name));
    if (!(value instanceof Map<?, ?> map)) {
      throw fieldTypeError(name, OBJECT_KIND, value);
    }
    @SuppressWarnings("unchecked")
    var inner = (Map<String, Object>) map;
    Object errorClass = inner.get(JsonStateWriter.ERROR_CLASS_FIELD);
    Object errorMessage = inner.get(JsonStateWriter.ERROR_MESSAGE_FIELD);
    String className = errorClass instanceof String s ? s : "java.lang.Throwable";
    String message = errorMessage instanceof String s ? s : "";
    return new SerializedThrowable(className, message);
  }

  @Override
  protected String doReadUnsupported(int id, String name) {
    var value = lookupOrAdvance(JsonWireKeys.effectiveKey(id, name));
    if (!(value instanceof Map<?, ?> map)) {
      throw fieldTypeError(name, OBJECT_KIND, value);
    }
    Object reason = map.get(JsonStateWriter.REASON_FIELD);
    if (!(reason instanceof String text) || text.isBlank()) {
      throw new IllegalStateException("Unsupported marker at '" + name + "' is missing \"__reason\"");
    }
    return text;
  }

  @Override
  protected ValueState doPeekValueStateOrNull(int id, String name) {
    String wireKey = JsonWireKeys.effectiveKey(id, name);
    var top = stack.peek();
    if (top instanceof BufferedFrame bf) {
      return markStateConsumed(bf, wireKey, detectStateMarkerInMap(bf.body, wireKey));
    }
    if (top instanceof LiveFrame lf) {
      if (lf.buffered.containsKey(wireKey)) {
        return markStateConsumed(lf, wireKey, detectStateMarkerInMap(lf.buffered, wireKey));
      }
      driveToFieldName(lf, wireKey);
      Object value = parser.readAnyValue();
      bufferField(lf, wireKey, value);
      return markStateConsumed(lf, wireKey, detectStateMarkerInMap(lf.buffered, wireKey));
    }
    throw new IllegalStateException("Cannot peek value state '" + name + NO_CURRENT_FRAME);
  }

  private ValueState markStateConsumed(RecordFrame frame, String wireKey, ValueState state) {
    if (state != null) {
      frame.consumed().add(wireKey);
    }
    return state;
  }

  @Override
  protected void doEndNested(int id, String name) {
    var top = stack.pop();
    if (top instanceof LiveFrame) {
      drainToObjectEnd();
    }
  }

  @Override
  protected void doBeginNested(int id, String name) {
    String wireKey = JsonWireKeys.effectiveKey(id, name);
    var top = stack.peek();
    if (top instanceof LiveFrame lf) {
      lf.consumed.add(wireKey);
      Map<String, Object> bufferedHit = takeBuffered(lf, wireKey);
      if (bufferedHit != null) {
        stack.push(new BufferedFrame(bufferedHit));
        return;
      }
      driveToFieldName(lf, wireKey);
      JsonStreamParser.Token t = parser.nextToken();
      if (t != JsonStreamParser.Token.OBJECT_START) {
        throw new IllegalStateException("Expected '{' for nested '" + name + GOT_SUFFIX + t);
      }
      stack.push(new LiveFrame());
      return;
    }
    if (top instanceof BufferedFrame bf) {
      bf.consumed.add(wireKey);
      Object raw = bf.body.get(wireKey);
      if (!(raw instanceof Map<?, ?> map)) {
        throw fieldTypeError(name, OBJECT_KIND, raw);
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> innerBody = (Map<String, Object>) map;
      stack.push(new BufferedFrame(innerBody));
      return;
    }
    throw new IllegalStateException("Cannot begin nested '" + name + NO_CURRENT_FRAME);
  }

  @Override
  protected byte[] doReadBytes(int id, String name) {
    var value = lookupOrAdvance(JsonWireKeys.effectiveKey(id, name));
    if (value instanceof String s) {
      return Base64.getDecoder().decode(s);
    }
    throw fieldTypeError(name, "bytes (Base64 string)", value);
  }

  @Override
  protected String doReadString(int id, String name) {
    var value = lookupOrAdvance(JsonWireKeys.effectiveKey(id, name));
    if (value instanceof String s) {
      return s;
    }
    throw fieldTypeError(name, "string", value);
  }

  @Override
  protected double doReadDouble(int id, String name) {
    var value = lookupOrAdvance(JsonWireKeys.effectiveKey(id, name));
    if (value instanceof Number n) {
      return n.doubleValue();
    }
    throw fieldTypeError(name, "double", value);
  }

  @Override
  protected float doReadFloat(int id, String name) {
    var value = lookupOrAdvance(JsonWireKeys.effectiveKey(id, name));
    if (value instanceof Number n) {
      return n.floatValue();
    }
    throw fieldTypeError(name, "float", value);
  }

  @Override
  protected long doReadLong(int id, String name) {
    var value = lookupOrAdvance(JsonWireKeys.effectiveKey(id, name));
    if (value instanceof Number n) {
      return narrowToLong(name, n);
    }
    throw fieldTypeError(name, "long", value);
  }

  @Override
  protected int doReadInt(int id, String name) {
    var value = lookupOrAdvance(JsonWireKeys.effectiveKey(id, name));
    if (value instanceof Number n) {
      return narrowToInt(name, n);
    }
    throw fieldTypeError(name, "int", value);
  }

  @Override
  protected short doReadShort(int id, String name) {
    var value = lookupOrAdvance(JsonWireKeys.effectiveKey(id, name));
    if (value instanceof Number n) {
      return narrowToShort(name, n);
    }
    throw fieldTypeError(name, "short", value);
  }

  @Override
  protected char doReadChar(int id, String name) {
    var value = lookupOrAdvance(JsonWireKeys.effectiveKey(id, name));
    if (value instanceof String s && s.length() == 1) {
      return s.charAt(0);
    }
    throw fieldTypeError(name, "char (single-character string)", value);
  }

  @Override
  protected boolean doReadBoolean(int id, String name) {
    var value = lookupOrAdvance(JsonWireKeys.effectiveKey(id, name));
    if (value instanceof Boolean b) {
      return b;
    }
    throw fieldTypeError(name, "boolean", value);
  }

  @Override
  protected boolean doHasField(int id, String name) {
    String wireKey = JsonWireKeys.effectiveKey(id, name);
    return switch (stack.peek()) {
      case BufferedFrame bf -> bf.body.containsKey(wireKey);
      case LiveFrame lf -> {
        if (lf.buffered.containsKey(wireKey)) {
          yield true;
        }
        while (true) {
          JsonStreamParser.Token next = parser.peekToken();
          if (next == JsonStreamParser.Token.OBJECT_END) {
            yield false;
          }
          if (next != JsonStreamParser.Token.FIELD_NAME) {
            yield false;
          }
          parser.nextToken();
          String fieldName = parser.stringValue();
          Object value = parser.readAnyValue();
          bufferField(lf, fieldName, value);
          if (fieldName.equals(wireKey)) {
            yield true;
          }
        }
      }
      case null -> false;
    };
  }

  private void drainToObjectEnd() {
    while (true) {
      JsonStreamParser.Token next = parser.peekToken();
      if (next == JsonStreamParser.Token.OBJECT_END) {
        parser.nextToken();
        return;
      }
      if (next == JsonStreamParser.Token.EOF) {
        throw new IllegalStateException("Unexpected EOF while draining record body");
      }
      if (next != JsonStreamParser.Token.FIELD_NAME) {
        throw new IllegalStateException("Expected FIELD_NAME or '}', got " + next);
      }
      parser.nextToken();
      parser.skipValue();
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> takeBuffered(LiveFrame lf, String name) {
    if (!lf.buffered.containsKey(name)) {
      return null;
    }
    Object raw = lf.buffered.remove(name);
    if (!(raw instanceof Map<?, ?> map)) {
      throw fieldTypeError(name, OBJECT_KIND, raw);
    }
    return (Map<String, Object>) map;
  }

  private void validateRecordBody(String name, TypeDescriptor<?> descriptor, Map<String, Object> body) {
    if (body.containsKey(JsonStateWriter.STATE_FIELD)) {
      throw new IllegalStateException(
        "readRecord at '" + name + "' but slot is a state marker; use peekValueStateOrNull first"
      );
    }
    if (isInEnvelope() || !(protocol instanceof Protocol.V1 v1) || !v1.typeVerificationEnabled()) {
      pushRecordSignature("");
      return;
    }
    Object wireType = body.get(JsonStateWriter.TYPE_FIELD);
    if (!(wireType instanceof String wireTypeStr)) {
      throw new IllegalStateException(
        "Type verification: expected " + JsonStateWriter.TYPE_FIELD + " field at '" + name + "' but found " + wireType
      );
    }
    pushRecordSignature(validateWireType(name, descriptor, wireTypeStr));
  }

  private void driveToFieldName(LiveFrame lf, String name) {
    while (true) {
      JsonStreamParser.Token next = parser.peekToken();
      if (next == JsonStreamParser.Token.OBJECT_END) {
        throw new IllegalStateException(FIELD_PREFIX + name + "' not found in current object");
      }
      if (next != JsonStreamParser.Token.FIELD_NAME) {
        throw new IllegalStateException("Expected FIELD_NAME or '}', got " + next + " (looking for '" + name + "')");
      }
      parser.nextToken();
      String fieldName = parser.stringValue();
      if (fieldName.equals(name)) {
        return;
      }
      Object value = parser.readAnyValue();
      bufferField(lf, fieldName, value);
    }
  }

  private void bufferField(LiveFrame lf, String fieldName, Object value) {
    if (lf.buffered.containsKey(fieldName)) {
      WireConstraintEnforcer.checkDuplicateKey(fieldName, protocol.wireConstraints());
    }
    lf.buffered.put(fieldName, value);
  }

  private void consumeTypeVerificationIfPresent(String name, TypeDescriptor<?> descriptor) {
    if (isInEnvelope() || !(protocol instanceof Protocol.V1 v1) || !v1.typeVerificationEnabled()) {
      pushRecordSignature("");
      return;
    }
    JsonStreamParser.Token next = parser.peekToken();
    if (next != JsonStreamParser.Token.FIELD_NAME) {
      throw new IllegalStateException(
        "Type verification: expected '" + JsonStateWriter.TYPE_FIELD + "' field at '" + name + GOT_SUFFIX + next
      );
    }
    parser.nextToken();
    String first = parser.stringValue();
    if (!JsonStateWriter.TYPE_FIELD.equals(first)) {
      throw new IllegalStateException(
        "Type verification: expected '" +
          JsonStateWriter.TYPE_FIELD +
          "' as first field at '" +
          name +
          "', got '" +
          first +
          "'"
      );
    }
    JsonStreamParser.Token vt = parser.nextToken();
    if (vt != JsonStreamParser.Token.STRING) {
      throw new IllegalStateException(
        "Type verification: expected STRING value for '" + JsonStateWriter.TYPE_FIELD + GOT_SUFFIX + vt
      );
    }
    String wireType = parser.stringValue();
    pushRecordSignature(validateWireType(name, descriptor, wireType));
  }

  private IllegalStateException fieldTypeError(String name, String expectedType, Object actual) {
    var actualType = actual == null ? "null" : actual.getClass().getSimpleName();
    return new IllegalStateException(FIELD_PREFIX + name + "': expected " + expectedType + " but found " + actualType);
  }

  private static boolean isIntegralNumber(Number n) {
    return (
      n instanceof Long || n instanceof Integer || n instanceof Short || n instanceof Byte || n instanceof BigInteger
    );
  }

  private long narrowToLong(String name, Number n) {
    if (n instanceof BigInteger bi) {
      try {
        return bi.longValueExact();
      } catch (ArithmeticException e) {
        throw numericOverflow(name, "long", bi);
      }
    }
    return n.longValue();
  }

  private int narrowToInt(String name, Number n) {
    if (n instanceof BigInteger bi) {
      try {
        return bi.intValueExact();
      } catch (ArithmeticException e) {
        throw numericOverflow(name, "int", bi);
      }
    }
    if (isIntegralNumber(n)) {
      long l = n.longValue();
      if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
        throw numericOverflow(name, "int", n);
      }
      return (int) l;
    }
    return n.intValue();
  }

  private short narrowToShort(String name, Number n) {
    if (n instanceof BigInteger bi) {
      try {
        return bi.shortValueExact();
      } catch (ArithmeticException e) {
        throw numericOverflow(name, "short", bi);
      }
    }
    if (isIntegralNumber(n)) {
      long l = n.longValue();
      if (l < Short.MIN_VALUE || l > Short.MAX_VALUE) {
        throw numericOverflow(name, "short", n);
      }
      return (short) l;
    }
    return n.shortValue();
  }

  private IllegalStateException numericOverflow(String name, String targetType, Number actual) {
    return new IllegalStateException(
      FIELD_PREFIX + name + "': numeric value " + actual + " does not fit " + targetType
    );
  }

  private String validateWireType(String name, TypeDescriptor<?> descriptor, String wireType) {
    int at = wireType.indexOf('@');
    String wireName = at < 0 ? wireType : wireType.substring(0, at);
    String wireSignature = at < 0 ? "" : wireType.substring(at + 1);
    if (!wireName.equals(descriptor.descriptorName()) && !descriptor.nameAliases().contains(wireName)) {
      throw new IllegalStateException(
        "Type mismatch at '" +
          name +
          "': expected " +
          descriptor.descriptorName() +
          (descriptor.nameAliases().isEmpty() ? "" : " (or aliases " + descriptor.nameAliases() + ")") +
          " but stream contained " +
          wireName
      );
    }
    return wireSignature;
  }

  private Object lookupOrAdvance(String name) {
    var top = stack.peek();
    if (top instanceof BufferedFrame bf) {
      if (!bf.body.containsKey(name)) {
        throw new IllegalStateException(FIELD_PREFIX + name + "' not found in current object");
      }
      bf.consumed.add(name);
      return bf.body.get(name);
    }
    if (top instanceof LiveFrame lf) {
      lf.consumed.add(name);
      if (lf.buffered.containsKey(name)) {
        return lf.buffered.remove(name);
      }
      driveToFieldName(lf, name);
      return parser.readAnyValue();
    }
    throw new IllegalStateException("Cannot read '" + name + NO_CURRENT_FRAME);
  }

  @SuppressWarnings("unchecked")
  private ValueState detectStateMarkerInMap(Map<String, Object> map, String name) {
    if (!map.containsKey(name)) {
      return null;
    }
    Object value = map.get(name);
    if (!(value instanceof Map<?, ?> inner)) {
      return null;
    }
    Object stateValue = ((Map<String, Object>) inner).get(JsonStateWriter.STATE_FIELD);
    if (!(stateValue instanceof String s)) {
      return null;
    }
    return switch (s) {
      case "NULL" -> ValueState.NULL;
      case "UNDEFINED" -> ValueState.UNDEFINED;
      case "UNRESOLVED" -> ValueState.UNRESOLVED;
      case "ERROR" -> ValueState.ERROR;
      case "TOMBSTONE" -> ValueState.TOMBSTONE;
      case "UNSUPPORTED" -> ValueState.UNSUPPORTED;
      default -> throw new IllegalStateException("Unknown state value '" + s + "' at '" + name + "'");
    };
  }

  private sealed interface RecordFrame permits LiveFrame, BufferedFrame {
    Map<String, Object> buffered();

    Set<String> consumed();
  }

  private static final class LiveFrame implements RecordFrame {

    private final Map<String, Object> buffered = new LinkedHashMap<>();
    private final Set<String> consumed = new HashSet<>();

    @Override
    public Map<String, Object> buffered() {
      return buffered;
    }

    @Override
    public Set<String> consumed() {
      return consumed;
    }
  }

  private static final class BufferedFrame implements RecordFrame {

    private final Map<String, Object> body;
    private final Set<String> consumed = new HashSet<>();

    BufferedFrame(Map<String, Object> body) {
      this.body = body;
    }

    @Override
    public Map<String, Object> buffered() {
      return body;
    }

    @Override
    public Set<String> consumed() {
      return consumed;
    }
  }
}
