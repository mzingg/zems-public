package dev.zems.lib.value;

import dev.zems.lib.value.Unit.Factor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes the unit expression grammar: factors {@code symbol} or {@code symbol^exponent} joined by {@code *}
 * and {@code /}, no spaces and no parentheses; a {@code /} negates the next factor's exponent.
 */
final class UnitExpression {

  private static final char TIMES = '*';
  private static final char PER = '/';
  private static final char POWER = '^';
  private static final char MINUS = '-';
  private static final int MAX_EXPONENT_DIGITS = 10;
  private static final long MAX_EXPONENT_MAGNITUDE = -((long) Integer.MIN_VALUE);

  private UnitExpression() {}

  static Unit read(String expression) {
    Map<UnitAtom, Long> sums = new LinkedHashMap<>();
    int length = expression.length();
    int position = 0;
    boolean divides = false;
    while (true) {
      int symbolStart = position;
      while (position < length && !endsSymbol(expression.charAt(position))) {
        position++;
      }
      if (position == symbolStart) {
        throw malformed(expression);
      }
      UnitAtom atom = UnitCatalogue.atom(expression.substring(symbolStart, position));
      long exponent = 1;
      if (position < length && expression.charAt(position) == POWER) {
        int exponentStart = ++position;
        while (position < length && !isOperator(expression.charAt(position))) {
          position++;
        }
        exponent = exponent(expression.substring(exponentStart, position), expression);
      }
      sums.merge(atom, divides ? -exponent : exponent, Long::sum);
      if (position == length) {
        return normalised(sums, expression);
      }
      if (!isOperator(expression.charAt(position))) {
        throw malformed(expression);
      }
      divides = expression.charAt(position) == PER;
      position++;
    }
  }

  static String write(List<Factor> factors) {
    boolean hasPositive = factors.stream().anyMatch(factor -> factor.exponent() > 0);
    var text = new StringBuilder();
    for (Factor factor : factors) {
      if (factor.exponent() > 0) {
        appendJoined(text, TIMES).append(factor.atom().symbol());
        appendPowerAboveOne(text, factor.exponent());
      }
    }
    for (Factor factor : factors) {
      if (factor.exponent() < 0 && hasPositive) {
        text.append(PER).append(factor.atom().symbol());
        appendPowerAboveOne(text, -((long) factor.exponent()));
      } else if (factor.exponent() < 0) {
        appendJoined(text, TIMES).append(factor.atom().symbol()).append(POWER).append(factor.exponent());
      }
    }
    return text.toString();
  }

  private static StringBuilder appendJoined(StringBuilder text, char separator) {
    return text.isEmpty() ? text : text.append(separator);
  }

  private static void appendPowerAboveOne(StringBuilder text, long exponent) {
    if (exponent > 1) {
      text.append(POWER).append(exponent);
    }
  }

  private static boolean isOperator(char c) {
    return c == TIMES || c == PER;
  }

  private static boolean endsSymbol(char c) {
    return isOperator(c) || c == POWER || Character.isWhitespace(c);
  }

  private static long exponent(String digits, String expression) {
    int start = !digits.isEmpty() && digits.charAt(0) == MINUS ? 1 : 0;
    int count = digits.length() - start;
    if (count == 0 || count > MAX_EXPONENT_DIGITS || digits.charAt(start) == '0') {
      throw malformed(expression);
    }
    for (int i = start; i < digits.length(); i++) {
      if (digits.charAt(i) < '0' || digits.charAt(i) > '9') {
        throw malformed(expression);
      }
    }
    long exponent = Long.parseLong(digits);
    // One past Integer.MAX_VALUE is allowed: the writer spells an exponent of Integer.MIN_VALUE after a '/' as its
    // magnitude, and the sums are range-checked once all factors are read.
    if (Math.abs(exponent) > MAX_EXPONENT_MAGNITUDE) {
      throw malformed(expression);
    }
    return exponent;
  }

  private static Unit normalised(Map<UnitAtom, Long> sums, String expression) {
    Map<UnitAtom, Integer> exponents = new LinkedHashMap<>();
    sums.forEach((atom, sum) -> {
      long total = sum;
      if (total != (int) total) {
        throw malformed(expression);
      }
      exponents.put(atom, (int) total);
    });
    if (
      exponents
        .values()
        .stream()
        .allMatch(exponent -> exponent == 0)
    ) {
      throw malformed(expression);
    }
    return Unit.ofExponents(exponents);
  }

  private static IllegalArgumentException malformed(String expression) {
    return new IllegalArgumentException("malformed unit expression '" + expression + "'");
  }
}
