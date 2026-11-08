import {headingIndex, type HeadingIndex} from './links.ts';
import {type NavGroup, navigation} from './navigation.ts';
import {renderPage, type RenderedPage} from './render.ts';
import {collectSources, type DocSource, readSource} from './sources.ts';

export type DocSite = {
  root: string;
  sources: DocSource[];
  headings: HeadingIndex;
  pages: Map<string, RenderedPage>; // slug → rendered page
  groups: NavGroup[];
};

/**
 * Collects and renders every source under the repo root.
 *
 * @throws BrokenDocLinkError when any source links to nothing published
 */
export function loadSite(root: string): DocSite {
  const sources = collectSources(root);
  const headings = headingIndex(root, sources);
  const read = (source: DocSource) => readSource(root, source);
  const pages = new Map(sources.map((source) => [source.slug, renderPage(read(source), source, sources, headings)]));
  const titles = new Map(sources.map((source) => [source.sourcePath, pages.get(source.slug)!.title]));
  return {root, sources, headings, pages, groups: navigation(sources, titles, read)};
}
