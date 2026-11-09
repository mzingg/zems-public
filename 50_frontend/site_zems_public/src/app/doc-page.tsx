import {DocNav} from '@/app/doc-nav';
import {JourneySteppers} from '@/app/journey-steppers';
import {MermaidDiagrams} from '@/app/mermaid-diagrams';
import {docSite} from '@/app/site-content';
import {pageUrl} from '@/docs-source/sources';

export function DocPage({slug}: {slug: string}) {
  const site = docSite();
  const page = site.pages.get(slug)!;
  return (
    <div className="doc-layout">
      <DocNav groups={site.groups} current={pageUrl(slug)} />
      <main data-testid="main">
        <article>
          <h1>{page.title}</h1>
          <div dangerouslySetInnerHTML={{__html: page.html}} />
        </article>
      </main>
      <MermaidDiagrams />
      <JourneySteppers />
    </div>
  );
}
