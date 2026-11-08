import type {NavGroup} from '@/docs-source/navigation';

export function DocNav({groups, current}: {groups: NavGroup[]; current: string}) {
  return (
    <nav className="doc-nav" data-testid="doc-nav">
      {groups.map((group) => (
        <section key={group.label}>
          <h2>{group.label}</h2>
          <ul>
            {group.items.map((item) => (
              <li key={item.href}>
                <a href={item.href} aria-current={!item.external && item.href === current ? 'page' : undefined}>
                  {item.label}
                </a>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </nav>
  );
}
