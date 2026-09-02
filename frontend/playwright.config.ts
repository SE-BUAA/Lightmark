import { defineConfig, devices } from '@playwright/test';

const configuredBaseUrl = process.env.E2E_BASE_URL || 'http://localhost:8081';
const baseURL = configuredBaseUrl.replace(/\/api\/?$/, '');

export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  expect: { timeout: 5_000 },
  fullyParallel: false,
  reporter: [['html', { outputFolder: 'e2e-results/html', open: 'never' }], ['json', { outputFile: 'e2e-results/results.json' }]],
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    ...devices['Desktop Chrome'],
  },
});
