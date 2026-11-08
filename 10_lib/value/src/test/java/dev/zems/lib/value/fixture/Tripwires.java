package dev.zems.lib.value.fixture;

import java.util.concurrent.atomic.AtomicBoolean;

// The flag lives outside InitTripwire: reading a field of that class would run the initialiser it watches.
public final class Tripwires {

  public static final AtomicBoolean INIT_TRIPWIRE_INITIALISED = new AtomicBoolean();

  private Tripwires() {}
}
