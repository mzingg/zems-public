import type {Definition, Link} from 'mdast';
import {visit} from 'unist-util-visit';
import {linkedPath, parseMarkdown} from './links.ts';
import {type DocSource, pageUrl, SITE_NAME} from './sources.ts';

export type NavItem = {label: string; href: string; external?: true};
export type NavGroup = {label: string; items: NavItem[]};

const OVERVIEW = 'Overview';
const SOURCE_TREE = 'https://github.com/mzingg/zems-public/tree/main/10_lib';

function linkOrder(entry: DocSource, read: (source: DocSource) => string): string[] {
  const order: string[] = [];
  visit(parseMarkdown(read(entry)), ['link', 'definition'], (node) => {
    const target = linkedPath((node as Link | Definition).url, entry);
    if (target !== null && !order.includes(target)) order.push(target);
  });
  return order;
}

function byTitle(titles: Map<string, string>) {
  return (a: DocSource, b: DocSource) => titles.get(a.sourcePath)!.localeCompare(titles.get(b.sourcePath)!);
}

/** Pages in the order the entry page first links them, then the pages it does not link, by title. */
function inEntryOrder(pages: DocSource[], order: string[], titles: Map<string, string>): DocSource[] {
  const linked = order.flatMap((target) => pages.filter((page) => page.sourcePath === target));
  const unlinked = pages.filter((page) => !linked.includes(page)).sort(byTitle(titles));
  return [...linked, ...unlinked];
}

function suiteGroup(sources: DocSource[], titles: Map<string, string>): NavGroup {
  const suite = sources.filter((source) => source.library === null);
  const overview = suite.filter((source) => source.slug === '');
  const pages = suite.filter((source) => source.slug !== '').sort(byTitle(titles));
  return {
    label: SITE_NAME,
    items: [
      ...overview.map((source) => ({label: OVERVIEW, href: pageUrl(source.slug)})),
      ...pages.map((source) => ({label: titles.get(source.sourcePath)!, href: pageUrl(source.slug)})),
    ],
  };
}

function libraryGroup(
  library: string,
  sources: DocSource[],
  titles: Map<string, string>,
  read: (source: DocSource) => string
): NavGroup {
  const own = sources.filter((source) => source.library === library);
  const readme = own.find((source) => source.slug === library);
  const entry = own.find((source) => source.slug === `${library}/docs`);
  const pages = own.filter((source) => source !== readme && source !== entry);
  const ordered = inEntryOrder(pages, entry ? linkOrder(entry, read) : [], titles);
  const item = (source: DocSource): NavItem => ({label: titles.get(source.sourcePath)!, href: pageUrl(source.slug)});
  return {
    label: readme ? titles.get(readme.sourcePath)! : library,
    items: [
      ...(readme ? [{label: OVERVIEW, href: pageUrl(readme.slug)}] : []),
      ...(entry ? [item(entry)] : []),
      ...ordered.map(item),
      {label: 'Source', href: `${SOURCE_TREE}/${library}`, external: true},
    ],
  };
}

/** The suite group, then one group per published library in source order; `titles` is keyed by source path. */
export function navigation(
  sources: DocSource[],
  titles: Map<string, string>,
  read: (source: DocSource) => string
): NavGroup[] {
  const libraries = [...new Set(sources.flatMap((source) => (source.library === null ? [] : [source.library])))];
  return [suiteGroup(sources, titles), ...libraries.map((library) => libraryGroup(library, sources, titles, read))];
}
