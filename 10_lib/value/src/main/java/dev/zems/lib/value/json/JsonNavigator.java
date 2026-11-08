package dev.zems.lib.value.json;

import dev.zems.lib.value.Navigator;
import dev.zems.lib.value.Value;
import dev.zems.lib.value.json.engine.JsonArray;
import dev.zems.lib.value.json.engine.JsonObject;
import dev.zems.lib.value.json.engine.JsonValue;
import dev.zems.lib.value.json.engine.internal.JsonLocations;
import java.util.List;

/**
 * Steps through members and elements of a JSON value. The first failing step ends the chain in an error naming the
 * walked path from {@code $} and, for a parsed value, its line and column; later steps keep that failure.
 */
public final class JsonNavigator implements Navigator {

  private final JsonValue current;
  private final PathStep path;
  private final Value<JsonValue> failure;

  JsonNavigator(JsonValue current, PathStep path, Value<JsonValue> failure) {
    this.current = current;
    this.path = path;
    this.failure = failure;
  }

  static JsonNavigator root(JsonValue value) {
    return new JsonNavigator(value, PathStep.ROOT, null);
  }

  @Override
  public JsonNavigator get(String name) {
    if (name == null) {
      throw new IllegalArgumentException(JsonValueMessages.NAME_NULL);
    }
    if (failure != null) {
      return this;
    }
    if (current instanceof JsonObject object) {
      JsonValue member = object.asMap().get(name);
      if (member == null) {
        return failed(JsonValueMessages.missingMember(name));
      }
      return new JsonNavigator(member, path.with(name), null);
    }
    return failed(JsonValueMessages.memberOfKind(name, current));
  }

  @Override
  public JsonNavigator get(int index) {
    if (failure != null) {
      return this;
    }
    if (current instanceof JsonArray array) {
      List<JsonValue> elements = array.asList();
      if (index < 0 || index >= elements.size()) {
        return failed(JsonValueMessages.outOfRange(index, elements.size()));
      }
      return new JsonNavigator(elements.get(index), path.with(index), null);
    }
    return failed(JsonValueMessages.elementOfKind(index, current));
  }

  @Override
  public Value<JsonValue> value() {
    return failure != null ? failure : JsonWrapping.wrap(current);
  }

  @Override
  public <R> Value<R> convertTo(Class<R> target) {
    return value().convertTo(target);
  }

  private JsonNavigator failed(String detail) {
    String message = JsonValueMessages.navigationFailure(
      JsonLocations.path(path.steps()),
      detail,
      JsonLocations.lineAndColumn(current).orElse("")
    );
    return new JsonNavigator(null, path, typed(Value.errorMessage(message, Object.class)));
  }

  @SuppressWarnings("unchecked")
  private static Value<JsonValue> typed(Value<?> value) {
    return (Value<JsonValue>) value;
  }
}
