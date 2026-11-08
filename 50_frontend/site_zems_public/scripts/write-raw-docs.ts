// Writes the raw markdown twin of every page and the llms.txt index into the static export. Runs after `next build`
// (chained in the build script, because .npmrc's ignore-scripts also disables postbuild hooks) with Node's type
// stripping, so it imports src/docs-source/ relatively with `.ts` extensions.
import fs from 'node:fs';
import path from 'node:path';
import {rewriteRaw} from '../src/docs-source/links.ts';
import {llmsTxt} from '../src/docs-source/llms-txt.ts';
import {loadSite} from '../src/docs-source/site.ts';
import {rawUrl, readSource, repoRoot, SITE} from '../src/docs-source/sources.ts';

const EXPORT_DIR = path.resolve('target', 'site');

function writeExportFile(url: string, content: string) {
  const file = path.join(EXPORT_DIR, url);
  fs.mkdirSync(path.dirname(file), {recursive: true});
  fs.writeFileSync(file, content);
}

const site = loadSite(repoRoot());
for (const source of site.sources) {
  writeExportFile(rawUrl(source.slug), rewriteRaw(readSource(site.root, source), source, site.sources, site.headings));
}
writeExportFile('/llms.txt', llmsTxt(SITE, site.groups, site.pages.get('')!.description));
console.log(`Wrote ${site.sources.length} markdown twins and llms.txt into ${EXPORT_DIR}`);
