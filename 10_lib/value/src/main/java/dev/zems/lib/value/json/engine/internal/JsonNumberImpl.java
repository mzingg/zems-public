package dev.zems.lib.value.json.engine.internal;

import dev.zems.lib.value.json.engine.JsonNumber;
import dev.zems.lib.value.json.engine.JsonValueException;
import java.util.Objects;
import java.util.regex.Pattern;

/** The library's JSON number, held as its text; the conversions read the text, never a lossy binary form. */
public final class JsonNumberImpl implements JsonNumber, JsonSourced {

  private static final Pattern NUMBER = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
  private static final int LONG_DIGITS = 19;
  private static final long EXPONENT_CAP = 1L << 40;

  private final String text;
  private final char[] source;
  private final int offset;

  JsonNumberImpl(String text, char[] source, int offset) {
    this.text = text;
    this.source = source;
    this.offset = offset;
  }

  public static JsonNumber of(int number) {
    return new JsonNumberImpl(Integer.toString(number), null, -1);
  }

  public static JsonNumber of(long number) {
    return new JsonNumberImpl(Long.toString(number), null, -1);
  }

  public static JsonNumber of(double number) {
    if (!Double.isFinite(number)) {
      throw new IllegalArgumentException(JsonMessages.nonFiniteDouble(number));
    }
    return new JsonNumberImpl(Double.toString(number), null, -1);
  }

  public static JsonNumber of(String text) {
    Objects.requireNonNull(text, JsonMessages.TEXT_NULL);
    String number = stripWhitespace(text);
    if (!NUMBER.matcher(number).matches()) {
      throw new IllegalArgumentException(JsonMessages.notANumber(text));
    }
    return new JsonNumberImpl(number, null, -1);
  }

  @Override
  public int asInt() {
    WholeNumber whole = wholeNumber(JsonMessages.INT);
    if (!whole.fitsLong() || whole.value() < Integer.MIN_VALUE || whole.value() > Integer.MAX_VALUE) {
      throw new JsonValueException(JsonMessages.outsideIntRange(this));
    }
    return (int) whole.value();
  }

  @Override
  public long asLong() {
    WholeNumber whole = wholeNumber(JsonMessages.LONG);
    if (!whole.fitsLong()) {
      throw new JsonValueException(JsonMessages.outsideLongRange(this));
    }
    return whole.value();
  }

  @Override
  public double asDouble() {
    double value = Double.parseDouble(text);
    if (Double.isInfinite(value)) {
      throw new JsonValueException(JsonMessages.outsideDoubleRange(this));
    }
    return value;
  }

  @Override
  public String toString() {
    return text;
  }

  @Override
  public char[] source() {
    return source;
  }

  @Override
  public int offset() {
    return offset;
  }

  private static String stripWhitespace(String text) {
    int start = 0;
    int end = text.length();
    while (start < end && JsonParser.isWhitespace(text.charAt(start))) {
      start++;
    }
    while (end > start && JsonParser.isWhitespace(text.charAt(end - 1))) {
      end--;
    }
    return text.substring(start, end);
  }

  // Works on digits and a capped exponent instead of a BigDecimal, whose size follows the exponent: 1e999999999999
  // must answer at once.
  private WholeNumber wholeNumber(String target) {
    boolean negative = text.charAt(0) == '-';
    int integerStart = negative ? 1 : 0;
    int integerEnd = digitsEnd(integerStart);
    int fractionStart = integerEnd;
    int fractionEnd = fractionStart;
    if (fractionStart < text.length() && text.charAt(fractionStart) == '.') {
      fractionStart++;
      fractionEnd = digitsEnd(fractionStart);
    }
    long exponent = exponent(fractionEnd) - (fractionEnd - fractionStart);
    StringBuilder digits = new StringBuilder(fractionEnd - integerStart);
    digits.append(text, integerStart, integerEnd).append(text, fractionStart, fractionEnd);
    int first = 0;
    while (first < digits.length() && digits.charAt(first) == '0') {
      first++;
    }
    if (first == digits.length()) {
      return new WholeNumber(true, 0);
    }
    int last = digits.length() - 1;
    while (digits.charAt(last) == '0') {
      last--;
      exponent++;
    }
    if (exponent < 0) {
      throw new JsonValueException(JsonMessages.notWhole(this, target));
    }
    int significant = last - first + 1;
    if (significant + exponent > LONG_DIGITS) {
      return new WholeNumber(false, 0);
    }
    return accumulate(negative, digits.substring(first, last + 1), (int) exponent);
  }

  private int digitsEnd(int start) {
    int end = start;
    while (end < text.length() && JsonParser.isDigit(text.charAt(end))) {
      end++;
    }
    return end;
  }

  private long exponent(int markIndex) {
    if (markIndex == text.length()) {
      return 0;
    }
    int index = markIndex + 1;
    boolean negative = text.charAt(index) == '-';
    if (negative || text.charAt(index) == '+') {
      index++;
    }
    long exponent = 0;
    for (; index < text.length() && exponent < EXPONENT_CAP; index++) {
      exponent = exponent * 10 + (text.charAt(index) - '0');
    }
    return negative ? -exponent : exponent;
  }

  // Accumulates below zero: the negative long range holds Long.MIN_VALUE, the positive one does not.
  private static WholeNumber accumulate(boolean negative, String significant, int trailingZeros) {
    long value = 0;
    String digits = significant + "0".repeat(trailingZeros);
    for (int index = 0; index < digits.length(); index++) {
      int digit = digits.charAt(index) - '0';
      if (value < (Long.MIN_VALUE + digit) / 10) {
        return new WholeNumber(false, 0);
      }
      value = value * 10 - digit;
    }
    if (negative) {
      return new WholeNumber(true, value);
    }
    if (value == Long.MIN_VALUE) {
      return new WholeNumber(false, 0);
    }
    return new WholeNumber(true, -value);
  }

  private record WholeNumber(boolean fitsLong, long value) {}
}
