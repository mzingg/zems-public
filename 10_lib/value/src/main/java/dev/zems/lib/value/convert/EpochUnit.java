package dev.zems.lib.value.convert;

/**
 * The unit an epoch number counts in, for {@link Conversions#epochToInstant(EpochUnit)}. Every constant counts from
 * {@code 1970-01-01T00:00:00Z}.
 */
public enum EpochUnit {
  SECONDS,
  MILLIS,
  MICROS,
  NANOS,
}
