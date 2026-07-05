import {expect, test} from '@playwright/test';
import {PUBLISHED_VERSION} from '../content/version';

test('landing page renders the suite content @Smoke', async ({page}) => {
  await page.goto('/');

  await expect(page).toHaveTitle('ZEMS — Zero-dependency Extensible Module Suite');
  await expect(page.getByRole('heading', {level: 1, name: 'ZEMS'})).toBeVisible();
  for (const name of ['Library', 'Use it', 'Source']) {
    await expect(page.getByRole('heading', {level: 2, name})).toBeVisible();
  }
  await expect(page.getByTestId('dependency-snippet')).toContainText(`<version>${PUBLISHED_VERSION}</version>`);
  await expect(page.getByRole('link', {name: 'Maven Central'})).toHaveAttribute(
    'href',
    'https://central.sonatype.com/artifact/dev.zems.lib/value'
  );
  await expect(page.getByTestId('footer')).toContainText('Apache License 2.0');
});
