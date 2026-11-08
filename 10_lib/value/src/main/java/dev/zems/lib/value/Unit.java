package dev.zems.lib.value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.LongBinaryOperator;

/**
 * A catalogue unit, or a product of catalogue units each with a non-zero integer exponent ({@code m/s},
 * {@code kg*m/s^2}, {@code EUR/kg}). Obtain one from {@link Units} and combine through the typed operations; units are
 * never parsed from caller strings. Two units are equal when their factors are, whatever the construction order, and
 * {@link #toString()} is the one canonical spelling.
 */
public final class Unit {

  private final List<Factor> factors;
  private final String canonical;

  private Unit(List<Factor> factors) {
    this.factors = factors;
    this.canonical = UnitExpression.write(factors);
  }

  record Factor(UnitAtom atom, int exponent) {}

  static Unit of(UnitAtom atom) {
    return new Unit(List.of(new Factor(atom, 1)));
  }

  static Unit ofExponents(Map<UnitAtom, Integer> exponents) {
    var sorted = new TreeMap<String, Factor>();
    exponents.forEach((atom, exponent) -> {
      if (exponent != 0) {
        sorted.put(atom.symbol(), new Factor(atom, exponent));
      }
    });
    return new Unit(List.copyOf(sorted.values()));
  }

  /**
   * This unit times {@code other}; the exponents of a shared unit add.
   *
   * @throws IllegalArgumentException if {@code other} is null, the two cancel out or an exponent overflows {@code int}
   */
  public Unit multiply(Unit other) {
    return combine(requireOther(other), Long::sum);
  }

  /**
   * This unit divided by {@code other}; the exponents of a shared unit subtract.
   *
   * @throws IllegalArgumentException if {@code other} is null, the two cancel out or an exponent overflows {@code int}
   */
  public Unit divide(Unit other) {
    return combine(requireOther(other), (mine, theirs) -> mine - theirs);
  }

  /**
   * This unit raised to {@code exponent}.
   *
   * @throws IllegalArgumentException if {@code exponent} is 0 or an exponent overflows {@code int}
   */
  public Unit pow(int exponent) {
    if (exponent == 0) {
      throw new IllegalArgumentException("Exponent must not be 0");
    }
    var raised = new ArrayList<Factor>(factors.size());
    for (Factor factor : factors) {
      raised.add(new Factor(factor.atom(), checkedExponent(factor.atom(), (long) factor.exponent() * exponent)));
    }
    return new Unit(List.copyOf(raised));
  }

  /**
   * This unit with {@code prefix}, such as {@code Units.METRE.withPrefix(Prefix.KILO)} for {@code km}.
   *
   * @throws IllegalArgumentException if {@code prefix} is null, this is not a single unprefixed unit with exponent 1, or
   *     the unit does not take {@code prefix} (minute, hour, day and currencies take none; tonne, bit and byte take only
   *     the multiples from kilo up, bit and byte also the binary prefixes)
   */
  public Unit withPrefix(Prefix prefix) {
    if (prefix == null) {
      throw new IllegalArgumentException("prefix must not be null");
    }
    if (factors.size() != 1 || factors.getFirst().exponent() != 1 || factors.getFirst().atom().isPrefixed()) {
      throw new IllegalArgumentException("Only a single unprefixed unit takes a prefix, not '" + canonical + "'");
    }
    UnitAtom atom = factors.getFirst().atom();
    if (!atom.takesAnyPrefix()) {
      throw new IllegalArgumentException("Unit '" + canonical + "' takes no prefix");
    }
    if (!atom.takes(prefix)) {
      throw new IllegalArgumentException("Unit '" + canonical + "' does not take the prefix '" + prefix.symbol() + "'");
    }
    return of(UnitCatalogue.prefixed(atom, prefix));
  }

  /** The systems of this unit's parts: {@code {PHYSICAL}} for {@code m/s}, {@code {MONETARY, PHYSICAL}} for {@code EUR/kg}. */
  public Set<UnitSystem> systems() {
    var systems = EnumSet.noneOf(UnitSystem.class);
    for (Factor factor : factors) {
      systems.add(factor.atom().system());
    }
    return Collections.unmodifiableSet(systems);
  }

  Dimension dimension() {
    Dimension dimension = Dimension.NONE;
    for (Factor factor : factors) {
      dimension = dimension.multiply(factor.atom().dimension().pow(factor.exponent()));
    }
    return dimension;
  }

  /** The canonical unit expression, such as {@code kg*m/s^2}. */
  @Override
  public String toString() {
    return canonical;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Unit other && factors.equals(other.factors);
  }

  @Override
  public int hashCode() {
    return factors.hashCode();
  }

  private static Unit requireOther(Unit other) {
    if (other == null) {
      throw new IllegalArgumentException("other must not be null");
    }
    return other;
  }

  private Unit combine(Unit other, LongBinaryOperator exponentRule) {
    var sorted = new TreeMap<String, Factor>();
    for (Factor factor : factors) {
      sorted.put(factor.atom().symbol(), factor);
    }
    for (Factor theirs : other.factors) {
      Factor mine = sorted.get(theirs.atom().symbol());
      long combined = exponentRule.applyAsLong(mine == null ? 0 : mine.exponent(), theirs.exponent());
      if (combined == 0) {
        sorted.remove(theirs.atom().symbol());
      } else {
        sorted.put(theirs.atom().symbol(), new Factor(theirs.atom(), checkedExponent(theirs.atom(), combined)));
      }
    }
    if (sorted.isEmpty()) {
      throw new IllegalArgumentException("Units '" + canonical + "' and '" + other.canonical + "' cancel out");
    }
    return new Unit(List.copyOf(sorted.values()));
  }

  private static int checkedExponent(UnitAtom atom, long exponent) {
    if (exponent != (int) exponent) {
      throw new IllegalArgumentException("Exponent of '" + atom.symbol() + "' overflows");
    }
    return (int) exponent;
  }
}
