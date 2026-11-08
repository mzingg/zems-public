import path from 'node:path';
import GithubSlugger from 'github-slugger';
import type {Definition, Heading, Link, Root} from 'mdast';
import {toString} from 'mdast-util-to-string';
import remarkGfm from 'remark-gfm';
import remarkParse from 'remark-parse';
import {unified} from 'unified';
import {visit} from 'unist-util-visit';
import {type DocSource, pageUrl, rawUrl, readSource} from './sources.ts';

export class BrokenDocLinkError extends Error {}

export type ResolvedLink =
  {kind: 'external' | 'same-page'; url: string} | {kind: 'doc'; target: DocSource; anchor: string | null};

export type HeadingIndex = Map<string, Set<string>>; // sourcePath → github-slugger ids of its headings

const SCHEME = /^[a-z][a-z0-9+.-]*:/i;

export function parseMarkdown(markdown: string): Root {
  return unified().use(remarkParse).use(remarkGfm).parse(markdown);
}

/** The first first-level heading: the page title, which the page renders apart from the body. */
export function titleHeading(tree: Root): Heading | undefined {
  return tree.children.find((node): node is Heading => node.type === 'heading' && node.depth === 1);
}

// The title heading is left out because the rendered body does not hold it: rehype-slug never sees its text, so a
// later heading with the same text gets the unsuffixed id on the page and must get it here too.
function headingIds(markdown: string): Set<string> {
  const tree = parseMarkdown(markdown);
  const title = titleHeading(tree);
  const slugger = new GithubSlugger();
  const ids = new Set<string>();
  visit(tree, 'heading', (heading) => {
    if (heading !== title) ids.add(slugger.slug(toString(heading)));
  });
  return ids;
}

export function headingIndex(root: string, sources: DocSource[]): HeadingIndex {
  return new Map(sources.map((source) => [source.sourcePath, headingIds(readSource(root, source))]));
}

/** The repo-relative path a relative link points at, or null for an external or same-page link. */
export function linkedPath(url: string, from: DocSource): string | null {
  if (SCHEME.test(url) || url.startsWith('#')) return null;
  const target = url.split('#')[0];
  return path.posix.normalize(path.posix.join(path.posix.dirname(from.sourcePath), target));
}

function broken(from: DocSource, url: string, reason: string): BrokenDocLinkError {
  return new BrokenDocLinkError(`Broken link in ${from.sourcePath}: '${url}' — ${reason}.`);
}

function requireHeading(headings: HeadingIndex, target: string, anchor: string, from: DocSource, url: string) {
  if (!headings.get(target)?.has(anchor)) throw broken(from, url, `${target} has no heading '${anchor}'`);
}

/** Resolves a link URL of a source; throws {@link BrokenDocLinkError} when it points at nothing published. */
export function resolveLink(url: string, from: DocSource, sources: DocSource[], headings: HeadingIndex): ResolvedLink {
  if (SCHEME.test(url)) return {kind: 'external', url};
  if (url.startsWith('#')) {
    requireHeading(headings, from.sourcePath, url.slice(1), from, url);
    return {kind: 'same-page', url};
  }
  const targetPath = linkedPath(url, from)!;
  const target = sources.find((source) => source.sourcePath === targetPath);
  if (!target) throw broken(from, url, `${targetPath} is not a published document`);
  const hash = url.indexOf('#');
  const anchor = hash < 0 || hash === url.length - 1 ? null : url.slice(hash + 1);
  if (anchor !== null) requireHeading(headings, target.sourcePath, anchor, from, url);
  return {kind: 'doc', target, anchor};
}

/** The URL a resolved link gets on the site: the page form for HTML pages, the twin form for raw markdown. */
export function publishedUrl(link: ResolvedLink, form: 'page' | 'twin'): string {
  if (link.kind !== 'doc') return link.url;
  const base = form === 'page' ? pageUrl(link.target.slug) : rawUrl(link.target.slug);
  return link.anchor === null ? base : `${base}#${link.anchor}`;
}

type Splice = {start: number; end: number; url: string};

function urlSplice(markdown: string, node: Link | Definition, url: string): Splice {
  const start = node.position!.start.offset!;
  const end = node.position!.end.offset!;
  const destinationFrom =
    node.type === 'link' ? (node.children.at(-1)?.position?.end.offset ?? start + 1) : markdown.indexOf(']:', start);
  const at = markdown.indexOf(node.url, destinationFrom);
  if (at < 0 || at + node.url.length > end) {
    throw new Error(`Cannot locate the link target '${node.url}' in its source text at offset ${start}`);
  }
  return {start: at, end: at + node.url.length, url};
}

/**
 * Returns the markdown with every link target pointed at its raw twin; every other byte stays as in the source.
 *
 * @throws BrokenDocLinkError when a link points at nothing published
 */
export function rewriteRaw(markdown: string, from: DocSource, sources: DocSource[], headings: HeadingIndex): string {
  const splices: Splice[] = [];
  visit(parseMarkdown(markdown), ['link', 'definition'], (node) => {
    const linkOrDefinition = node as Link | Definition;
    const resolved = resolveLink(linkOrDefinition.url, from, sources, headings);
    if (resolved.kind === 'doc') splices.push(urlSplice(markdown, linkOrDefinition, publishedUrl(resolved, 'twin')));
  });
  return splices
    .sort((a, b) => b.start - a.start)
    .reduce((text, splice) => text.slice(0, splice.start) + splice.url + text.slice(splice.end), markdown);
}
