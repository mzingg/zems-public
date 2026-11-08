import type {Element, Root} from 'hast';
import {visit} from 'unist-util-visit';

function isMermaidCode(node: Element['children'][number] | undefined): node is Element {
  if (node?.type !== 'element' || node.tagName !== 'code') return false;
  const className = node.properties.className;
  return Array.isArray(className) && className.includes('language-mermaid');
}

/** Turns `pre > code.language-mermaid` into `pre.mermaid` holding the diagram text, which mermaid renders in the browser. */
export function rehypeMermaid() {
  return (tree: Root) => {
    visit(tree, 'element', (node) => {
      if (node.tagName !== 'pre' || node.children.length !== 1) return;
      const code = node.children[0];
      if (!isMermaidCode(code)) return;
      node.properties = {className: ['mermaid']};
      node.children = code.children;
    });
  };
}
