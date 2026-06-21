package dev.zems.lib.value.marshal.format.json;

import dev.zems.lib.value.ValueState;
import dev.zems.lib.value.marshal.AbstractStateReader;
import dev.zems.lib.value.marshal.Protocol;
import dev.zems.lib.value.marshal.SerializedThrowable;
import dev.zems.lib.value.marshal.descriptor.TypeDescriptor;
import dev.zems.lib.value.marshal.wire.WireConstraintEnforcer;
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
 * JSON wire-format implementation of {@link dev.zems.lib.value.marshal.StateReader StateReader}. Driven by an
 * incremental {@link JsonStreamParser} — never materialises the entire wire.
 *
 * <p>
 * Descriptors are expected to read fields in the order they were written. Out-of-order reads are tolerated via a
 * per-record buffered-fallback map: the reader drives the parser forward past any fields it doesn't yet need,
 * materialising each value into a small per-record map. Subsequent reads of those names hit the buffered map. Memory is
 * bounded by the size of one record (not the entire stream).
 *
 * <p>
 * {@link AbstractStateReader#peekValueStateOrNull(int, String)} forces materialisation of the candidate value (state
 * markers are nested objects whose first field is {@code __state}). Once materialised, a typed
 * {@link AbstractStateReader#readRecord(int, String, TypeDescriptor)} on the same name reads from the buffered map
 * instead of the parser; from that point inside that record, all reads operate on the materialised tree.
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

  // Streaming wrapper bookkeeping: each top-level streaming record is wrapped in
  // {name:{body}}. The wrapper is opened lazily on doHasMoreRecords / record-open and closed
  // on doConsumeRecordSeparator.
  private boolean wrapperOpen;

  public JsonStateReader(Protocol protocol, Reader reader) {
    super(protocol, SUPPORTED_MODES);
    Objects.requireNonNull(reader, "reader must not be null");
    this.streaming = protocol.mode() == Protocol.Mode.STREAMING;
    this.parser = new JsonStreamParser(reader, protocol.wireConstraints());
    if (!streaming) {
      // Framed: open the outer wrapper now so envelope reads find $header/$terminator inside.
      JsonStreamParser.Token t = parser.nextToken();
      if (t != JsonStreamParser.Token.OBJECT_START) {
        throw new IllegalStateException("Framed JSON must start with '{'; got " + t);
      }
      stack.push(new LiveFrame());
    }
  }

  // ============ Composite records ============

  @Override
  protected void doReadRecordOpen(int id, String name, TypeDescriptor<?> descriptor) {
    String wireKey = JsonWireKeys.effectiveKey(id, name);
    var top = stack.peek();
    if (top instanceof LiveFrame lf) {
      lf.consumed.add(wireKey);
      Map<String, Object> bufferedHit = takeBuffered(lf, wireKey);
      if (bufferedHit != null) {
        // Pre-materialised — switch to BufferedFrame for nested reads.
        validateRecordBody(name, descriptor, bufferedHit);
        stack.push(new BufferedFrame(bufferedHit));
        return;
      }
      // Drive parser forward to find the field, then consume OBJECT_START.
      driveToFieldName(lf, wireKey);
      JsonStreamParser.Token t = parser.nextToken();
      if (t != JsonStreamParser.Token.OBJECT_START) {
        throw new IllegalStateException("Expected '{' for record '" + name + GOT_SUFFIX + t);
      }
      // Validate type-verification (and consume __type if present).
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
    // BufferedFrame: nothing to do — parent's parser state already advanced past the value.
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
    parser.nextToken(); // consume the wrapper open
    stack.push(new LiveFrame());
    wrapperOpen = true;
    return true;
  }

  @Override
  protected void doConsumeRecordSeparator() {
    if (!wrapperOpen) {
      return;
    }
    // Drain wrapper to its OBJECT_END and pop the frame.
    drainToObjectEnd();
    var popped = stack.pop();
    if (!(popped instanceof LiveFrame)) {
      throw new IllegalStateException(
        "Streaming wrapper close: expected LiveFrame, got " + popped.getClass().getSimpleName()
      );
    }
    wrapperOpen = false;
  }

  // ============ Unknown-slot drain ============

  @Override
  protected List<String> doDrainUnknownSlotNames() {
    var top = stack.peek();
    if (top instanceof LiveFrame lf) {
      // Buffered fields the descriptor never consumed are unknown; a peek-resolved state slot stays buffered but is
      // recorded as consumed, so subtracting consumed avoids reporting a handled state slot.
      List<String> names = new ArrayList<>();
      for (String key : lf.buffered.keySet()) {
        if (!lf.consumed.contains(key)) {
          names.add(key);
        }
      }
      lf.buffered.clear();
      // Drive the parser forward up to (but not including) OBJECT_END, capturing any
      // remaining FIELD_NAMEs and skipping their values — these are unread, so unknown.
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
      // A fully-materialised record from an earlier out-of-order read. Each read marks its slot consumed (typed read,
      // record/nested open, or peek-resolved state marker), so whatever remains — bar the __type metadata key, which
      // is not a user slot — is a slot the descriptor never read, i.e. unknown. This makes the out-of-order read agree
      // with the in-order read under UnknownSlotPolicy.FAIL.
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
      // Drain remaining wrapper content (if user didn't read everything) up to OBJECT_END.
      while (!stack.isEmpty()) {
        var frame = stack.pop();
        if (frame instanceof LiveFrame) {
          drainToObjectEnd();
        }
      }
    }
    parser.close();
  }

  // ============ State markers ============

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
  protected ValueState doPeekValueStateOrNull(int id, String name) {
    String wireKey = JsonWireKeys.effectiveKey(id, name);
    var top = stack.peek();
    if (top instanceof BufferedFrame bf) {
      return markStateConsumed(bf, wireKey, detectStateMarkerInMap(bf.body, wireKey));
    }
    if (top instanceof LiveFrame lf) {
      // If already buffered, inspect.
      if (lf.buffered.containsKey(wireKey)) {
        return markStateConsumed(lf, wireKey, detectStateMarkerInMap(lf.buffered, wireKey));
      }
      // Drive parser forward; materialise the value into buffered for either branch.
      driveToFieldName(lf, wireKey);
      Object value = parser.readAnyValue();
      bufferField(lf, wireKey, value);
      return markStateConsumed(lf, wireKey, detectStateMarkerInMap(lf.buffered, wireKey));
    }
    throw new IllegalStateException("Cannot peek value state '" + name + NO_CURRENT_FRAME);
  }

  /**
   * A slot resolved to a state marker is handled by the peek alone — {@code StateReader.read} returns the state Value
   * with no following typed read — so mark it consumed here, otherwise the unknown-slot drain would report a handled
   * null/undefined/tombstone slot as unknown. A {@code null} state means a real value the caller will read next, which
   * the typed read consumes.
   */
  private ValueState markStateConsumed(RecordFrame frame, String wireKey, ValueState state) {
    if (state != null) {
      frame.consumed().add(wireKey);
    }
    return state;
  }

  // ============ Structure ============

  @Override
  protected void doEndNested(int id, String name) {
    var top = stack.pop();
    if (top instanceof LiveFrame) {
      drainToObjectEnd();
    }
  }

  @Override
  protected void doBeginNested(int id, String name) {
    // Same shape as doReadRecordOpen but without type verification semantics.
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

  // ============ Primitives ============

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

  // ============ Field presence ============

  /**
   * Active implementation: returns true if {@code name} can be reached within the current record. For a
   * {@link BufferedFrame} we just consult the body map. For a {@link LiveFrame} we first check the buffered map (fields
   * skipped past during an earlier out-of-order read), then drive the parser forward — buffering each non-matching
   * field — until we either find the target or hit OBJECT_END. Memory cost is bounded by the size of a single record.
   *
   * <p>
   * Required for slot-aliasing: a descriptor that calls {@code readString("name", "fullName")} expects {@code hasField}
   * to actively look ahead for any of the candidate names.
   */
  @Override
  protected boolean doHasField(int id, String name) {
    String wireKey = JsonWireKeys.effectiveKey(id, name);
    return switch (stack.peek()) {
      case BufferedFrame bf -> bf.body.containsKey(wireKey);
      case LiveFrame lf -> {
        if (lf.buffered.containsKey(wireKey)) {
          yield true;
        }
        // Drive forward; buffer non-matching fields; stop at OBJECT_END.
        while (true) {
          JsonStreamParser.Token next = parser.peekToken();
          if (next == JsonStreamParser.Token.OBJECT_END) {
            yield false;
          }
          if (next != JsonStreamParser.Token.FIELD_NAME) {
            // Unexpected — surface to the caller via the next typed read.
            yield false;
          }
          parser.nextToken(); // consume FIELD_NAME
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

  // ============ Internal: parser-driven helpers ============

  /** Drive the parser forward, skipping fields, until OBJECT_END is consumed. */
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
      parser.nextToken(); // consume FIELD_NAME
      parser.skipValue();
    }
  }

  /** If the named entry is buffered as a Map, remove and return it; otherwise null. */
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

  /** When opening a record from a buffered Map, run the type-verification check on the body. */
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

  /**
   * Drive the parser forward through the current LiveFrame, buffering any non-matching field into {@code lf.buffered},
   * until a FIELD_NAME matching {@code name} is consumed. Throws if OBJECT_END is encountered first (without consuming
   * it — caller's drain logic handles that).
   */
  private void driveToFieldName(LiveFrame lf, String name) {
    while (true) {
      JsonStreamParser.Token next = parser.peekToken();
      if (next == JsonStreamParser.Token.OBJECT_END) {
        throw new IllegalStateException(FIELD_PREFIX + name + "' not found in current object");
      }
      if (next != JsonStreamParser.Token.FIELD_NAME) {
        throw new IllegalStateException("Expected FIELD_NAME or '}', got " + next + " (looking for '" + name + "')");
      }
      // consume the field name token
      parser.nextToken();
      String fieldName = parser.stringValue();
      if (fieldName.equals(name)) {
        return;
      }
      // Buffer the value of the skipped field.
      Object value = parser.readAnyValue();
      bufferField(lf, fieldName, value);
    }
  }

  /**
   * Buffers a field skipped past during an out-of-order read, enforcing the duplicate-key policy <b>before</b> it can
   * silently overwrite an earlier occurrence. Every buffering path (typed-read skip, {@code hasField} probe,
   * {@code peekValueStateOrNull} probe) routes through here, so {@link WireConstraints.DuplicateKeyPolicy#FAIL} fires
   * consistently regardless of which read first touched the region.
   */
  private void bufferField(LiveFrame lf, String fieldName, Object value) {
    if (lf.buffered.containsKey(fieldName)) {
      WireConstraintEnforcer.checkDuplicateKey(fieldName, protocol.wireConstraints());
    }
    lf.buffered.put(fieldName, value);
  }

  /**
   * After the wrapper {@code {} } for a typed record body has been opened (LiveFrame just pushed), peek for an optional
   * {@code __type} field and validate when type-verification is enabled.
   */
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

  // Narrowing a materialised number into a fixed-width slot: an integral value that does not fit (e.g. a JSON integer
  // wider than the target) is rejected rather than silently truncated. Floating-point sources keep the existing lossy
  // conversion — integer-precision preservation is what this guards.
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

  /**
   * Parses a {@code "descriptorName@signature"} wire-type string and validates the name against
   * {@link TypeDescriptor#descriptorName()} and {@link TypeDescriptor#nameAliases()}. Returns the parsed signature
   * (empty string if the stream had no {@code @signature} suffix).
   */
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

  /**
   * Look up {@code name} in the current frame's buffered map, otherwise drive the parser forward (for LiveFrame),
   * materialise the value, and return it. The returned value is removed from the buffered map (consumed).
   */
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
      // FIELD_NAME consumed; read the value's tokens directly into a typed result.
      // Materialising is acceptable because the caller asks for a known typed read.
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
      default -> throw new IllegalStateException("Unknown state value '" + s + "' at '" + name + "'");
    };
  }

  // ============ Frame model ============

  private sealed interface RecordFrame permits LiveFrame, BufferedFrame {
    /** Name → already-materialised value, for fields that were skipped past while looking for another. */
    Map<String, Object> buffered();

    /** Wire keys the descriptor consumed (typed read, record/nested open, or peek-resolved state marker). */
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

    /** The fully-materialised record body — driven by the parent's earlier readAnyValue(). */
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
