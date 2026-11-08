/** @jest-environment node */
import {llmsTxt} from '@/docs-source/llms-txt';
import type {NavGroup} from '@/docs-source/navigation';

const GROUPS: NavGroup[] = [
  {
    label: 'ZEMS',
    items: [
      {label: 'Overview', href: '/'},
      {label: 'Guide', href: '/guide'},
    ],
  },
  {
    label: 'ZEMS Alpha',
    items: [
      {label: 'Overview', href: '/alpha'},
      {label: 'ZEMS Alpha Documentation', href: '/alpha/docs'},
      {label: 'Setup', href: '/alpha/docs/setup'},
      {label: 'Source', href: 'https://github.com/mzingg/zems-public/tree/main/10_lib/alpha', external: true},
    ],
  },
];

describe('llmsTxt', () => {
  it('lists every document as its raw twin, grouped as the navigation', () => {
    expect(llmsTxt('https://zems.dev', GROUPS, 'Small, focused libraries.')).toBe(
      [
        '# ZEMS',
        '',
        '> Small, focused libraries.',
        '',
        '## ZEMS',
        '',
        '- [Overview](https://zems.dev/index.md)',
        '- [Guide](https://zems.dev/guide.md)',
        '',
        '## ZEMS Alpha',
        '',
        '- [Overview](https://zems.dev/alpha.md)',
        '- [ZEMS Alpha Documentation](https://zems.dev/alpha/docs.md)',
        '- [Setup](https://zems.dev/alpha/docs/setup.md)',
        '',
      ].join('\n')
    );
  });

  it('leaves external navigation items out', () => {
    expect(llmsTxt('https://zems.dev', GROUPS, 'Summary.')).not.toContain('github.com');
  });
});
