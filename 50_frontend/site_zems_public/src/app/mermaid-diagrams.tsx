'use client';

import {useEffect} from 'react';

// Imported on demand so a page without a diagram never downloads mermaid.
export function MermaidDiagrams() {
  useEffect(() => {
    if (!document.querySelector('pre.mermaid')) return;
    import('mermaid')
      .then(async ({default: mermaid}) => {
        const dark = window.matchMedia('(prefers-color-scheme: dark)').matches;
        mermaid.initialize({startOnLoad: false, theme: dark ? 'dark' : 'default'});
        await mermaid.run();
      })
      .catch((error: unknown) => console.error('Mermaid diagrams failed to render', error));
  }, []);
  return null;
}
