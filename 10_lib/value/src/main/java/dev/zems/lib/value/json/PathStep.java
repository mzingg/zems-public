package dev.zems.lib.value.json;

import java.util.Arrays;
import java.util.List;

/** One step of a walked path, linked to the step before it, so taking a step costs the same at any depth. */
final class PathStep {

  static final PathStep ROOT = new PathStep(null, null, 0);

  private final PathStep parent;
  private final Object step;
  private final int depth;

  private PathStep(PathStep parent, Object step, int depth) {
    this.parent = parent;
    this.step = step;
    this.depth = depth;
  }

  PathStep with(String name) {
    return new PathStep(this, name, depth + 1);
  }

  PathStep with(int index) {
    return new PathStep(this, index, depth + 1);
  }

  List<Object> steps() {
    Object[] steps = new Object[depth];
    for (PathStep at = this; at.parent != null; at = at.parent) {
      steps[at.depth - 1] = at.step;
    }
    return Arrays.asList(steps);
  }
}
