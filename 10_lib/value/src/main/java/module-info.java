module dev.zems.lib.value {
  // JDK platform modules consumed by the published test-jar (AllocationMeasurement uses
  // com.sun.management.ThreadMXBean for per-thread allocation accounting). No external
  // dependency added — both modules ship with every HotSpot / OpenJDK build.
  requires java.management;
  requires jdk.management;

  exports dev.zems.lib.value;
  exports dev.zems.lib.value.builtin;
  exports dev.zems.lib.value.cache;
  exports dev.zems.lib.value.marshal;
  exports dev.zems.lib.value.marshal.descriptor;
  exports dev.zems.lib.value.marshal.wire;
  exports dev.zems.lib.value.marshal.wire.v1;
  exports dev.zems.lib.value.marshal.format.binary;
  exports dev.zems.lib.value.marshal.format.json;
}
