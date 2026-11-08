package dev.zems.lib.value.json.engine.internal;

/** An open object or array on the way from the document root to the value being read; one segment of its path. */
class JsonFrame {

  private final boolean object;
  private String name;
  private int index;

  JsonFrame(boolean object) {
    this.object = object;
  }

  final boolean isObject() {
    return object;
  }

  final String name() {
    return name;
  }

  final void name(String memberName) {
    name = memberName;
  }

  final int index() {
    return index;
  }

  final void next() {
    if (object) {
      name = null;
    } else {
      index++;
    }
  }
}
