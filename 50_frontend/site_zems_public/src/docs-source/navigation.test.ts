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

  it('builds the suite group and one group per published library', () => {
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
    ]);
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
      ...before,
      {
        label: 'ZEMS Beta',
        items: [
          {label: 'Overview', href: '/beta'},
          {label: 'Hidden', href: '/beta/docs/hidden'},
          {label: 'Source', href: 'https://github.com/mzingg/zems-public/tree/main/10_lib/beta', external: true},
        ],
      },
    ]);
  });
});
