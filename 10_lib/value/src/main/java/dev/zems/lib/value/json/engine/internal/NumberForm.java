package dev.zems.lib.value.json.engine.internal;

import java.math.BigInteger;
import java.util.Objects;

/**
 * The value of a JSON number text: {@code (negative ? -1 : 1) × digits × 10^exponent}, with {@code digits} free of
 * leading and trailing zeros; a zero has empty digits, exponent 0 and no sign.
 *
 * @param writtenDigits the digit characters of the integer and fraction parts as written
 */
public record NumberForm(boolean negative, String digits, BigInteger exponent, int writtenDigits) {
  /** Reads the value of a JSON number text. */
  public static NumberForm of(String text) {
    boolean negative = text.charAt(0) == '-';
    int integerStart = negative ? 1 : 0;
    int integerEnd = digitsEnd(text, integerStart);
    int fractionStart = integerEnd;
    int fractionEnd = integerEnd;
    if (integerEnd < text.length() && text.charAt(integerEnd) == '.') {
      fractionStart = integerEnd + 1;
      fractionEnd = digitsEnd(text, fractionStart);
    }
    StringBuilder written = new StringBuilder(fractionEnd - integerStart)
      .append(text, integerStart, integerEnd)
      .append(text, fractionStart, fractionEnd);
    int count = written.length();
    int first = 0;
    while (first < count && written.charAt(first) == '0') {
      first++;
    }
    if (first == count) {
      return new NumberForm(false, "", BigInteger.ZERO, count);
    }
    int last = count - 1;
    while (written.charAt(last) == '0') {
      last--;
    }
    BigInteger exponent = writtenExponent(text, fractionEnd).add(
      BigInteger.valueOf((long) (count - 1 - last) - (fractionEnd - fractionStart))
    );
    return new NumberForm(negative, written.substring(first, last + 1), exponent, count);
  }

  /** Answers whether the number is zero. */
  public boolean isZero() {
    return digits.isEmpty();
  }

  /** Answers whether both forms hold the same number, however it was written. */
  public boolean isSameNumber(NumberForm other) {
    return negative == other.negative && digits.equals(other.digits) && exponent.equals(other.exponent);
  }

  /** A hash that equal numbers share, however they were written. */
  public int numberHash() {
    return Objects.hash(negative, digits, exponent);
  }

  private static int digitsEnd(String text, int start) {
    int end = start;
    while (end < text.length() && text.charAt(end) >= '0' && text.charAt(end) <= '9') {
      end++;
    }
    return end;
  }

  private static BigInteger writtenExponent(String text, int mark) {
    return mark < text.length() ? new BigInteger(text.substring(mark + 1)) : BigInteger.ZERO;
  }
}
