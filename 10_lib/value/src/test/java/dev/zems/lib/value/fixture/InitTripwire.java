package dev.zems.lib.value.fixture;

// Never named as a class literal: tests pass its name as data, so only resolving that name runs this initialiser.
public final class InitTripwire {

  static {
    Tripwires.INIT_TRIPWIRE_INITIALISED.set(true);
  }

  private InitTripwire() {}
}
