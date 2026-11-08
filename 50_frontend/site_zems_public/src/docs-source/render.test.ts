/** @jest-environment node */
import {BrokenDocLinkError, headingIndex, type HeadingIndex} from '@/docs-source/links';
import {renderPage} from '@/docs-source/render';
import {collectSources, type DocSource} from '@/docs-source/sources';
import {
  ALPHA_DIAGRAM,
  ALPHA_DOCS_README,
  ALPHA_SETUP,
  createFixtureRepo,
  type FixtureRepo,
  SUITE_README,
} from '@/docs-source/test-support/fixture-repo';

describe('renderPage', () => {
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

  it('takes the title from the first-level heading and leaves it out of the body', () => {
    const page = renderPage(ALPHA_DIAGRAM, source('10_lib/alpha/docs/diagram.md'), sources, headings);

    expect(page.title).toBe('Diagram');
    expect(page.html).not.toContain('<h1');
  });

  it('takes the description from the first paragraph as plain text', () => {
    const page = renderPage(SUITE_README, source('README.md'), sources, headings);

    expect(page.description).toBe('Small, focused libraries for the fixture suite.');
  });

  it('renders headings with the ids the broken-link guard checks', () => {
    const page = renderPage(ALPHA_DIAGRAM, source('10_lib/alpha/docs/diagram.md'), sources, headings);

    expect(page.html).toContain('<h2 id="a-heading">A heading</h2>');
  });

  it('turns a mermaid fence into a mermaid block', () => {
    const page = renderPage(ALPHA_DIAGRAM, source('10_lib/alpha/docs/diagram.md'), sources, headings);

    expect(page.html).toContain('<pre class="mermaid">flowchart LR\n  A --> B');
    expect(page.html).not.toContain('language-mermaid');
  });

  it('keeps other code fences as code', () => {
    const page = renderPage(ALPHA_SETUP, source('10_lib/alpha/docs/setup.md'), sources, headings);

    expect(page.html).toContain('<pre><code class="language-xml">');
    expect(page.html).toContain('&#x3C;artifactId>alpha&#x3C;/artifactId>');
  });

  it('points links at page URLs', () => {
    const page = renderPage(ALPHA_DOCS_README, source('10_lib/alpha/docs/README.md'), sources, headings);

    expect(page.html).toContain('<a href="/alpha/docs/setup">setup</a>');
    expect(page.html).toContain('<a href="/alpha/docs/diagram#a-heading">diagram</a>');
    expect(page.html).toContain('<a href="/alpha/docs/setup#setup-heading">setup</a>');
    expect(page.html).toContain('<a href="https://zems.dev">the site</a>');
  });

  it('renders a markdown table with its links pointed at page URLs', () => {
    const page = renderPage(SUITE_README, source('README.md'), sources, headings);

    expect(page.html).toContain('<table>');
    expect(page.html).toContain('<a href="/alpha">alpha</a>');
    expect(page.html).toContain('<a href="/guide">Guide</a>');
  });

  it('throws when the source has no first-level heading', () => {
    expect(() => renderPage('Just text.\n', source('docs/guide.md'), sources, headings)).toThrow(
      "Missing '# ' title in docs/guide.md"
    );
  });

  it('throws BrokenDocLinkError on a broken link', () => {
    expect(() => renderPage('# Broken\n\nSee [x](missing.md).\n', source('docs/guide.md'), sources, headings)).toThrow(
      BrokenDocLinkError
    );
  });
});
