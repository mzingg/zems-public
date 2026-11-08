/** @jest-environment node */
import {BrokenDocLinkError, headingIndex, type HeadingIndex, resolveLink, rewriteRaw} from '@/docs-source/links';
import {collectSources, type DocSource} from '@/docs-source/sources';
import {
  ALPHA_DOCS_README,
  createFixtureRepo,
  type FixtureRepo,
  SUITE_GUIDE,
  SUITE_README,
} from '@/docs-source/test-support/fixture-repo';

function thrownBy(action: () => unknown): Error {
  try {
    action();
  } catch (error) {
    return error as Error;
  }
  throw new Error('expected the action to throw');
}

describe('links', () => {
  let repo: FixtureRepo;
  let sources: DocSource[];
  let headings: HeadingIndex;
  const source = (sourcePath: string) => sources.find((s) => s.sourcePath === sourcePath)!;

  beforeEach(() => {
    repo = createFixtureRepo();
    sources = collectSources(repo.root);
    headings = headingIndex(repo.root, sources);
  });

  afterEach(() => {
    repo.remove();
  });

  describe('headingIndex', () => {
    it('holds the github-slugger ids of every heading of a source', () => {
      expect(headings.get('10_lib/alpha/docs/diagram.md')).toContain('a-heading');
      expect(headings.get('10_lib/alpha/docs/setup.md')).toContain('setup-heading');
    });

    it('suffixes a repeated heading the way github-slugger does', () => {
      repo.write('docs/guide.md', '# Guide\n\nText.\n\n## Usage\n\nOne.\n\n## Usage\n\nTwo.\n');

      const index = headingIndex(repo.root, collectSources(repo.root));

      expect([...index.get('docs/guide.md')!]).toEqual(expect.arrayContaining(['usage', 'usage-1']));
    });
  });

  describe('resolveLink', () => {
    it.each(['https://zems.dev', 'mailto:someone@example.org'])('leaves the external URL %p unchanged', (url) => {
      expect(resolveLink(url, source('docs/guide.md'), sources, headings)).toEqual({kind: 'external', url});
    });

    it('accepts a same-page anchor that exists on the page', () => {
      expect(resolveLink('#a-heading', source('10_lib/alpha/docs/diagram.md'), sources, headings)).toEqual({
        kind: 'same-page',
        url: '#a-heading',
      });
    });

    it('resolves a relative link to a collected source', () => {
      expect(resolveLink('setup.md', source('10_lib/alpha/docs/README.md'), sources, headings)).toEqual({
        kind: 'doc',
        target: source('10_lib/alpha/docs/setup.md'),
        anchor: null,
      });
    });

    it('resolves a relative link with an anchor that exists on the target', () => {
      expect(resolveLink('diagram.md#a-heading', source('10_lib/alpha/docs/README.md'), sources, headings)).toEqual({
        kind: 'doc',
        target: source('10_lib/alpha/docs/diagram.md'),
        anchor: 'a-heading',
      });
    });

    it('resolves a link that climbs out of a directory', () => {
      expect(resolveLink('../10_lib/alpha/README.md', source('docs/guide.md'), sources, headings)).toEqual({
        kind: 'doc',
        target: source('10_lib/alpha/README.md'),
        anchor: null,
      });
    });

    it.each([
      [
        'a missing file',
        'docs/guide.md',
        'missing.md',
        "Broken link in docs/guide.md: 'missing.md' — docs/missing.md is not a published document.",
      ],
      [
        'an internal CLAUDE.md',
        '10_lib/alpha/docs/README.md',
        '../CLAUDE.md',
        "Broken link in 10_lib/alpha/docs/README.md: '../CLAUDE.md' — 10_lib/alpha/CLAUDE.md is not a published document.",
      ],
      [
        'an unpublished library',
        '10_lib/alpha/docs/README.md',
        '../../beta/README.md',
        "Broken link in 10_lib/alpha/docs/README.md: '../../beta/README.md' — 10_lib/beta/README.md is not a published document.",
      ],
      [
        'a missing anchor on another page',
        '10_lib/alpha/docs/README.md',
        'diagram.md#no-such-heading',
        "Broken link in 10_lib/alpha/docs/README.md: 'diagram.md#no-such-heading' — 10_lib/alpha/docs/diagram.md has no heading 'no-such-heading'.",
      ],
      [
        'a missing same-page anchor',
        '10_lib/alpha/docs/diagram.md',
        '#no-such-heading',
        "Broken link in 10_lib/alpha/docs/diagram.md: '#no-such-heading' — 10_lib/alpha/docs/diagram.md has no heading 'no-such-heading'.",
      ],
    ])('throws BrokenDocLinkError for %s', (_case, from, url, message) => {
      const error = thrownBy(() => resolveLink(url, source(from), sources, headings));

      expect(error).toBeInstanceOf(BrokenDocLinkError);
      expect(error.message).toBe(message);
    });
  });

  describe('rewriteRaw', () => {
    it('points link targets at twins and leaves every other byte unchanged', () => {
      const raw = rewriteRaw(ALPHA_DOCS_README, source('10_lib/alpha/docs/README.md'), sources, headings);

      expect(raw).toBe(
        ALPHA_DOCS_README.replace('](setup.md)', '](/alpha/docs/setup.md)')
          .replace('](diagram.md#a-heading)', '](/alpha/docs/diagram.md#a-heading)')
          .replace('](setup.md#setup-heading)', '](/alpha/docs/setup.md#setup-heading)')
      );
      expect(raw).toContain('[not a link](missing.md)');
      expect(raw).toContain('[the site](https://zems.dev)');
    });

    it('rewrites link reference definitions and links to the landing page', () => {
      const raw = rewriteRaw(SUITE_GUIDE, source('docs/guide.md'), sources, headings);

      expect(raw).toBe(
        SUITE_GUIDE.replace('[alpha]: ../10_lib/alpha/README.md', '[alpha]: /alpha.md').replace(
          '](../README.md)',
          '](/index.md)'
        )
      );
    });

    it('rewrites the links of a markdown table', () => {
      const raw = rewriteRaw(SUITE_README, source('README.md'), sources, headings);

      expect(raw).toBe(
        SUITE_README.replace('](10_lib/alpha/README.md)', '](/alpha.md)').replace('](docs/guide.md)', '](/guide.md)')
      );
    });

    it('throws BrokenDocLinkError on a broken link', () => {
      const error = thrownBy(() =>
        rewriteRaw('# Broken\n\nSee [x](missing.md).\n', source('docs/guide.md'), sources, headings)
      );

      expect(error).toBeInstanceOf(BrokenDocLinkError);
      expect(error.message).toBe(
        "Broken link in docs/guide.md: 'missing.md' — docs/missing.md is not a published document."
      );
    });
  });
});
