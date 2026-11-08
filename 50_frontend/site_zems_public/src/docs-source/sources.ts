import fs from 'node:fs';
import path from 'node:path';

// Modules in src/docs-source/ import each other relatively with `.ts` extensions and no `@/` alias: Node runs them
// unbundled (type stripping) in scripts/write-raw-docs.ts and reads no tsconfig paths.

export const SITE = 'https://zems.dev';
export const SITE_NAME = 'ZEMS';

const PUBLIC_MARKER = '<zems.public>true</zems.public>';
const LIBRARIES_DIR = '10_lib';

export type DocSource = {
  sourcePath: string; // repo-relative, posix: 'README.md', 'docs/setup.md', '10_lib/value/docs/setup.md'
  slug: string; // '' (landing), 'setup', 'value', 'value/docs', 'value/docs/setup'
  library: string | null; // 'value', or null for suite pages
};

// The module directory is the working directory of `next build`, jest and the twin script alike.
export function repoRoot(): string {
  return path.resolve(process.cwd(), '..', '..');
}

export function publishedLibraries(root: string): string[] {
  const librariesDir = path.join(root, LIBRARIES_DIR);
  if (!fs.existsSync(librariesDir)) return [];
  return fs
    .readdirSync(librariesDir, {withFileTypes: true})
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .filter((name) => {
      const pom = path.join(librariesDir, name, 'pom.xml');
      return fs.existsSync(pom) && fs.readFileSync(pom, 'utf8').includes(PUBLIC_MARKER);
    })
    .sort();
}

function markdownFiles(root: string, dir: string): string[] {
  const absolute = path.join(root, dir);
  if (!fs.existsSync(absolute)) return [];
  return fs
    .readdirSync(absolute, {withFileTypes: true})
    .filter((entry) => entry.isFile() && entry.name.endsWith('.md'))
    .map((entry) => entry.name)
    .sort();
}

function exists(root: string, sourcePath: string): boolean {
  return fs.existsSync(path.join(root, sourcePath));
}

export function collectSources(root: string): DocSource[] {
  const sources: DocSource[] = [];
  if (exists(root, 'README.md')) sources.push({sourcePath: 'README.md', slug: '', library: null});
  for (const name of markdownFiles(root, 'docs')) {
    sources.push({sourcePath: `docs/${name}`, slug: name.slice(0, -'.md'.length), library: null});
  }
  for (const library of publishedLibraries(root)) {
    const base = `${LIBRARIES_DIR}/${library}`;
    if (exists(root, `${base}/README.md`)) sources.push({sourcePath: `${base}/README.md`, slug: library, library});
    for (const name of markdownFiles(root, `${base}/docs`)) {
      const slug = name === 'README.md' ? `${library}/docs` : `${library}/docs/${name.slice(0, -'.md'.length)}`;
      sources.push({sourcePath: `${base}/docs/${name}`, slug, library});
    }
  }

  const bySlug = new Map<string, DocSource>();
  for (const source of sources) {
    const clash = bySlug.get(source.slug);
    if (clash) {
      throw new Error(`Duplicate page slug '${source.slug}' from ${clash.sourcePath} and ${source.sourcePath}`);
    }
    bySlug.set(source.slug, source);
  }
  return sources;
}

export function readSource(root: string, source: DocSource): string {
  return fs.readFileSync(path.join(root, source.sourcePath), 'utf8');
}

export function pageUrl(slug: string): string {
  return slug === '' ? '/' : `/${slug}`;
}

export function rawUrl(slug: string): string {
  return slug === '' ? '/index.md' : `/${slug}.md`;
}
