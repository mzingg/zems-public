import type {Definition, Link, Paragraph} from 'mdast';
import {toString} from 'mdast-util-to-string';
import rehypeSlug from 'rehype-slug';
import rehypeStringify from 'rehype-stringify';
import remarkGfm from 'remark-gfm';
import remarkParse from 'remark-parse';
import remarkRehype from 'remark-rehype';
import {unified} from 'unified';
import {visit} from 'unist-util-visit';
import {type HeadingIndex, publishedUrl, resolveLink, titleHeading} from './links.ts';
import {rehypeMermaid} from './rehype-mermaid.ts';
import type {DocSource} from './sources.ts';

export type RenderedPage = {title: string; description: string; html: string};

const processor = unified()
  .use(remarkParse)
  .use(remarkGfm)
  .use(remarkRehype)
  .use(rehypeSlug)
  .use(rehypeMermaid)
  .use(rehypeStringify);

function plainText(node: Paragraph | undefined): string {
  return node ? toString(node).replace(/\s+/g, ' ').trim() : '';
}

/**
 * Renders a source to HTML without its title heading, with every link pointed at its page URL.
 *
 * @throws Error when the source has no first-level heading
 * @throws BrokenDocLinkError when a link points at nothing published
 */
export function renderPage(
  markdown: string,
  from: DocSource,
  sources: DocSource[],
  headings: HeadingIndex
): RenderedPage {
  const tree = processor.parse(markdown);
  const title = titleHeading(tree);
  if (!title) throw new Error(`Missing '# ' title in ${from.sourcePath}`);
  tree.children.splice(tree.children.indexOf(title), 1);

  visit(tree, ['link', 'definition'], (node) => {
    const linkOrDefinition = node as Link | Definition;
    linkOrDefinition.url = publishedUrl(resolveLink(linkOrDefinition.url, from, sources, headings), 'page');
  });

  const firstParagraph = tree.children.find((node): node is Paragraph => node.type === 'paragraph');
  return {
    title: toString(title),
    description: plainText(firstParagraph),
    html: processor.stringify(processor.runSync(tree)),
  };
}
