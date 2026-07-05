import type {ReactNode} from 'react';

export type Library = {
  groupId: string;
  artifactId: string;
  /** One-sentence summary without a trailing full stop; may carry inline <code> spans. */
  summary: ReactNode;
};

// The published libraries shown in the "Library" section, in display order. Adding the next published library to
// the site is one new entry here — the app shell in src/app renders whatever this list carries. The first entry
// also provides the coordinates of the dependency snippet in the "Use it" section.
export const LIBRARIES: Library[] = [
  {
    groupId: 'dev.zems.lib',
    artifactId: 'value',
    summary: (
      <>
        a typed value model (<code>Value&lt;T&gt;</code>) with a versioned binary and JSON marshalling layer, and no
        runtime dependencies of its own
      </>
    ),
  },
];
