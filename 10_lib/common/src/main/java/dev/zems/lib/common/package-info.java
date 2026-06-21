/**
 * Shared zems utilities. Today the module's value sits in its {@code tests} classifier JAR (the {@code _test}
 * package), which carries the {@code @ContractTest} / {@code @JourneyTest} annotations and the
 * {@code AllocationMeasurement} helper used across modules. The main JAR is a placeholder; production helpers will land
 * here when the JDK does not already provide what we need.
 */
package dev.zems.lib.common;
