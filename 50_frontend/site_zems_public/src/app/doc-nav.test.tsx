import {render, screen, within} from '@testing-library/react';
import '@testing-library/jest-dom';
import {DocNav} from '@/app/doc-nav';
import type {NavGroup} from '@/docs-source/navigation';

const GROUPS: NavGroup[] = [
  {
    label: 'ZEMS',
    items: [
      {label: 'Overview', href: '/'},
      {label: 'Setup', href: '/setup'},
    ],
  },
  {
    label: 'ZEMS Alpha',
    items: [
      {label: 'Overview', href: '/alpha'},
      {label: 'Setup', href: '/alpha/docs/setup'},
      {label: 'Source', href: 'https://github.com/mzingg/zems-public/tree/main/10_lib/alpha', external: true},
    ],
  },
];

describe('DocNav', () => {
  beforeEach(() => {
    render(<DocNav groups={GROUPS} current="/alpha/docs/setup" />);
  });

  it('shows every group label', () => {
    const nav = screen.getByTestId('doc-nav');

    expect(within(nav).getByText('ZEMS')).toBeInTheDocument();
    expect(within(nav).getByText('ZEMS Alpha')).toBeInTheDocument();
  });

  it('links every item to its page', () => {
    const links = within(screen.getByTestId('doc-nav')).getAllByRole('link');

    expect(links.map((link) => [link.textContent, link.getAttribute('href')])).toEqual([
      ['Overview', '/'],
      ['Setup', '/setup'],
      ['Overview', '/alpha'],
      ['Setup', '/alpha/docs/setup'],
      ['Source', 'https://github.com/mzingg/zems-public/tree/main/10_lib/alpha'],
    ]);
  });

  it('marks the current page and only that one', () => {
    const current = within(screen.getByTestId('doc-nav'))
      .getAllByRole('link')
      .filter((link) => link.getAttribute('aria-current') === 'page');

    expect(current).toHaveLength(1);
    expect(current[0]).toHaveAttribute('href', '/alpha/docs/setup');
  });

  it('links the library source on GitHub', () => {
    expect(screen.getByRole('link', {name: 'Source'})).toHaveAttribute(
      'href',
      'https://github.com/mzingg/zems-public/tree/main/10_lib/alpha'
    );
  });
});
