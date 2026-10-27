import { defineConfig } from '@playwright/test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const frontendDir = path.dirname(fileURLToPath(import.meta.url));
const projectDir = path.resolve(frontendDir, '..');
const databaseFile = path.join(projectDir, `.e2e-bookstore-${process.pid}.db`).replaceAll('\\', '/');

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  retries: 0,
  reporter: 'list',
  use: { baseURL: 'http://127.0.0.1:5173', channel: process.env.PLAYWRIGHT_CHANNEL || 'msedge', headless: true },
  webServer: [
    {
      command: '.\\.venv\\Scripts\\python.exe -m uvicorn src.api:app --host 127.0.0.1 --port 8000',
      cwd: projectDir,
      url: 'http://127.0.0.1:8000/api/books',
      env: { ...process.env, DATABASE_URL: `sqlite:///${databaseFile}` },
      reuseExistingServer: !process.env.CI,
      timeout: 30000,
    },
    {
      command: 'npm run dev -- --host 127.0.0.1 --port 5173 --strictPort',
      cwd: frontendDir,
      url: 'http://127.0.0.1:5173',
      reuseExistingServer: !process.env.CI,
      timeout: 30000,
    },
  ],
});
