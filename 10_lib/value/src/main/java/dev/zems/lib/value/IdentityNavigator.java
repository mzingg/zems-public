package dev.zems.lib.value;

/** The navigator of a value with nothing to step into: a step answers an unsupported value, a state marker stays. */
record IdentityNavigator(Value<?> value) implements Navigator {
  private static final String NAME_NULL = "Member name must not be null";

  @Override
  public Navigator get(String name) {
    if (name == null) {
      throw new IllegalArgumentException(NAME_NULL);
    }
    return step();
  }

  @Override
  public Navigator get(int index) {
    return step();
  }

  @Override
  public <R> Value<R> convertTo(Class<R> target) {
    return value.convertTo(target);
  }

  private Navigator step() {
    if (value.isStateMarker()) {
      return this;
    }
    return new IdentityNavigator(
      Value.unsupported(value.getClass().getSimpleName() + " is not navigable", Object.class)
    );
  }
}
