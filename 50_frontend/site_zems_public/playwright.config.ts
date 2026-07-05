import {defineConfig, devices} from '@playwright/test';

const LOCAL_BASE_URL = 'http://127.0.0.1:40201';

export default defineConfig({
  outputDir: './target/playwright-results',
  testDir: './src/e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [
    ['html', {open: 'never', outputFolder: './target/playwright-report'}],
    ['dot'],
    ['blob', {outputFile: `../../80_ops/var/e2e-reports-for-merge/report-site_zems_public.zip`}],
  ],
  use: {
    baseURL: process.env.E2E_BASE_URL || LOCAL_BASE_URL,
    trace: 'on-first-retry',
    testIdAttribute: 'data-testid',
  },
  projects: [
    {
      name: 'chromium',
      use: {...devices['Desktop Chrome']},
    },
    {
      name: 'firefox',
      use: {...devices['Desktop Firefox']},
    },
    // WebKit is gated to macOS — Linux runs would need version-pinned library
    // overrides for the downloaded WebKit build (see flake.nix:playwrightRuntimeLibs).
    ...(process.platform === 'darwin'
      ? [
          {
            name: 'webkit',
            use: {...devices['Desktop Safari']},
          },
        ]
      : []),
  ],
  webServer: {
    command: 'npm run start',
    url: LOCAL_BASE_URL,
    reuseExistingServer: !process.env.CI,
  },
});
