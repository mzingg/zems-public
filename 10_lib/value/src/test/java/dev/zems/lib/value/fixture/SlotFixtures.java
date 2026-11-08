package dev.zems.lib.value.fixture;

import dev.zems.lib.value.SlotShape;
import dev.zems.lib.value.Value;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SlotFixtures {

  private SlotFixtures() {}

  public static final class RefusingBag {

    public static final SlotShape<RefusingBag> SLOTS = new SlotShape<>() {
      @Override
      public Class<RefusingBag> type() {
        return RefusingBag.class;
      }

      @Override
      public Map<String, Value<?>> slots(RefusingBag instance) {
        return Map.of();
      }

      @Override
      public Value<RefusingBag> build(Map<String, Value<?>> slots) {
        return Value.errorMessage("a refusing bag accepts no slots", RefusingBag.class);
      }
    };
  }

  public static final class MisdeclaredSlots {

    public static final String SLOTS = "not a shape";
  }

  public static final class NullSlots {

    public static final SlotShape<NullSlots> SLOTS = null;
  }

  public static final class InstanceSlots {

    public final SlotShape<SlotBag> SLOTS = SlotBag.SLOTS;
  }

  public record Point(int x, int y) {
    public static final SlotShape<Point> SLOTS = new SlotShape<>() {
      @Override
      public Class<Point> type() {
        return Point.class;
      }

      @Override
      public Map<String, Value<?>> slots(Point instance) {
        Map<String, Value<?>> slots = new LinkedHashMap<>();
        slots.put("x", Value.of(instance.x()));
        slots.put("y", Value.of(instance.y()));
        return slots;
      }

      @Override
      public Value<Point> build(Map<String, Value<?>> slots) {
        return Value.of(new Point((Integer) slots.get("x").unbox(), (Integer) slots.get("y").unbox()));
      }
    };
  }
}
