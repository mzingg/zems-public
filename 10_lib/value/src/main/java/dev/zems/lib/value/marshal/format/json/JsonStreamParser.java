package dev.zems.lib.value.marshal.format.json;

import dev.zems.lib.value.marshal.wire.WireConstraintEnforcer;
import dev.zems.lib.value.marshal.wire.WireConstraints;
import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Incremental pull-based JSON parser. Yields one event at a time, never materialising the whole document.
 *
 * <p>
 * Use {@link #peekToken()} for one-token lookahead and {@link #nextToken()} to advance. After a
 * {@code STRING}/{@code FIELD_NAME} token, call {@link #stringValue()} for the text. To consume a whole value, call
 * {@link #readAnyValue()} — it materialises the next value (scalar, object, or array) into a Java tree of
 * {@link Map}/{@link List}/{@code String}/{@code Long}/{@code Double}/{@code Boolean}/{@code null} — or
 * {@link #skipValue()} to discard it. {@link JsonStateReader} drives the parser this way; scalars arrive boxed from
 * {@link #readAnyValue()}, not through per-type accessors.
 *
 * <p>
 * Strict JSON only — no comments, no trailing commas, no NaN/Infinity, no leading zeros in numbers. UTF-16 surrogate
 * pairs in {@code \\u} escapes are honoured.
 *
 * <p>
 * {@link #readAnyValue()} materialises nested objects/arrays by recursion, so nesting is bounded by
 * {@code maxNestingDepth} (checked on every container open). Under {@link WireConstraints#UNCHECKED} that bound is
 * effectively unlimited, so a pathologically nested document is then bounded only by the thread's stack size and may
 * raise a {@code StackOverflowError} — the documented cost of opting out of the safety bounds.
 *
 * <p>
 * Package-private — the public reader entry point is {@link JsonStateReader}.
 */
final class JsonStreamParser implements Closeable {

  private static final int DEFAULT_BUFFER_CHARS = 4096;
  private final Reader reader;
  private final WireConstraints constraints;
  private final char[] buf;
  private final Deque<Frame> stack = new ArrayDeque<>();
  private int bufPos;
  private int bufLimit;
  private boolean eofReached;
  private long charPosition; // diagnostics — chars consumed since start
  // Most-recent token (after nextToken). Valid until the next nextToken/peekToken changes it.
  private Token currentToken;
  private String currentString;
  private String currentNumber;
  private boolean currentIsIntegral;
  private boolean currentBoolean;
  // One-token lookahead.
  private boolean hasPeeked;
  private Token peekedToken;
  private String peekedString;
  private String peekedNumber;
  private boolean peekedIsIntegral;
  private boolean peekedBoolean;

  JsonStreamParser(Reader reader, WireConstraints constraints) {
    this(reader, constraints, DEFAULT_BUFFER_CHARS);
  }

  JsonStreamParser(Reader reader, WireConstraints constraints, int bufferChars) {
    this.reader = Objects.requireNonNull(reader, "reader must not be null");
    this.constraints = Objects.requireNonNull(constraints, "constraints must not be null");
    if (bufferChars < 64) {
      throw new IllegalArgumentException("bufferChars must be at least 64, got " + bufferChars);
    }
    this.buf = new char[bufferChars];
  }

  /** Renders a 16-bit code unit as the JSON escape form {@code \\uHHHH} (lowercase hex). */
  private static String hexEscape(int codeUnit) {
    return "\\u" + String.format("%04x", codeUnit);
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private static Object tryLong(String slice) {
    try {
      return Long.valueOf(slice);
    } catch (NumberFormatException e) {
      // Integral token wider than long — keep the exact value as BigInteger rather than losing
      // precision to a Double. Narrowing into a fixed-width slot is range-checked by the reader.
      return new BigInteger(slice);
    }
  }

  WireConstraints constraints() {
    return constraints;
  }

  private void checkDepth(int newDepth) {
    WireConstraintEnforcer.checkDepth(newDepth, constraints);
  }

  // ============ Public API ============

  /** One-token lookahead. Idempotent until {@link #nextToken()} consumes it. */
  public Token peekToken() {
    if (!hasPeeked) {
      readTokenIntoPeeked();
      hasPeeked = true;
    }
    return peekedToken;
  }

  /** Advance one token. Returns {@link Token#EOF} once the input is exhausted. */
  public Token nextToken() {
    if (hasPeeked) {
      promotePeekedToCurrent();
    } else {
      readTokenIntoCurrent();
    }
    return currentToken;
  }

  /** Valid after STRING or FIELD_NAME. */
  public String stringValue() {
    return currentString;
  }

  /** Current nesting depth. 0 = outside any object/array. */
  public int depth() {
    return stack.size();
  }

  /**
   * Consumes a complete value at the current position — primitive, object, or array — including all nested content.
   * After the call, the token cursor is positioned just past the value. Requires that the next token is a
   * value-starting token (STRING/NUMBER/BOOLEAN/NULL/ OBJECT_START/ARRAY_START).
   */
  public void skipValue() {
    Token t = nextToken();
    switch (t) {
      case STRING, NUMBER, BOOLEAN, NULL -> {
        // already consumed
      }
      case OBJECT_START -> {
        int targetDepth = depth() - 1;
        while (depth() > targetDepth) {
          Token next = nextToken();
          if (next == Token.EOF) {
            throw error("Unexpected EOF inside object");
          }
        }
      }
      case ARRAY_START -> {
        int targetDepth = depth() - 1;
        while (depth() > targetDepth) {
          Token next = nextToken();
          if (next == Token.EOF) {
            throw error("Unexpected EOF inside array");
          }
        }
      }
      default -> throw error("skipValue() at unexpected token " + t);
    }
  }

  /**
   * Materialises the next value as a Java tree of {@link Map}/{@link List}/{@link String}/
   * {@link Long}/{@link Double}/{@link Boolean}/{@code null}. Used by {@link JsonStateReader} to buffer fields skipped
   * during out-of-order reads.
   */
  public Object readAnyValue() {
    Token t = nextToken();
    return materialiseValue(t);
  }

  @Override
  public void close() {
    try {
      reader.close();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // ============ Internal: token machine ============

  private void readTokenIntoCurrent() {
    PendingToken p = readToken();
    currentToken = p.token;
    currentString = p.stringValue;
    currentNumber = p.numberSlice;
    currentIsIntegral = p.isIntegral;
    currentBoolean = p.booleanValue;
  }

  private void readTokenIntoPeeked() {
    PendingToken p = readToken();
    peekedToken = p.token;
    peekedString = p.stringValue;
    peekedNumber = p.numberSlice;
    peekedIsIntegral = p.isIntegral;
    peekedBoolean = p.booleanValue;
  }

  private void promotePeekedToCurrent() {
    currentToken = peekedToken;
    currentString = peekedString;
    currentNumber = peekedNumber;
    currentIsIntegral = peekedIsIntegral;
    currentBoolean = peekedBoolean;
    hasPeeked = false;
    peekedToken = null;
    peekedString = null;
    peekedNumber = null;
  }

  private PendingToken readToken() {
    skipWhitespace();
    if (atEof()) {
      if (!stack.isEmpty()) {
        throw error("Unexpected EOF inside container (depth=" + stack.size() + ")");
      }
      return PendingToken.simple(Token.EOF);
    }
    if (!stack.isEmpty()) {
      Frame top = stack.peek();
      if (top.isObject) {
        if (top.afterKey) {
          // expecting value
          PendingToken p = readValueToken();
          recordValueProgress(top, p.token);
          return p;
        }
        // expecting field-name (or comma/end)
        consumeSeparatorIfNeeded(top, '}');
        char c = peekChar();
        if (c == '}') {
          if (top.afterComma) {
            throw error("Trailing comma before '}'");
          }
          consumeChar();
          stack.pop();
          recordContainerEndOnParent();
          return PendingToken.simple(Token.OBJECT_END);
        }
        if (c != '"') {
          throw error("Expected '\"' for field name, got '" + c + "'");
        }
        String name = parseStringLiteral();
        skipWhitespace();
        if (atEof() || peekChar() != ':') {
          throw error("Expected ':' after field name '" + name + "'");
        }
        consumeChar();
        top.afterKey = true;
        top.afterComma = false;
        return PendingToken.string(Token.FIELD_NAME, name);
      }
      // array
      consumeSeparatorIfNeeded(top, ']');
      char c = peekChar();
      if (c == ']') {
        if (top.afterComma) {
          throw error("Trailing comma before ']'");
        }
        consumeChar();
        stack.pop();
        recordContainerEndOnParent();
        return PendingToken.simple(Token.ARRAY_END);
      }
      PendingToken p = readValueToken();
      recordValueProgress(top, p.token);
      top.afterComma = false;
      return p;
    }
    // top level
    return readValueToken();
  }

  /** Reads a value at the current position. Caller is responsible for parent-frame bookkeeping. */
  private PendingToken readValueToken() {
    char c = peekChar();
    return switch (c) {
      case '{' -> {
        consumeChar();
        checkDepth(stack.size() + 1);
        stack.push(new Frame(true));
        yield PendingToken.simple(Token.OBJECT_START);
      }
      case '[' -> {
        consumeChar();
        checkDepth(stack.size() + 1);
        stack.push(new Frame(false));
        yield PendingToken.simple(Token.ARRAY_START);
      }
      case '"' -> PendingToken.string(Token.STRING, parseStringLiteral());
      case 't' -> {
        expectLiteral("true");
        yield PendingToken.bool(true);
      }
      case 'f' -> {
        expectLiteral("false");
        yield PendingToken.bool(false);
      }
      case 'n' -> {
        expectLiteral("null");
        yield PendingToken.simple(Token.NULL);
      }
      default -> {
        if (c == '-' || (c >= '0' && c <= '9')) {
          yield parseNumber();
        }
        throw error("Unexpected character '" + c + "' at value position");
      }
    };
  }

  /**
   * Updates the parent frame after a value-completing token (scalar or container-end). If the token starts a new
   * container, parent state is unchanged — the close will eventually call {@link #recordContainerEndOnParent()} which
   * flips the bookkeeping then.
   */
  private void recordValueProgress(Frame top, Token t) {
    if (t == Token.OBJECT_START || t == Token.ARRAY_START) {
      // The value has started but not completed; defer.
      return;
    }
    if (top.isObject) {
      top.afterKey = false;
    }
    top.atStart = false;
  }

  /** After popping a container, advance the parent's bookkeeping as if a value just completed. */
  private void recordContainerEndOnParent() {
    if (stack.isEmpty()) {
      return;
    }
    Frame parent = stack.peek();
    if (parent.isObject) {
      parent.afterKey = false;
    }
    parent.atStart = false;
  }

  /**
   * Between elements in a container, consume the comma if {@code !atStart}. If the next char is the container-end,
   * leave it alone (caller handles the close case). On any other char in non-start state, throw.
   */
  private void consumeSeparatorIfNeeded(Frame top, char endChar) {
    if (top.atStart || top.afterComma) {
      return;
    }
    char c = peekChar();
    if (c == ',') {
      consumeChar();
      skipWhitespace();
      top.afterComma = true;
    } else if (c != endChar) {
      throw error("Expected ',' or '" + endChar + "', got '" + c + "'");
    }
  }

  // ============ Internal: lexical primitives ============

  private void skipWhitespace() {
    while (true) {
      while (bufPos < bufLimit) {
        char c = buf[bufPos];
        if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
          bufPos++;
          charPosition++;
        } else {
          return;
        }
      }
      if (!fillBuffer()) {
        return;
      }
    }
  }

  private boolean atEof() {
    return bufPos >= bufLimit && !fillBuffer();
  }

  private char peekChar() {
    if (bufPos >= bufLimit && !fillBuffer()) {
      throw error("Unexpected EOF");
    }
    return buf[bufPos];
  }

  private char consumeChar() {
    if (bufPos >= bufLimit && !fillBuffer()) {
      throw error("Unexpected EOF");
    }
    char c = buf[bufPos++];
    charPosition++;
    return c;
  }

  /**
   * Refill the buffer with a single read. Preserves any unread content at the start (compaction). Returns true iff the
   * buffer has at least one char available after the call.
   */
  private boolean fillBuffer() {
    if (eofReached) {
      return bufPos < bufLimit;
    }
    if (bufPos > 0) {
      if (bufPos < bufLimit) {
        System.arraycopy(buf, bufPos, buf, 0, bufLimit - bufPos);
      }
      bufLimit -= bufPos;
      bufPos = 0;
    }
    int r;
    try {
      r = reader.read(buf, bufLimit, buf.length - bufLimit);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    if (r < 0) {
      eofReached = true;
    } else {
      bufLimit += r;
    }
    return bufPos < bufLimit;
  }

  private void expectLiteral(String literal) {
    for (int i = 0; i < literal.length(); i++) {
      if (atEof()) {
        throw error("Truncated literal '" + literal + "'");
      }
      char c = consumeChar();
      if (c != literal.charAt(i)) {
        throw error("Expected '" + literal + "', mismatched at offset " + i + " ('" + c + "')");
      }
    }
  }

  private String parseStringLiteral() {
    if (consumeChar() != '"') {
      throw error("Expected '\"' to start string literal");
    }
    StringBuilder sb = new StringBuilder();
    while (true) {
      if (atEof()) {
        throw error("Unterminated string");
      }
      char c = consumeChar();
      if (c == '"') {
        return sb.toString();
      }
      if (c == '\\') {
        sb.append(parseEscape());
      } else if (c < 0x20) {
        throw error("Unescaped control character 0x" + Integer.toHexString(c) + " in string");
      } else {
        sb.append(c);
      }
      WireConstraintEnforcer.checkStringLength(sb.length(), constraints);
    }
  }

  private CharSequence parseEscape() {
    if (atEof()) {
      throw error("Unterminated string escape");
    }
    char esc = consumeChar();
    return switch (esc) {
      case '"' -> "\"";
      case '\\' -> "\\";
      case '/' -> "/";
      case 'b' -> "\b";
      case 'f' -> "\f";
      case 'n' -> "\n";
      case 'r' -> "\r";
      case 't' -> "\t";
      case 'u' -> parseUnicodeEscape();
      default -> throw error("Invalid escape character: \\" + esc);
    };
  }

  private CharSequence parseUnicodeEscape() {
    char hi = parseHex4();
    if (Character.isHighSurrogate(hi)) {
      if (atEof() || peekChar() != '\\') {
        throw error("High surrogate " + hexEscape(hi) + " not followed by low surrogate");
      }
      consumeChar(); // backslash
      if (atEof() || consumeChar() != 'u') {
        throw error("High surrogate " + hexEscape(hi) + " not followed by \\u low surrogate");
      }
      char lo = parseHex4();
      if (!Character.isLowSurrogate(lo)) {
        throw error("Invalid low surrogate " + hexEscape(lo));
      }
      return new String(new char[] { hi, lo });
    }
    if (Character.isLowSurrogate(hi)) {
      throw error("Unexpected low surrogate " + hexEscape(hi) + " without preceding high surrogate");
    }
    return Character.toString(hi);
  }

  private char parseHex4() {
    int v = 0;
    for (int i = 0; i < 4; i++) {
      if (atEof()) {
        throw error("Truncated unicode escape");
      }
      char c = consumeChar();
      int digit;
      if (c >= '0' && c <= '9') {
        digit = c - '0';
      } else if (c >= 'a' && c <= 'f') {
        digit = c - 'a' + 10;
      } else if (c >= 'A' && c <= 'F') {
        digit = c - 'A' + 10;
      } else {
        throw error("Invalid hex digit in unicode escape: '" + c + "'");
      }
      v = (v << 4) | digit;
    }
    return (char) v;
  }

  private PendingToken parseNumber() {
    StringBuilder sb = new StringBuilder();
    boolean hasFraction = false;
    boolean hasExponent = false;
    if (peekChar() == '-') {
      sb.append(consumeChar());
    }
    if (atEof() || !isDigit(peekChar())) {
      throw error("Invalid number: expected digit");
    }
    int intStart = sb.length();
    while (!atEof() && isDigit(peekChar())) {
      sb.append(consumeChar());
    }
    // RFC 8259: the integer part is a single '0' or a nonzero digit followed by digits — no leading zeros.
    if (sb.length() - intStart > 1 && sb.charAt(intStart) == '0') {
      throw error("Invalid number: leading zeros are not allowed");
    }
    if (!atEof() && peekChar() == '.') {
      hasFraction = true;
      sb.append(consumeChar());
      if (atEof() || !isDigit(peekChar())) {
        throw error("Invalid number: expected digit after '.'");
      }
      while (!atEof() && isDigit(peekChar())) {
        sb.append(consumeChar());
      }
    }
    if (!atEof()) {
      char c = peekChar();
      if (c == 'e' || c == 'E') {
        hasExponent = true;
        sb.append(consumeChar());
        if (!atEof()) {
          char sign = peekChar();
          if (sign == '+' || sign == '-') {
            sb.append(consumeChar());
          }
        }
        if (atEof() || !isDigit(peekChar())) {
          throw error("Invalid number: expected digit in exponent");
        }
        while (!atEof() && isDigit(peekChar())) {
          sb.append(consumeChar());
        }
      }
    }
    String slice = sb.toString();
    WireConstraintEnforcer.checkNumberLength(slice.length(), constraints);
    return PendingToken.number(slice, !hasFraction && !hasExponent);
  }

  private IllegalStateException error(String message) {
    return new IllegalStateException(message + " at char position " + charPosition);
  }

  // ============ Generic value materialiser (for buffered out-of-order reads) ============

  private Object materialiseValue(Token t) {
    return switch (t) {
      case STRING -> currentString;
      case NUMBER -> currentIsIntegral ? tryLong(currentNumber) : Double.valueOf(currentNumber);
      case BOOLEAN -> currentBoolean;
      case NULL -> null;
      case OBJECT_START -> materialiseObject();
      case ARRAY_START -> materialiseArray();
      default -> throw error("Cannot materialise value at token " + t);
    };
  }

  private Map<String, Object> materialiseObject() {
    Map<String, Object> out = new LinkedHashMap<>();
    while (true) {
      Token t = nextToken();
      if (t == Token.OBJECT_END) {
        return out;
      }
      if (t != Token.FIELD_NAME) {
        throw error("Expected FIELD_NAME or OBJECT_END, got " + t);
      }
      String name = currentString;
      Object value = readAnyValue();
      if (out.containsKey(name)) {
        WireConstraintEnforcer.checkDuplicateKey(name, constraints);
      }
      WireConstraintEnforcer.checkMapEntries(out.size() + 1, constraints);
      out.put(name, value);
    }
  }

  private List<Object> materialiseArray() {
    List<Object> out = new ArrayList<>();
    while (true) {
      Token t = peekToken();
      if (t == Token.ARRAY_END) {
        nextToken(); // consume
        return out;
      }
      Object element = readAnyValue();
      WireConstraintEnforcer.checkArrayLength(out.size() + 1, constraints);
      out.add(element);
    }
  }

  enum Token {
    OBJECT_START,
    OBJECT_END,
    ARRAY_START,
    ARRAY_END,
    FIELD_NAME,
    STRING,
    NUMBER,
    BOOLEAN,
    NULL,
    EOF,
  }

  private static final class Frame {

    final boolean isObject;
    boolean atStart = true; // no element yet seen in this container
    boolean afterKey = false; // (objects only) expecting value for the key just consumed
    boolean afterComma = false; // just consumed a separator — next must be value/key, NOT close

    Frame(boolean isObject) {
      this.isObject = isObject;
    }
  }

  private record PendingToken(
    Token token,
    String stringValue,
    String numberSlice,
    boolean isIntegral,
    boolean booleanValue
  ) {
    static PendingToken simple(Token t) {
      return new PendingToken(t, null, null, false, false);
    }

    static PendingToken string(Token t, String value) {
      return new PendingToken(t, value, null, false, false);
    }

    static PendingToken number(String slice, boolean integral) {
      return new PendingToken(Token.NUMBER, null, slice, integral, false);
    }

    static PendingToken bool(boolean value) {
      return new PendingToken(Token.BOOLEAN, null, null, false, value);
    }
  }
}
