import type {Metadata} from 'next';
import {loadSite, type DocSite} from '@/docs-source/site';
import {rawUrl, repoRoot, SITE_NAME} from '@/docs-source/sources';

let site: DocSite | undefined;

export function docSite(): DocSite {
  site ??= loadSite(repoRoot());
  return site;
}

export function pageMetadata(slug: string): Metadata {
  const page = docSite().pages.get(slug)!;
  return {
    title: slug === '' ? SITE_NAME : `${page.title} · ${SITE_NAME}`,
    description: page.description,
    alternates: {types: {'text/markdown': rawUrl(slug)}},
  };
}
