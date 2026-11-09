import fs from 'node:fs';
import path from 'node:path';

// Modules in src/docs-source/ import each other relatively with `.ts` extensions and no `@/` alias: Node runs them
// unbundled (type stripping) in scripts/write-raw-docs.ts and reads no tsconfig paths.

export const SITE = 'https://zems.dev';
export const SITE_NAME = 'ZEMS';

const PUBLIC_MARKER = '<zems.public>true</zems.public>';
const LIBRARIES_DIR = '10_lib';
const DOCS_DIR = 'docs';

export type DocSource = {
  sourcePath: string; // repo-relative, posix: 'README.md', 'docs/setup.md', '10_lib/value/docs/setup.md'
  slug: string; // '' (landing), 'setup', 'value', 'value/docs', 'value/docs/setup', 'essay', 'essay/first'
  library: string | null; // 'value', or null for suite and section pages
  section: string | null; // 'essay' for the pages of docs/essay/, or null for suite and library pages
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

function subdirectories(root: string, dir: string): string[] {
  const absolute = path.join(root, dir);
  if (!fs.existsSync(absolute)) return [];
  return fs
    .readdirSync(absolute, {withFileTypes: true})
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .sort();
}

function exists(root: string, sourcePath: string): boolean {
  return fs.existsSync(path.join(root, sourcePath));
}

function pageName(fileName: string): string {
  return fileName.slice(0, -'.md'.length);
}

export function collectSources(root: string): DocSource[] {
  const sources: DocSource[] = [];
  if (exists(root, 'README.md')) sources.push({sourcePath: 'README.md', slug: '', library: null, section: null});
  for (const name of markdownFiles(root, DOCS_DIR)) {
    sources.push({sourcePath: `${DOCS_DIR}/${name}`, slug: pageName(name), library: null, section: null});
  }
  for (const section of subdirectories(root, DOCS_DIR)) {
    for (const name of markdownFiles(root, `${DOCS_DIR}/${section}`)) {
      const slug = name === 'README.md' ? section : `${section}/${pageName(name)}`;
      sources.push({sourcePath: `${DOCS_DIR}/${section}/${name}`, slug, library: null, section});
    }
  }
  for (const library of publishedLibraries(root)) {
    const base = `${LIBRARIES_DIR}/${library}`;
    if (exists(root, `${base}/README.md`)) {
      sources.push({sourcePath: `${base}/README.md`, slug: library, library, section: null});
    }
    for (const name of markdownFiles(root, `${base}/docs`)) {
      const slug = name === 'README.md' ? `${library}/docs` : `${library}/docs/${pageName(name)}`;
      sources.push({sourcePath: `${base}/docs/${name}`, slug, library, section: null});
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
