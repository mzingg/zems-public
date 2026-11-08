package dev.zems.lib.value.fixture;

import dev.zems.lib.value.SlotShape;
import dev.zems.lib.value.Value;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

// Not final: a subclass payload pins that conversion errors name SlotBag, the shape type, not the runtime class.
public class SlotBag {

  public static final SlotShape<SlotBag> SLOTS = new SlotShape<>() {
    @Override
    public Class<SlotBag> type() {
      return SlotBag.class;
    }

    @Override
    public Map<String, Value<?>> slots(SlotBag instance) {
      return instance.slots();
    }

    @Override
    public Value<SlotBag> build(Map<String, Value<?>> slots) {
      return Value.of(new SlotBag(slots));
    }
  };

  private final Map<String, Value<?>> slots;

  public SlotBag(Map<String, Value<?>> slots) {
    this.slots = Collections.unmodifiableMap(new LinkedHashMap<>(slots));
  }

  public static SlotBag empty() {
    return new SlotBag(Map.of());
  }

  public SlotBag with(String name, Value<?> value) {
    var next = new LinkedHashMap<>(slots);
    next.put(name, value);
    return new SlotBag(next);
  }

  public Map<String, Value<?>> slots() {
    return slots;
  }
}
