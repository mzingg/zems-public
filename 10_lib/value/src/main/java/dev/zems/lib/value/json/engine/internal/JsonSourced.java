package dev.zems.lib.value.json.engine.internal;

/** A library value that remembers the text it was parsed from; a built value answers a null source. */
interface JsonSourced {
  char[] source();

  int offset();
}
