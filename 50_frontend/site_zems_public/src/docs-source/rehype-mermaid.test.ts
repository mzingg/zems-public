/** @jest-environment node */
import rehypeStringify from 'rehype-stringify';
import remarkParse from 'remark-parse';
import remarkRehype from 'remark-rehype';
import {unified} from 'unified';
import {rehypeMermaid} from '@/docs-source/rehype-mermaid';

function toHtml(markdown: string): string {
  return String(
    unified().use(remarkParse).use(remarkRehype).use(rehypeMermaid).use(rehypeStringify).processSync(markdown)
  );
}

describe('rehypeMermaid', () => {
  it('turns a mermaid code block into a mermaid pre holding the diagram text', () => {
    const html = toHtml('```mermaid\nflowchart LR\n  A --> B\n```\n');

    expect(html).toMatch(/^<pre class="mermaid">flowchart LR\n {2}A --> B\n?<\/pre>$/);
  });

  it('leaves other code blocks untouched', () => {
    const markdown = '```java\nint x = 1;\n```\n';

    expect(toHtml(markdown)).toBe(
      String(unified().use(remarkParse).use(remarkRehype).use(rehypeStringify).processSync(markdown))
    );
    expect(toHtml(markdown)).toBe('<pre><code class="language-java">int x = 1;\n</code></pre>');
  });

  it('leaves inline code and fences without a language untouched', () => {
    expect(toHtml('Use `mermaid` here.\n\n```\nplain\n```\n')).toBe(
      '<p>Use <code>mermaid</code> here.</p>\n<pre><code>plain\n</code></pre>'
    );
  });
});
