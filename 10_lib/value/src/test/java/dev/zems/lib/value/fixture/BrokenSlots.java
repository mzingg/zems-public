package dev.zems.lib.value.fixture;

import dev.zems.lib.value.SlotShape;
import dev.zems.lib.value.Value;
import java.util.Map;

public final class BrokenSlots {

  public static final SlotShape<BrokenSlots> SLOTS = new SlotShape<>() {
    @Override
    public Class<BrokenSlots> type() {
      return BrokenSlots.class;
    }

    @Override
    public Map<String, Value<?>> slots(BrokenSlots instance) {
      throw new IllegalStateException("broken");
    }

    @Override
    public Value<BrokenSlots> build(Map<String, Value<?>> slots) {
      return Value.of(new BrokenSlots());
    }
  };
}
