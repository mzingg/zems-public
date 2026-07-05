import type {ReactNode} from 'react';

// The prose of the landing page, one exported fragment per section. Text edits happen here (or in libraries.tsx,
// meta.ts, version.ts) — src/app is only the rendering shell.

export const INTRO: ReactNode = (
  <>
    ZEMS is a suite of small, self-contained JVM libraries published under the <code>dev.zems</code> namespace — each
    does one thing, carries no runtime dependencies of its own, and stays easy to extend. The first library available is{' '}
    <code>value</code>; more will follow.
  </>
);

export const LIBRARY_OUTRO: ReactNode = <>More modules from the suite will appear here as they are released.</>;

export const USE_IT: ReactNode = (
  <>
    Released on <a href="https://central.sonatype.com/artifact/dev.zems.lib/value">Maven Central</a> — no extra
    repository or token needed. Requires JDK 25 or newer.
  </>
);

export const SOURCE: ReactNode = (
  <>
    The published code lives on GitHub:{' '}
    <a href="https://github.com/mzingg/zems-public">github.com/mzingg/zems-public</a>. Contributions are welcome — see
    the repository&apos;s <code>CONTRIBUTING.md</code>.
  </>
);

export const FOOTER: ReactNode = (
  <>
    Apache License 2.0. Released versions are listed on the{' '}
    <a href="https://github.com/mzingg/zems-public/releases">GitHub releases</a> page.
  </>
);
