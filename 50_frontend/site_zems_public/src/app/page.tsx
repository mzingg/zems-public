import {LIBRARIES} from '@/content/libraries';
import {FOOTER, INTRO, LIBRARY_OUTRO, SOURCE, USE_IT} from '@/content/sections';
import {PUBLISHED_VERSION} from '@/content/version';

const FEATURED = LIBRARIES[0];

const DEPENDENCY_SNIPPET = `<dependency>
  <groupId>${FEATURED.groupId}</groupId>
  <artifactId>${FEATURED.artifactId}</artifactId>
  <version>${PUBLISHED_VERSION}</version>
</dependency>`;

export default function Home() {
  return (
    <main data-testid="main">
      <h1>ZEMS</h1>
      <p className="tagline">Zero-dependency Extensible Module Suite</p>

      <p>{INTRO}</p>

      <h2>Library</h2>
      {LIBRARIES.map((library) => (
        <p key={`${library.groupId}:${library.artifactId}`}>
          <code>
            {library.groupId}:{library.artifactId}
          </code>{' '}
          — {library.summary}. {LIBRARY_OUTRO}
        </p>
      ))}

      <h2>Use it</h2>
      <p>{USE_IT}</p>
      <pre data-testid="dependency-snippet">
        <code>{DEPENDENCY_SNIPPET}</code>
      </pre>

      <h2>Source</h2>
      <p>{SOURCE}</p>

      <footer data-testid="footer">{FOOTER}</footer>
    </main>
  );
}
