module dev.zems.lib.value {
  requires static org.junit.jupiter.api;
  requires static org.assertj.core;

  exports dev.zems.lib.value;
  exports dev.zems.lib.value.builtin;
  exports dev.zems.lib.value.cache;
  exports dev.zems.lib.value.convert;
  exports dev.zems.lib.value.valueio;
  exports dev.zems.lib.value.valueio.descriptor;
  exports dev.zems.lib.value.valueio.wire;
  exports dev.zems.lib.value.valueio.wire.v1;
  exports dev.zems.lib.value.valueio.format.binary;
  exports dev.zems.lib.value.valueio.format.json;
  exports dev.zems.lib.value.json;
  exports dev.zems.lib.value.json.engine;
  exports dev.zems.lib.value.junit;
}
