package dev.zems.lib.value;

import java.util.Map;

/**
 * How a named-slot structure takes part in structural conversion. A type participates by declaring
 * {@code public static final SlotShape<X> SLOTS}; {@link Value#convertTo(Class)} finds it the way
 * {@code TypeDescriptor.find} finds {@code DESCRIPTOR} — no registry, nothing registered at runtime.
 *
 * <p>
 * A record converts into the structure by handing one slot per component, named after the component and in component
 * order, to {@link #build}; the structure converts into a record by reading {@link #slots} and filling each component
 * from the same-named slot. A map-shaped structure, whose slot names exist only at runtime, and a fixed-slot one
 * implement the same three methods: conversion always reads by name.
 */
public interface SlotShape<T> {
  /** The structure type this shape reads and builds; an instance {@link #build} answers is always one of these. */
  Class<T> type();

  /** The named slots {@code instance} holds. A name the map lacks is a missing slot. */
  Map<String, Value<?>> slots(T instance);

  /** Builds an instance from name-value pairs; answers an error value when the pairs do not form a valid instance. */
  Value<T> build(Map<String, Value<?>> slots);
}
