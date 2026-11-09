/** @jest-environment node */
import path from 'node:path';
import {
  collectSources,
  type DocSource,
  pageUrl,
  publishedLibraries,
  rawUrl,
  readSource,
  repoRoot,
} from '@/docs-source/sources';
import {ALPHA_SETUP, createFixtureRepo, type FixtureRepo, MARKED_POM} from '@/docs-source/test-support/fixture-repo';

const byPath = (a: DocSource, b: DocSource) => a.sourcePath.localeCompare(b.sourcePath);

describe('sources', () => {
  let repo: FixtureRepo;

  beforeEach(() => {
    repo = createFixtureRepo();
  });

  afterEach(() => {
    repo.remove();
  });

  it('collects the suite pages and the pages of marked libraries only', () => {
    const sources = collectSources(repo.root);

    expect([...sources].sort(byPath)).toEqual(
      [
        {sourcePath: 'README.md', slug: '', library: null, section: null},
        {sourcePath: 'docs/guide.md', slug: 'guide', library: null, section: null},
        {sourcePath: 'docs/essay/README.md', slug: 'essay', library: null, section: 'essay'},
        {sourcePath: 'docs/essay/first.md', slug: 'essay/first', library: null, section: 'essay'},
        {sourcePath: 'docs/essay/second.md', slug: 'essay/second', library: null, section: 'essay'},
        {sourcePath: '10_lib/alpha/README.md', slug: 'alpha', library: 'alpha', section: null},
        {sourcePath: '10_lib/alpha/docs/README.md', slug: 'alpha/docs', library: 'alpha', section: null},
        {sourcePath: '10_lib/alpha/docs/diagram.md', slug: 'alpha/docs/diagram', library: 'alpha', section: null},
        {sourcePath: '10_lib/alpha/docs/setup.md', slug: 'alpha/docs/setup', library: 'alpha', section: null},
        {sourcePath: '10_lib/alpha/docs/unlinked.md', slug: 'alpha/docs/unlinked', library: 'alpha', section: null},
      ].sort(byPath)
    );
  });

  it('never collects an unmarked library or an internal CLAUDE.md', () => {
    const paths = collectSources(repo.root).map((source) => source.sourcePath);

    expect(paths.filter((p) => p.startsWith('10_lib/beta/'))).toEqual([]);
    expect(paths.filter((p) => p.endsWith('CLAUDE.md'))).toEqual([]);
  });

  it('lists the marked libraries sorted by directory name', () => {
    repo.write('10_lib/beta/pom.xml', MARKED_POM);
    repo.write('10_lib/aardvark/pom.xml', MARKED_POM);
    repo.write('10_lib/aardvark/README.md', '# ZEMS Aardvark\n\nFirst by name.\n');

    expect(publishedLibraries(repo.root)).toEqual(['aardvark', 'alpha', 'beta']);
  });

  it('ignores a library directory without a POM', () => {
    repo.write('10_lib/loose/README.md', '# Loose\n\nNo POM.\n');

    expect(publishedLibraries(repo.root)).toEqual(['alpha']);
  });

  it('collects a library that becomes marked with no other change', () => {
    repo.write('10_lib/beta/pom.xml', MARKED_POM);

    const betaSources = collectSources(repo.root)
      .filter((source) => source.library === 'beta')
      .sort(byPath);

    expect(betaSources).toEqual(
      [
        {sourcePath: '10_lib/beta/README.md', slug: 'beta', library: 'beta', section: null},
        {sourcePath: '10_lib/beta/docs/hidden.md', slug: 'beta/docs/hidden', library: 'beta', section: null},
      ].sort(byPath)
    );
  });

  it('collects a docs section without an entry page under the section name', () => {
    repo.write('docs/notes/one.md', '# One\n\nA note.\n');

    expect(collectSources(repo.root).filter((source) => source.section === 'notes')).toEqual([
      {sourcePath: 'docs/notes/one.md', slug: 'notes/one', library: null, section: 'notes'},
    ]);
  });

  it('throws when a docs section and a library produce the same slug', () => {
    repo.write('docs/alpha/README.md', '# Alpha essay\n\nSame slug as the alpha library.\n');

    expect(() => collectSources(repo.root)).toThrow(
      "Duplicate page slug 'alpha' from docs/alpha/README.md and 10_lib/alpha/README.md"
    );
  });

  it('throws when two sources produce the same slug', () => {
    repo.write('docs/alpha.md', '# Alpha clash\n\nSame slug as the alpha library.\n');

    expect(() => collectSources(repo.root)).toThrow(
      "Duplicate page slug 'alpha' from docs/alpha.md and 10_lib/alpha/README.md"
    );
  });

  it('reads a source verbatim', () => {
    const setup = collectSources(repo.root).find((source) => source.slug === 'alpha/docs/setup');

    expect(readSource(repo.root, setup!)).toBe(ALPHA_SETUP);
  });

  it('resolves the repo root two levels above the module directory', () => {
    expect(repoRoot()).toBe(path.resolve(process.cwd(), '..', '..'));
  });

  it.each([
    ['', '/'],
    ['setup', '/setup'],
    ['value', '/value'],
    ['value/docs', '/value/docs'],
    ['value/docs/setup', '/value/docs/setup'],
    ['essay/first', '/essay/first'],
  ])('maps slug %p to page URL %p', (slug, url) => {
    expect(pageUrl(slug)).toBe(url);
  });

  it.each([
    ['', '/index.md'],
    ['setup', '/setup.md'],
    ['value', '/value.md'],
    ['value/docs', '/value/docs.md'],
    ['value/docs/setup', '/value/docs/setup.md'],
  ])('maps slug %p to raw twin URL %p', (slug, url) => {
    expect(rawUrl(slug)).toBe(url);
  });
});
