/** @jest-environment node */
import {type NavGroup, navigation} from '@/docs-source/navigation';
import {collectSources, type DocSource, readSource} from '@/docs-source/sources';
import {createFixtureRepo, type FixtureRepo, MARKED_POM} from '@/docs-source/test-support/fixture-repo';

const SUITE_GROUP: NavGroup = {
  label: 'ZEMS',
  items: [
    {label: 'Overview', href: '/'},
    {label: 'Guide', href: '/guide'},
  ],
};

const ESSAY_GROUP: NavGroup = {
  label: 'Essay',
  items: [
    {label: 'Overview', href: '/essay'},
    {label: 'Second', href: '/essay/second'},
    {label: 'First', href: '/essay/first'},
  ],
};

const ALPHA_SOURCE = {
  label: 'Source',
  href: 'https://github.com/mzingg/zems-public/tree/main/10_lib/alpha',
  external: true,
} as const;

function titlesOf(root: string, sources: DocSource[]): Map<string, string> {
  return new Map(
    sources.map((source) => [source.sourcePath, /^# (.+)$/m.exec(readSource(root, source))![1]] as [string, string])
  );
}

describe('navigation', () => {
  let repo: FixtureRepo;

  beforeEach(() => {
    repo = createFixtureRepo();
  });

  afterEach(() => {
    repo.remove();
  });

  function navigationOf(): NavGroup[] {
    const sources = collectSources(repo.root);
    return navigation(sources, titlesOf(repo.root, sources), (source) => readSource(repo.root, source));
  }

  it('builds the suite group, one group per published library and one group per docs section', () => {
    expect(navigationOf()).toEqual([
      SUITE_GROUP,
      {
        label: 'ZEMS Alpha',
        items: [
          {label: 'Overview', href: '/alpha'},
          {label: 'ZEMS Alpha Documentation', href: '/alpha/docs'},
          {label: 'Setup', href: '/alpha/docs/setup'},
          {label: 'Diagram', href: '/alpha/docs/diagram'},
          {label: 'Unlinked', href: '/alpha/docs/unlinked'},
          ALPHA_SOURCE,
        ],
      },
      ESSAY_GROUP,
    ]);
  });

  it('orders a section by its entry page links, then by title, and labels a section without an entry by its name', () => {
    repo.write('docs/essay/zz-appendix.md', '# Appendix\n\nUnlinked.\n');
    repo.write('docs/notes/one.md', '# One\n\nA note.\n');

    const [, , essay, notes] = navigationOf();

    expect(essay.items.map((item) => item.label)).toEqual(['Overview', 'Second', 'First', 'Appendix']);
    expect(notes).toEqual({label: 'notes', items: [{label: 'One', href: '/notes/one'}]});
  });

  it('orders the docs pages the entry page does not link by title', () => {
    repo.write('10_lib/alpha/docs/zz-appendix.md', '# Appendix\n\nAlso unlinked.\n');

    const alpha = navigationOf()[1];

    expect(alpha.items.map((item) => item.label)).toEqual([
      'Overview',
      'ZEMS Alpha Documentation',
      'Setup',
      'Diagram',
      'Appendix',
      'Unlinked',
      'Source',
    ]);
  });

  it('adds a group for a newly marked library with no other change', () => {
    const before = navigationOf();
    repo.write('10_lib/beta/pom.xml', MARKED_POM);

    expect(navigationOf()).toEqual([
      ...before.slice(0, 2),
      {
        label: 'ZEMS Beta',
        items: [
          {label: 'Overview', href: '/beta'},
          {label: 'Hidden', href: '/beta/docs/hidden'},
          {label: 'Source', href: 'https://github.com/mzingg/zems-public/tree/main/10_lib/beta', external: true},
        ],
      },
      ESSAY_GROUP,
    ]);
  });
});
