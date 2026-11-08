import fs from 'node:fs';
import path from 'node:path';
import {expect, test} from '@playwright/test';

const REPO_ROOT = path.resolve(process.cwd(), '..', '..');

function sourceVersionLine(sourcePath: string): string {
  const markdown = fs.readFileSync(path.join(REPO_ROOT, sourcePath), 'utf8');
  return /<version>[^<]+<\/version>/.exec(markdown)![0];
}

function firstParagraph(sourcePath: string): string {
  const markdown = fs.readFileSync(path.join(REPO_ROOT, sourcePath), 'utf8');
  return markdown.split('\n\n')[1].replace(/\s+/g, ' ').trim();
}

test('landing page shows the suite overview and links every library @Smoke', async ({page}) => {
  await page.goto('/');

  await expect(page).toHaveTitle('ZEMS');
  await expect(page.getByRole('heading', {level: 1, name: 'ZEMS'})).toBeVisible();
  await expect(page.locator('article a[href="/common"]').first()).toBeVisible();
  await expect(page.locator('article a[href="/value"]').first()).toBeVisible();
  await expect(page.getByTestId('footer')).toContainText('Apache License 2.0');
});

test('suite setup page renders from docs/setup.md @Smoke', async ({page}) => {
  await page.goto('/setup');

  await expect(page.getByRole('heading', {level: 1, name: 'Setup'})).toBeVisible();
  await expect(page.locator('article a[href="/value/docs/setup"]')).toBeVisible();
});

test('library area shows what it does, its docs and its source @Smoke', async ({page}) => {
  await page.goto('/value');

  await expect(page.getByRole('heading', {level: 1, name: 'ZEMS Value'})).toBeVisible();
  await expect(page.locator('article')).toContainText(firstParagraph('10_lib/value/README.md'));
  const nav = page.getByTestId('doc-nav');
  await expect(nav.getByText('ZEMS Value', {exact: true})).toBeVisible();
  await expect(nav.locator('a[href="/value/docs"]')).toBeVisible();
  await expect(nav.locator('a[href="/value/docs/setup"]')).toBeVisible();
  await expect(nav.locator('a[href="https://github.com/mzingg/zems-public/tree/main/10_lib/value"]')).toHaveText(
    'Source'
  );
});

test('library setup page shows the dependency snippet with the released version @Smoke', async ({page}) => {
  await page.goto('/value/docs/setup');

  const snippet = page.locator('article pre').filter({hasText: '<artifactId>value</artifactId>'}).first();
  await expect(snippet).toContainText(sourceVersionLine('10_lib/value/docs/setup.md'));
});

test('a page points at its raw markdown twin and its anchors land on headings @Smoke', async ({page}) => {
  await page.goto('/value/docs/getting-started');

  await expect(page).toHaveTitle('Getting Started · ZEMS');
  await expect(page.locator('link[rel="alternate"][type="text/markdown"]')).toHaveAttribute(
    'href',
    '/value/docs/getting-started.md'
  );
  const anchored = 'a[href="/value/docs/jdk-directions#project-valhalla-values-without-identity"]';
  await expect(page.locator(anchored).first()).toBeVisible();

  await page.goto('/value/docs/jdk-directions');
  await expect(page.locator('#project-valhalla-values-without-identity')).toBeVisible();
});

test('raw markdown twins and llms.txt are served @Smoke', async ({request}) => {
  const twin = await request.get('/value/docs/getting-started.md');
  expect(twin.status()).toBe(200);
  expect(twin.headers()['content-type']).toMatch(/^text\/markdown/);
  expect(await twin.text()).toMatch(/^# Getting Started\n/);

  const landingTwin = await request.get('/index.md');
  expect(landingTwin.status()).toBe(200);
  expect(await landingTwin.text()).toMatch(/^# ZEMS\n/);

  const llms = await request.get('/llms.txt');
  expect(llms.status()).toBe(200);
  expect(await llms.text()).toContain('https://zems.dev/value/docs/getting-started.md');
});

test('mermaid renders on the page and stays fenced in the twin @Smoke', async ({page, request}) => {
  await page.goto('/value/docs/explicit-state');

  await expect(page.locator('pre.mermaid svg').first()).toBeVisible();

  const twin = await request.get('/value/docs/explicit-state.md');
  expect(await twin.text()).toContain('```mermaid\n');
});

test('every page declares its icons and the browser default /favicon.ico is served @Smoke', async ({page, request}) => {
  await page.goto('/value/docs');
  const icoLink = page.locator('link[rel="icon"][href^="/favicon.ico"]');
  const svgLink = page.locator('link[rel="icon"][type="image/svg+xml"]');
  await expect(icoLink).toHaveCount(1);
  await expect(svgLink).toHaveCount(1);

  for (const href of [await icoLink.getAttribute('href'), await svgLink.getAttribute('href'), '/favicon.ico']) {
    const icon = await request.get(href!);
    expect(icon.status(), href!).toBe(200);
    expect(icon.headers()['content-type'], href!).toMatch(/^image\//);
  }
});
